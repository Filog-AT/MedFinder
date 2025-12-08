package com.example.medfinder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.DecimalFormat
import androidx.fragment.app.DialogFragment

class MapFragment : Fragment() {

    private var mapView: MapView? = null
    private val db = FirebaseFirestore.getInstance()
    private lateinit var sharedViewModel: SharedViewModel
    private var isFragmentActive = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    internal var currentLocation: Location? = null
    private var locationMarker: Marker? = null
    private var myLocationOverlay: MyLocationNewOverlay? = null

    private val pharmacyDataList = mutableListOf<PharmacyData>()
    private var hasLocationPermission = false
    private var isLocationSetUp = false
    private var isMapInitialized = false

    data class PharmacyData(
        val id: String,
        val name: String,
        val location: com.google.firebase.firestore.GeoPoint,
        var distance: Double = 0.0,
        var hasMedicine: Boolean = false,
        var medicineName: String = "",
        var marker: Marker? = null
    ) : java.io.Serializable {
        // Convert GeoPoint to serializable format
        fun getLocationLat(): Double = location.latitude
        fun getLocationLng(): Double = location.longitude

        // Create a copy with primitive types for serialization
        fun toSerializableMap(): Map<String, Any> {
            return mapOf(
                "id" to id,
                "name" to name,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "distance" to distance,
                "hasMedicine" to hasMedicine,
                "medicineName" to medicineName
            )
        }
    }

    companion object {
        private const val TAG = "MapFragment"
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                Log.d(TAG, "Fine location permission granted")
                hasLocationPermission = true
                setupLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                Log.d(TAG, "Coarse location permission granted")
                hasLocationPermission = true
                setupLocation()
            }
            else -> {
                Log.d(TAG, "Location permission denied")
                hasLocationPermission = false
                Toast.makeText(requireContext(), "Location permission is required to show distances", Toast.LENGTH_LONG).show()
                showAllPharmacies()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView")
        val view = inflater.inflate(R.layout.fragment_map, container, false)

        Configuration.getInstance().load(requireContext(),
            requireContext().getSharedPreferences("osm_prefs", 0))

        mapView = view.findViewById(R.id.mapView)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")
        isFragmentActive = true

        sharedViewModel = ViewModelProvider(requireActivity())[SharedViewModel::class.java]

        if (!isMapInitialized) {
            setupMap()
            checkLocationPermission()
        }

        view.postDelayed({
            loadPharmaciesBasedOnSearch()
        }, 500)

        view.findViewById<View>(R.id.btn_my_location)?.setOnClickListener {
            currentLocation?.let { location ->
                centerMapToLocation(location)
                Toast.makeText(requireContext(), "Centered on your location", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(requireContext(), "Location not available", Toast.LENGTH_SHORT).show()
                checkLocationPermission()
            }
        }
    }

    private fun loadPharmaciesBasedOnSearch() {
        if (!isFragmentActive) return

        val searchQuery = sharedViewModel.searchQuery
        Log.d(TAG, "🔍 ViewModel search query: '$searchQuery'")

        if (!searchQuery.isNullOrEmpty()) {
            Log.d(TAG, "🚀 Starting MEDICINE SEARCH for: $searchQuery")
            searchPharmaciesWithMedicine(searchQuery)
        } else {
            Log.d(TAG, "📍 Showing ALL PHARMACIES (no search)")
            showAllPharmacies()
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Location permission already granted")
                hasLocationPermission = true
                setupLocation()
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                showLocationPermissionExplanation()
            }

            else -> {
                locationPermissionRequest.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }

    private fun showLocationPermissionExplanation() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Location Permission Needed")
            .setMessage("This app needs location permission to show your current position on the map and find nearby pharmacies.")
            .setPositiveButton("OK") { _, _ ->
                locationPermissionRequest.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(requireContext(), "Showing default location", Toast.LENGTH_SHORT).show()
                centerMapToDefaultLocation()
            }
            .create()
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun setupLocation() {
        if (isLocationSetUp) return

        Log.d(TAG, "Setting up location services")
        isLocationSetUp = true

        val locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                    Log.d(TAG, "📍 Location updated: ${location.latitude}, ${location.longitude}")

                    updateUserLocationMarker(location)
                    updateAllMarkerDistancesAndIcons()

                    if (locationMarker == null) {
                        centerMapToLocation(location)
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                currentLocation = it
                Log.d(TAG, "📌 Last known location: ${it.latitude}, ${it.longitude}")
                updateUserLocationMarker(it)
                updateAllMarkerDistancesAndIcons()
                centerMapToLocation(it)
            } ?: run {
                Log.d(TAG, "No last known location")
                centerMapToDefaultLocation()
            }
        }
    }

    private fun updateAllMarkerDistancesAndIcons() {
        currentLocation?.let { userLocation ->
            val currentMapView = mapView ?: return
            val currentZoom = currentMapView.zoomLevelDouble
            val scale = getMarkerScaleForZoomLevel(currentZoom)

            Log.d(TAG, "📏 Updating distances and icons for ${pharmacyDataList.size} pharmacies")

            pharmacyDataList.forEach { pharmacyData ->
                // Calculate distance
                val distance = calculateDistance(
                    userLocation.latitude,
                    userLocation.longitude,
                    pharmacyData.location.latitude,
                    pharmacyData.location.longitude
                )
                pharmacyData.distance = distance

                // Update with current scale
                pharmacyData.marker?.let { marker ->
                    val newIcon = createBadgeMarkerWithDistance(pharmacyData, scale)
                    marker.setIcon(newIcon)
                    updateMarkerTitle(marker, pharmacyData)
                }
            }
            mapView?.invalidate()
        }
    }

    private fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    private fun centerMapToLocation(location: Location) {
        val currentMapView = mapView ?: return
        val userLocation = GeoPoint(location.latitude, location.longitude)
        currentMapView.controller.animateTo(userLocation)
        currentMapView.controller.setZoom(18.0)

        Log.d(TAG, "🗺️ Map centered to user location")
    }

    private fun centerMapToDefaultLocation() {
        val currentMapView = mapView ?: return
        val defaultLocation = GeoPoint(16.41197, 120.59341)
        currentMapView.controller.animateTo(defaultLocation)
        currentMapView.controller.setZoom(18.0)

        Log.d(TAG, "🗺️ Map centered to default location")
    }

    private fun setupMap() {
        val currentMapView = mapView ?: return
        Log.d(TAG, "🗺 Setting up map...")

        currentMapView.setTileSource(XYTileSource(
            "CartoDB Voyager",
            0, 18, 256, ".png",
            arrayOf("https://a.basemaps.cartocdn.com/rastertiles/voyager/"),
            "© OpenStreetMap contributors © CartoDB"
        ))

        currentMapView.setMultiTouchControls(true)
        currentMapView.setBuiltInZoomControls(true)

        val startPoint = GeoPoint(16.41197, 120.59341)
        currentMapView.controller.setCenter(startPoint)
        currentMapView.controller.setZoom(15.0)

        // Add zoom listener
        currentMapView.addMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                return false
            }

            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                event?.let {
                    // Update all markers when zoom changes
                    updateAllMarkersForZoomLevel()
                }
                return false
            }
        })

        isMapInitialized = true
        Log.d(TAG, "✅ Map setup complete")
    }

    private fun updateAllMarkersForZoomLevel() {
        val currentMapView = mapView ?: return
        val currentZoom = currentMapView.zoomLevelDouble
        val scale = getMarkerScaleForZoomLevel(currentZoom)

        Log.d(TAG, "🔄 Updating markers for zoom level: $currentZoom, scale: $scale")

        pharmacyDataList.forEach { pharmacyData ->
            pharmacyData.marker?.let { marker ->
                val newIcon = createBadgeMarkerWithDistance(pharmacyData, scale)
                marker.setIcon(newIcon)
            }
        }

        // Refresh the map
        currentMapView.invalidate()
    }

    private fun searchPharmaciesWithMedicine(medicineName: String) {
        if (!isFragmentActive) return
        Log.d(TAG, "🔍 SEARCHING for medicine: $medicineName")
        clearMap()
        showToast("Searching for $medicineName...")
        db.collection("Pharmacies")
            .get()
            .addOnSuccessListener { pharmacyResult ->
                if (!isFragmentActive) return@addOnSuccessListener
                val totalPharmacies = pharmacyResult.size()
                Log.d(TAG, "Found $totalPharmacies pharmacies")
                if (totalPharmacies == 0) {
                    showToast("No pharmacies found")
                    return@addOnSuccessListener
                }

                var processedCount = 0
                pharmacyResult.documents.forEach { pharmacyDoc ->
                    val pharmacyId = pharmacyDoc.id
                    val pharmacyName = pharmacyDoc.getString("pharmacy_name") ?: "Unknown Pharmacy"
                    val location = pharmacyDoc.getGeoPoint("Location")

                    if (location != null) {
                        val pharmacyData = PharmacyData(
                            id = pharmacyId,
                            name = pharmacyName,
                            location = location,
                            medicineName = medicineName
                        )

                        checkMedicineStock(pharmacyId, medicineName) { hasMedicine ->
                            if (!isFragmentActive) return@checkMedicineStock

                            pharmacyData.hasMedicine = hasMedicine

                            // Calculate initial distance
                            currentLocation?.let { userLocation ->
                                val distance = calculateDistance(
                                    userLocation.latitude,
                                    userLocation.longitude,
                                    location.latitude,
                                    location.longitude
                                )
                                pharmacyData.distance = distance
                            }

                            val marker = createMarkerWithDistance(pharmacyData)
                            pharmacyData.marker = marker
                            pharmacyDataList.add(pharmacyData)

                            processedCount++
                            Log.d(TAG, "✅ Processed $processedCount/$totalPharmacies: $pharmacyName")

                            if (processedCount == totalPharmacies) {
                                showSearchResultsSummary(medicineName, totalPharmacies)
                                Log.d(TAG, "✅ All pharmacies loaded and markers created")
                            }
                        }
                    } else {
                        processedCount++
                    }
                }
            }
            .addOnFailureListener { e ->
                if (!isFragmentActive) return@addOnFailureListener
                Log.e(TAG, "Error loading pharmacies", e)
                showToast("Error loading pharmacies")
            }
    }

    private fun checkMedicineStock(
        pharmacyId: String,
        medicineName: String,
        onComplete: (Boolean) -> Unit
    ) {
        db.collection("Pharmacies")
            .document(pharmacyId)
            .collection("Medicines")
            .whereEqualTo("medicine_name", medicineName)
            .get()
            .addOnSuccessListener { inventoryResult ->
                if (!isFragmentActive) return@addOnSuccessListener

                val hasMedicine = inventoryResult.any { document ->
                    val stock = document.getLong("stock") ?: 0
                    stock > 0
                }
                onComplete(hasMedicine)
            }
            .addOnFailureListener { e ->
                if (!isFragmentActive) return@addOnFailureListener
                Log.e(TAG, "Error checking inventory", e)
                onComplete(false)
            }
    }

    private fun createMarkerWithDistance(pharmacyData: PharmacyData): Marker? {
        val currentMapView = mapView ?: return null

        try {
            val marker = Marker(currentMapView)
            marker.position = GeoPoint(
                pharmacyData.location.latitude,
                pharmacyData.location.longitude
            )
            marker.setRelatedObject(pharmacyData.id)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            // Get current zoom level and calculate scale
            val currentZoom = currentMapView.zoomLevelDouble
            val scale = getMarkerScaleForZoomLevel(currentZoom)

            val markerIcon = createBadgeMarkerWithDistance(pharmacyData, scale)
            marker.setIcon(markerIcon)

            updateMarkerTitle(marker, pharmacyData)

            // Store the scale with the marker
            marker.setRelatedObject(scale)

            // Updated OnMarkerClickListener with navigation option
            marker.setOnMarkerClickListener { clickedMarker, mapView ->
                showPharmacyBottomSheet(pharmacyData)
                true
            }

            currentMapView.overlays.add(marker)
            return marker
        } catch (e: Exception) {
            Log.e(TAG, "Error creating marker for ${pharmacyData.name}: ${e.message}")
            return null
        }
    }

    private fun showPharmacyBottomSheet(pharmacyData: PharmacyData) {
        try {
            val bottomSheetFragment = PharmacyBottomSheetFragment.newInstance(pharmacyData) // Use Fragment
            bottomSheetFragment.show(parentFragmentManager, "PharmacyBottomSheet")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing bottom sheet: ${e.message}", e)
            Toast.makeText(requireContext(), "Error showing pharmacy details", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createFixedSizeMarker(pharmacyData: PharmacyData): Drawable {
        val fixedWidth = 120
        val fixedHeight = 160
        val bitmap = Bitmap.createBitmap(fixedWidth, fixedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        return BitmapDrawable(resources, bitmap)
    }

    private fun createBadgeMarkerWithDistance(pharmacyData: PharmacyData, scale: Float = 1.0f): Drawable {
        val baseCircleDiameter = 140
        val baseBadgeHeight = 70
        val basePadding = 10
        val circleDiameter = (baseCircleDiameter * scale).toInt()
        val badgeHeight = (baseBadgeHeight * scale).toInt()
        val padding = (basePadding * scale).toInt()
        val totalHeight = circleDiameter + badgeHeight + padding
        val bitmapWidth = circleDiameter + 40
        val bitmap = Bitmap.createBitmap(bitmapWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.TRANSPARENT)

        val circleColor = when {
            pharmacyData.medicineName.isNotEmpty() -> {
                if (pharmacyData.hasMedicine) Color.parseColor("#4CAF50")
                else Color.parseColor("#F44336")
            }
            else -> Color.parseColor("#2196F3")
        }

        val circlePaint = Paint().apply {
            color = circleColor
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val borderPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 5f * scale
        }

        val centerX = bitmapWidth / 2f
        val circleCenterY = circleDiameter / 2f

        val ovalWidth = circleDiameter.toFloat()
        val ovalHeight = circleDiameter.toFloat() * 0.8f
        val ovalLeft = centerX - (ovalWidth / 2f)
        val ovalTop = circleCenterY - (ovalHeight / 2f)
        val ovalRight = centerX + (ovalWidth / 2f)
        val ovalBottom = circleCenterY + (ovalHeight / 2f)
        val ovalRect = RectF(ovalLeft, ovalTop, ovalRight, ovalBottom)

        canvas.drawOval(ovalRect, circlePaint)
        canvas.drawOval(ovalRect, borderPaint)

        val badgePaint = Paint().apply {
            color = Color.parseColor("#222222")
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        val badgeWidth = ovalWidth * 0.8f
        val badgeRect = RectF(
            centerX - (badgeWidth / 2),
            circleCenterY + (ovalHeight / 2) - (10 * scale),
            centerX + (badgeWidth / 2),
            circleCenterY + (ovalHeight / 2) + (badgeHeight * scale)
        )

        val cornerRadius = 10f * scale
        canvas.drawRoundRect(badgeRect, cornerRadius, cornerRadius, badgePaint)

        val badgeBorderPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
        }
        canvas.drawRoundRect(badgeRect, cornerRadius, cornerRadius, badgeBorderPaint)
        val decimalFormat = DecimalFormat("#.#")
        val distanceText = if (pharmacyData.distance > 0) {
            "${decimalFormat.format(pharmacyData.distance)} km"
        } else {
            "?? km"
        }
        val distanceTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 30f * scale
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val distanceBounds = Rect()
        distanceTextPaint.getTextBounds(distanceText, 0, distanceText.length, distanceBounds)
        val distanceY = badgeRect.centerY() + (distanceBounds.height() / 2f) - (2 * scale)
        val textBgPaint = Paint().apply {
            color = Color.parseColor("#444444")
            isAntiAlias = true
            style = Paint.Style.FILL
            alpha = 150
        }
        val textBgPadding = 4f * scale
        val textBgRect = RectF(
            badgeRect.left + textBgPadding,
            distanceY - distanceBounds.height() - textBgPadding,
            badgeRect.right - textBgPadding,
            distanceY + textBgPadding
        )
        canvas.drawRoundRect(textBgRect, 5f * scale, 5f * scale, textBgPaint)
        canvas.drawText(distanceText, centerX, distanceY, distanceTextPaint)

        val circleTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f * scale  // Pharmacy name text size
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(2f * scale, 1f * scale, 1f * scale, Color.BLACK)
        }

        if (pharmacyData.medicineName.isNotEmpty()) {
            val medText = if (pharmacyData.hasMedicine) "✓" else "✗"
            val medBounds = Rect()
            circleTextPaint.getTextBounds(medText, 0, medText.length, medBounds)
            val medY = circleCenterY + (medBounds.height() / 2f) - (8 * scale)

            canvas.drawText(medText, centerX, medY, circleTextPaint)
        } else {
            val pharmacyName = pharmacyData.name
            val displayText = if (pharmacyName.contains(" ")) {
                val firstWord = pharmacyName.substring(0, pharmacyName.indexOf(" "))
                if (firstWord.length > 8) firstWord.substring(0, 7) + "." else firstWord
            } else {
                if (pharmacyName.length > 8) pharmacyName.substring(0, 7) + "." else pharmacyName
            }

            val nameBounds = Rect()
            circleTextPaint.getTextBounds(displayText, 0, displayText.length, nameBounds)

            val nameY = circleCenterY + (nameBounds.height() / 2f) - (5 * scale)
            canvas.drawText(displayText, centerX, nameY, circleTextPaint)
        }

        val drawable = BitmapDrawable(resources, bitmap)
        drawable.setBounds(0, 0, bitmapWidth, totalHeight)

        return drawable
    }

    private fun getMarkerScaleForZoomLevel(zoomLevel: Double): Float {
        // Define scaling rules
        return when {
            zoomLevel >= 18.0 -> 1.0f      // Max zoom: full size (100%)
            zoomLevel >= 16.0 -> 0.8f      // City level: 80%
            zoomLevel >= 14.0 -> 0.6f      // Neighborhood: 60%
            zoomLevel >= 12.0 -> 0.4f      // Town: 40%
            zoomLevel >= 10.0 -> 0.3f      // Region: 30% (your requested minimum)
            zoomLevel >= 8.0  -> 0.25f     // State: 25%
            zoomLevel >= 6.0  -> 0.2f      // Country: 20%
            else -> 0.15f                  // World view: 15%
        }
    }
    private fun updateMarkerWithDistanceIcon(pharmacyData: PharmacyData) {
        pharmacyData.marker?.let { marker ->
            val currentMapView = mapView ?: return
            val currentZoom = currentMapView.zoomLevelDouble
            val scale = getMarkerScaleForZoomLevel(currentZoom)

            val newIcon = createBadgeMarkerWithDistance(pharmacyData, scale)
            marker.setIcon(newIcon)
            updateMarkerTitle(marker, pharmacyData)
        }
    }

    private fun updateMarkerTitle(marker: Marker, pharmacyData: PharmacyData) {
        val decimalFormat = DecimalFormat("#.#")
        val distanceStr = if (pharmacyData.distance > 0) {
            "${decimalFormat.format(pharmacyData.distance)} km"
        } else {
            "?? km"
        }

        if (pharmacyData.medicineName.isNotEmpty()) {
            val status = if (pharmacyData.hasMedicine) "✅ HAS" else "❌ NO"
            marker.title = "${pharmacyData.name}\n$status ${pharmacyData.medicineName}\n📏 $distanceStr"
        } else {
            marker.title = "${pharmacyData.name}\n📏 $distanceStr"
        }
        marker.snippet = "Tap for options"
    }

    private fun clearMap() {
        mapView?.overlays?.clear()
        pharmacyDataList.clear()
        locationMarker = null
    }

    private fun showAllPharmacies() {
        if (!isFragmentActive) return

        Log.d(TAG, "🔄 Loading ALL pharmacies...")

        clearMap()

        db.collection("Pharmacies")
            .get()
            .addOnSuccessListener { result ->
                if (!isFragmentActive) return@addOnSuccessListener

                val totalPharmacies = result.size()
                Log.d(TAG, "📊 Found $totalPharmacies pharmacies")

                var processedCount = 0

                result.documents.forEach { document ->
                    val location = document.getGeoPoint("Location")
                    val name = document.getString("pharmacy_name") ?: "Unknown Pharmacy"
                    val pharmacyId = document.id

                    if (location != null) {
                        val pharmacyData = PharmacyData(
                            id = pharmacyId,
                            name = name,
                            location = location
                        )

                        currentLocation?.let { userLocation ->
                            val distance = calculateDistance(
                                userLocation.latitude,
                                userLocation.longitude,
                                location.latitude,
                                location.longitude
                            )
                            pharmacyData.distance = distance
                        }

                        val marker = createMarkerWithDistance(pharmacyData)
                        pharmacyData.marker = marker
                        pharmacyDataList.add(pharmacyData)

                        processedCount++

                        if (processedCount == totalPharmacies) {
                            Log.d(TAG, "✅ All $totalPharmacies pharmacies added")
                            showToast("Showing ${result.size()} pharmacies")
                        }
                    } else {
                        processedCount++
                    }
                }

                if (result.isEmpty()) {
                    showToast("No pharmacies found")
                }
            }
            .addOnFailureListener { e ->
                if (!isFragmentActive) return@addOnFailureListener
                Log.e(TAG, "Error loading pharmacies", e)
                showToast("Error loading pharmacies")
            }
    }

    private fun updateUserLocationMarker(location: Location) {
        val currentMapView = mapView ?: return

        locationMarker?.let {
            currentMapView.overlays.remove(it)
        }

        val marker = Marker(currentMapView)
        marker.position = GeoPoint(location.latitude, location.longitude)
        marker.title = "Your Location"
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

        val userLocationIcon = ContextCompat.getDrawable(requireContext(), R.drawable.my_location_icon)
        marker.setIcon(userLocationIcon)

        currentMapView.overlays.add(marker)
        locationMarker = marker

        Log.d(TAG, "✅ User location marker added")
    }

    private fun createSimpleCircleMarker(color: Int, isUserLocation: Boolean = false): Drawable {
        val size = if (isUserLocation) 45 else 40
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            this.color = color
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint)

        val borderPaint = Paint().apply {
            this.color = Color.WHITE
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, borderPaint)
        val drawable = BitmapDrawable(resources, bitmap)
        drawable.setBounds(0, 0, size, size)

        return drawable
    }

    private fun showSearchResultsSummary(medicineName: String, totalPharmacies: Int) {
        if (!isFragmentActive) return

        val availableCount = pharmacyDataList.count { it.hasMedicine }
        val message = if (availableCount > 0) {
            "Found $availableCount out of $totalPharmacies pharmacies with $medicineName"
        } else {
            "No pharmacies found with $medicineName in stock"
        }

        Log.d(TAG, "📊 $message")
        showToast(message)
    }

    private fun showPharmacyOptions(pharmacyData: PharmacyData) {
        val options = arrayOf("Get Directions", "Make Reservation", "Cancel")

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Pharmacy Options")
            .setMessage("Select an option for ${pharmacyData.name}")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> openDirectionsToPharmacy(pharmacyData)
                    1 -> openReservationFragment(pharmacyData.id)
                    // 2 is Cancel
                }
                dialog.dismiss()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openDirectionsToPharmacy(pharmacyData: PharmacyData) {
        try {
            currentLocation?.let { userLocation ->
                val destinationLat = pharmacyData.location.latitude
                val destinationLng = pharmacyData.location.longitude

                // Create Google Maps intent
                val gmmIntentUri = Uri.parse(
                    "google.navigation:q=$destinationLat,$destinationLng&mode=d"
                )
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")

                // Check if Google Maps is installed
                if (mapIntent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(mapIntent)
                } else {
                    // Fallback to web browser with Google Maps
                    val webUri = Uri.parse(
                        "https://www.google.com/maps/dir/?api=1" +
                                "&destination=$destinationLat,$destinationLng" +
                                "&travelmode=driving"
                    )
                    val webIntent = Intent(Intent.ACTION_VIEW, webUri)
                    startActivity(webIntent)
                }

                Log.d(TAG, "🗺️ Opening directions to ${pharmacyData.name}")
                Toast.makeText(requireContext(), "Opening directions...", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(requireContext(),
                    "Unable to get your current location. Please enable location services.",
                    Toast.LENGTH_LONG).show()
                checkLocationPermission()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error opening directions: ${e.message}", e)
            Toast.makeText(requireContext(),
                "Error opening navigation app. Please install Google Maps.",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun openReservationFragment(pharmacyId: String) {
        try {
            Log.d(TAG, "🚀 Opening reservation fragment for pharmacy: $pharmacyId")

            if (!LoginUtils.isUserLoggedIn(requireContext())) {
                LoginUtils.redirectToLogin(requireContext(), "Please login to reserve medicines")
                return
            }

            if (!LoginUtils.isCustomer(requireContext())) {
                Toast.makeText(requireContext(),
                    "Only customers can make reservations",
                    Toast.LENGTH_LONG).show()
                return
            }

            val pharmacyData = pharmacyDataList.find { it.id == pharmacyId }
            val distance = pharmacyData?.distance ?: 0.0

            val reservationFragment = MedReserveFragment.newInstance(pharmacyId, distance)

            val container = view?.findViewById<ViewGroup>(R.id.fragment_container)
            if (container != null) {
                container.visibility = View.VISIBLE
            }

            childFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_up,      // Your custom slide_in_up
                    R.anim.slide_out_down,   // Your custom slide_out_down
                    R.anim.slide_in_up,      // Your custom slide_in_up
                    R.anim.slide_out_down    // Your custom slide_out_down
                )
                .replace(R.id.fragment_container, reservationFragment, "RESERVATION_FRAGMENT")
                .addToBackStack("reservation")
                .commit()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error opening reservation fragment: ${e.message}", e)
            Toast.makeText(requireContext(), "Error opening reservation", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showToast(message: String) {
        if (isFragmentActive && isAdded) {
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        mapView?.onResume()

        if (isFragmentActive) {
            view?.postDelayed({
                loadPharmaciesBasedOnSearch()
            }, 300)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        mapView?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
        isFragmentActive = false

        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        isMapInitialized = false
    }
}