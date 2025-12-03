package com.example.medfinder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
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
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
class MapFragment : Fragment() {

    private var mapView: MapView? = null
    private val db = FirebaseFirestore.getInstance()
    private lateinit var sharedViewModel: SharedViewModel
    private var isFragmentActive = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var currentLocation: Location? = null
    private var locationMarker: Marker? = null
    private var myLocationOverlay: MyLocationNewOverlay? = null

    // Permission request launcher
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted
                Log.d("MapFragment", "Fine location permission granted")
                setupLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted
                Log.d("MapFragment", "Coarse location permission granted")
                setupLocation()
            }
            else -> {
                // No location access granted
                Log.d("MapFragment", "Location permission denied")
                Toast.makeText(requireContext(), "Location permission is required to show your location", Toast.LENGTH_LONG).show()
                // Show all pharmacies anyway
                showAllPharmacies()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_map, container, false)

        Configuration.getInstance().load(requireContext(),
            requireContext().getSharedPreferences("osm_prefs", 0))

        mapView = view.findViewById(R.id.mapView)

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isFragmentActive = true

        sharedViewModel = ViewModelProvider(requireActivity())[SharedViewModel::class.java]

        setupMap()
        checkLocationPermission()

        view.postDelayed({
            val searchQuery = sharedViewModel.searchQuery
            Log.d("MapFragment", "🔍 ViewModel search query: '$searchQuery'")

            if (!searchQuery.isNullOrEmpty()) {
                Log.d("MapFragment", "🚀 Starting MEDICINE SEARCH for: $searchQuery")
                searchPharmaciesWithMedicine(searchQuery)
            } else {
                Log.d("MapFragment", "📍 Showing ALL PHARMACIES (no search)")
                showAllPharmacies()
            }
        }, 1000)

    }

    private fun checkLocationPermission() {
        when {
            // Check if permissions are already granted
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d("MapFragment", "Location permission already granted")
                setupLocation()
            }

            // Should we show explanation?
            ActivityCompat.shouldShowRequestPermissionRationale(
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                // Show explanation dialog
                showLocationPermissionExplanation()
            }

            else -> {
                // Request permission
                Log.d("MapFragment", "Requesting location permission")
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
        Log.d("MapFragment", "Setting up location services")

        // Create location request
        val locationRequest = LocationRequest.create().apply {
            interval = 10000 // 10 seconds
            fastestInterval = 5000 // 5 seconds
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // Create location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                    Log.d("MapFragment", "📍 Location updated: ${location.latitude}, ${location.longitude}")

                    // Update or add user location marker
                    updateUserLocationMarker(location)

                    // Center map on user location (only first time)
                    if (locationMarker == null) {
                        centerMapToLocation(location)
                    }
                }
            }
        }

        // Start location updates
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        // Also get last known location
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                currentLocation = it
                Log.d("MapFragment", "📌 Last known location: ${it.latitude}, ${it.longitude}")
                updateUserLocationMarker(it)
                centerMapToLocation(it)
            } ?: run {
                Log.d("MapFragment", "No last known location")
                centerMapToDefaultLocation()
            }
        }
    }

    // Add this to your MapFragment class
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle back press in child fragments
        childFragmentManager.addOnBackStackChangedListener {
            Log.d("MapFragment", "📊 Back stack changed, entries: ${childFragmentManager.backStackEntryCount}")

            if (childFragmentManager.backStackEntryCount == 0) {
                // No more fragments in back stack, hide the container
                view?.findViewById<ViewGroup>(R.id.fragment_container)?.visibility = View.GONE
                Log.d("MapFragment", "📦 Container hidden")
            }
        }
    }

    private fun updateUserLocationMarker(location: Location) {
        val currentMapView = mapView
        if (currentMapView == null) {
            Log.e("MapFragment", "MapView is null when updating location marker")
            return
        }

        // Remove old marker if exists
        locationMarker?.let {
            currentMapView.overlays.remove(it)
        }

        // Create new marker for user location
        val marker = Marker(currentMapView)
        marker.position = GeoPoint(location.latitude, location.longitude)
        marker.title = "Your Location"
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

        // Create blue circle for user location
        val userLocationIcon = createColoredCircleMarker(Color.BLUE, true)
        marker.setIcon(userLocationIcon)

        currentMapView.overlays.add(marker)
        locationMarker = marker
        currentMapView.invalidate()

        Log.d("MapFragment", "✅ User location marker added")
    }

    private fun createColoredCircleMarker(color: Int, isUserLocation: Boolean = false): Drawable {
        val size = if (isUserLocation) 30 else 40
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            this.color = color
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint)

        val borderPaint = Paint().apply {
            this.color = if (isUserLocation) Color.WHITE else Color.BLACK
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = if (isUserLocation) 3f else 2f
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, borderPaint)

        return BitmapDrawable(resources, bitmap)
    }

    private fun centerMapToLocation(location: Location) {
        val currentMapView = mapView
        if (currentMapView == null) {
            Log.e("MapFragment", "MapView is null when centering to location")
            return
        }

        val userLocation = GeoPoint(location.latitude, location.longitude)
        currentMapView.controller.animateTo(userLocation)
        currentMapView.controller.setZoom(18.0)

        Log.d("MapFragment", "🗺️ Map centered to user location: ${location.latitude}, ${location.longitude}")
        Toast.makeText(requireContext(), "Showing your location", Toast.LENGTH_SHORT).show()
    }

    private fun centerMapToDefaultLocation() {
        val currentMapView = mapView
        if (currentMapView == null) {
            Log.e("MapFragment", "MapView is null when centering to default")
            return
        }

        // Default location (you can change this)
        val defaultLocation = GeoPoint(16.41197, 120.59341) // Baguio City
        currentMapView.controller.animateTo(defaultLocation)
        currentMapView.controller.setZoom(18.0)

        Log.d("MapFragment", "🗺️ Map centered to default location")
        Toast.makeText(requireContext(), "Showing default location", Toast.LENGTH_SHORT).show()
    }

    private fun setupMap() {
        val currentMapView = mapView
        if (currentMapView == null) {
            Log.e("MapFragment", "MapView is null during setup")
            return
        }

        currentMapView.setTileSource(XYTileSource(
            "CartoDB Voyager",
            0, 18, 256, ".png",
            arrayOf("https://a.basemaps.cartocdn.com/rastertiles/voyager/"),
            "© OpenStreetMap contributors © CartoDB"
        ))

        currentMapView.setMultiTouchControls(true)
        currentMapView.setBuiltInZoomControls(true)

        // Add my location button (OSMDroid built-in)
        currentMapView.overlays.add(MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), currentMapView).apply {
            setDrawAccuracyEnabled(true)
            enableMyLocation()
            myLocationOverlay = this
        })

        // Set initial zoom and center (will be overridden by location)
        val startPoint = GeoPoint(16.41197, 120.59341)
        val mapController = currentMapView.controller
        mapController.setZoom(15.0) // Start with wider view
        mapController.setCenter(startPoint)

        // Add marker click listener
        currentMapView.overlays.add(object : org.osmdroid.views.overlay.Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
                if (e != null && mapView != null) {
                    val projection = mapView.projection
                    val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt())

                    // Check if a marker was clicked
                    for (overlay in mapView.overlays.reversed()) {
                        if (overlay is Marker) {
                            val markerPoint = overlay.position
                            val distance = markerPoint.distanceToAsDouble(geoPoint)

                            // If click is near the marker
                            if (distance < 0.0001) {
                                val pharmacyId = overlay.relatedObject as? String
                                if (pharmacyId != null) {
                                    openReservationFragment(pharmacyId)
                                    return true
                                }
                            }
                        }
                    }
                }
                return false
            }
        })
    }

    private fun searchPharmaciesWithMedicine(medicineName: String) {
        if (!isFragmentActive) return

        Log.d("MapFragment", "🔍 SEARCHING for medicine: $medicineName")

        mapView?.overlays?.clear()
        showToast("Searching for $medicineName...")

        db.collection("Pharmacies")
            .get()
            .addOnSuccessListener { pharmacyResult ->
                if (!isFragmentActive) return@addOnSuccessListener

                val totalPharmacies = pharmacyResult.size()
                Log.d("MapFragment", "Found $totalPharmacies pharmacies")

                if (totalPharmacies == 0) {
                    showToast("No pharmacies found")
                    return@addOnSuccessListener
                }

                var completedQueries = 0

                pharmacyResult.documents.forEach { pharmacyDoc ->
                    val pharmacyId = pharmacyDoc.id
                    val pharmacyName = pharmacyDoc.getString("pharmacy_name") ?: "Unknown Pharmacy"
                    val location = pharmacyDoc.getGeoPoint("Location")

                    Log.d("MapFragment", "Checking pharmacy: $pharmacyName")

                    if (location != null) {
                        checkMedicineStock(pharmacyId, pharmacyName, location, medicineName) { hasMedicine ->
                            completedQueries++
                            Log.d("MapFragment", "Completed $completedQueries/$totalPharmacies - $pharmacyName has $medicineName: $hasMedicine")

                            // Pass pharmacyId to addPharmacyMarker
                            addPharmacyMarker(pharmacyName, location, hasMedicine, medicineName, pharmacyId)

                            if (completedQueries == totalPharmacies) {
                                showSearchResultsSummary(medicineName, totalPharmacies)
                            }
                        }
                    } else {
                        completedQueries++
                        Log.d("MapFragment", "Skipping pharmacy (no location): $pharmacyName")
                    }
                }
            }
            .addOnFailureListener { e ->
                if (!isFragmentActive) return@addOnFailureListener
                Log.e("MapFragment", "Error loading pharmacies", e)
                showToast("Error loading pharmacies")
            }
    }

    private fun checkMedicineStock(
        pharmacyId: String,
        pharmacyName: String,
        location: com.google.firebase.firestore.GeoPoint,
        medicineName: String,
        onComplete: (Boolean) -> Unit
    ) {
        Log.d("MapFragment", "Checking stock for $pharmacyName - Medicine: $medicineName")

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

                Log.d("MapFragment", "🎯 RESULT: $pharmacyName - $medicineName available: $hasMedicine (Found ${inventoryResult.size()} items)")

                // addPharmacyMarker is now called from the callback above with pharmacyId
                onComplete(hasMedicine)
            }
            .addOnFailureListener { e ->
                if (!isFragmentActive) return@addOnFailureListener
                Log.e("MapFragment", "Error checking inventory for $pharmacyName", e)
                // addPharmacyMarker is now called from the callback above with pharmacyId
                onComplete(false)
            }
    }

    private fun addPharmacyMarker(
        name: String,
        location: com.google.firebase.firestore.GeoPoint,
        hasMedicine: Boolean,
        medicineName: String,
        pharmacyId: String
    ) {
        try {
            val currentMapView = mapView
            if (currentMapView == null) {
                Log.e("MapFragment", "MapView is null when adding marker for $name")
                return
            }

            val marker = Marker(currentMapView)
            marker.position = GeoPoint(location.latitude, location.longitude)

            // Store pharmacy ID in the marker
            marker.setRelatedObject(pharmacyId)

            val markerColor = if (hasMedicine) Color.GREEN else Color.RED
            val markerDrawable = createColoredCircleMarker(markerColor)

            if (hasMedicine) {
                marker.title = "✅ $name - HAS $medicineName"
                Log.d("MapFragment", "🟢 GREEN marker for $name")
            } else {
                marker.title = "❌ $name - NO $medicineName"
                Log.d("MapFragment", "🔴 RED marker for $name")
            }

            marker.setIcon(markerDrawable)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            marker.setPanToView(false)

            // Add click listener to marker
            marker.setOnMarkerClickListener { marker, mapView ->
                Log.d("MapFragment", "🎯 Marker clicked: ${marker.title}")
                val clickedPharmacyId = marker.relatedObject as? String
                if (clickedPharmacyId != null) {
                    openReservationFragment(clickedPharmacyId)
                }
                true
            }

            currentMapView.overlays.add(marker)
            currentMapView.invalidate()

        } catch (e: Exception) {
            Log.e("MapFragment", "Error adding marker for $name: ${e.message}")
        }
    }


    private fun createColoredCircleMarker(color: Int): Drawable {
        val size = 40
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

        return BitmapDrawable(resources, bitmap)
    }

    private fun showAllPharmacies() {
        if (!isFragmentActive) return

        db.collection("Pharmacies")
            .get()
            .addOnSuccessListener { result ->
                if (!isFragmentActive) return@addOnSuccessListener

                mapView?.overlays?.clear()
                result.documents.forEach { document ->
                    val location = document.getGeoPoint("Location")
                    val name = document.getString("pharmacy_name") ?: "Unknown Pharmacy"
                    val pharmacyId = document.id  // Get the pharmacy ID

                    if (location != null) {
                        addSimpleMarker(name, location, pharmacyId)
                    }
                }
                mapView?.invalidate()
                showToast("Showing ${result.size()} pharmacies")
            }
            .addOnFailureListener { e ->
                if (!isFragmentActive) return@addOnFailureListener
                Log.e("MapFragment", "Error loading pharmacies", e)
                showToast("Error loading pharmacies")
            }
    }

    private fun addSimpleMarker(
        name: String,
        location: com.google.firebase.firestore.GeoPoint,
        pharmacyId: String
    ) {
        try {
            val currentMapView = mapView
            if (currentMapView == null) {
                Log.e("MapFragment", "MapView is null when adding simple marker for $name")
                return
            }

            val marker = Marker(currentMapView)
            marker.position = GeoPoint(location.latitude, location.longitude)
            marker.title = name

            // Store pharmacy ID in the marker
            marker.setRelatedObject(pharmacyId)

            val neutralMarker = createColoredCircleMarker(Color.BLUE)
            marker.setIcon(neutralMarker)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            marker.setPanToView(false)

            // Add click listener to marker
            marker.setOnMarkerClickListener { marker, mapView ->
                Log.d("MapFragment", "🎯 Marker clicked: ${marker.title}")
                val clickedPharmacyId = marker.relatedObject as? String
                if (clickedPharmacyId != null) {
                    openReservationFragment(clickedPharmacyId)
                }
                true
            }

            currentMapView.overlays.add(marker)

        } catch (e: Exception) {
            Log.e("MapFragment", "Error adding simple marker for $name: ${e.message}")
        }
    }

    private fun showSearchResultsSummary(medicineName: String, totalPharmacies: Int) {
        if (!isFragmentActive) return

        val availableCount = mapView?.overlays?.count { overlay ->
            overlay is Marker && overlay.title?.contains("✅") == true
        } ?: 0

        val message = if (availableCount > 0) {
            "Found $availableCount out of $totalPharmacies pharmacies with $medicineName"
        } else {
            "No pharmacies found with $medicineName in stock"
        }

        Log.d("MapFragment", "📊 $message")
        showToast(message)
    }

    private fun openReservationFragment(pharmacyId: String) {
        try {
            Log.d("MapFragment", "🚀 Opening reservation fragment for pharmacy: $pharmacyId")

            if (LoginUtils.isGuestUser(requireContext())) {
                Log.d("MapFragment", "❌ User is GUEST, cannot reserve")
                Toast.makeText(requireContext(),
                    "Guests cannot make reservations. Please create an account or login.",
                    Toast.LENGTH_LONG).show()
                LoginUtils.redirectToLogin(requireContext())
                return
            }

            // Check if user is logged in AND is a customer
            if (!LoginUtils.isUserLoggedIn(requireContext())) {
                Log.d("MapFragment", "❌ User not logged in")
                LoginUtils.redirectToLogin(requireContext(), "Please login to reserve medicines")
                return
            }

            if (!LoginUtils.isCustomer(requireContext())) {
                Log.d("MapFragment", "❌ User is not a customer")
                Toast.makeText(requireContext(),
                    "Only customers can make reservations. Please login as a customer.",
                    Toast.LENGTH_LONG).show()
                return
            }

            // Create the reservation fragment
            val reservationFragment = MedicineReservationFragment.newInstance(pharmacyId)

            // Get the container and make it visible
            val container = view?.findViewById<ViewGroup>(R.id.fragment_container)
            if (container != null) {
                container.visibility = View.VISIBLE
                Log.d("MapFragment", "✅ Container made visible")
            }

            // Use child fragment manager since this is already a fragment
            childFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_up,
                    R.anim.slide_out_down,
                    R.anim.slide_in_up,
                    R.anim.slide_out_down
                )
                .replace(R.id.fragment_container, reservationFragment, "RESERVATION_FRAGMENT")
                .addToBackStack("reservation")
                .commit()

            Log.d("MapFragment", "✅ Transaction committed")

        } catch (e: Exception) {
            Log.e("MapFragment", "❌ Error opening reservation fragment: ${e.message}", e)
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

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentActive = false

        // Stop location updates to save battery
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e("MapFragment", "Error removing location updates", e)
        }

        mapView = null
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }
}