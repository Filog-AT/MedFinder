package com.example.medfinder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MapFragment : Fragment() {

    private var mapView: MapView? = null
    private val db = FirebaseFirestore.getInstance()
    private lateinit var sharedViewModel: SharedViewModel
    private var isFragmentActive = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_map, container, false)

        Configuration.getInstance().load(requireContext(),
            requireContext().getSharedPreferences("osm_prefs", 0))

        mapView = view.findViewById(R.id.mapView)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isFragmentActive = true

        sharedViewModel = ViewModelProvider(requireActivity())[SharedViewModel::class.java]

        setupMap()

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

        val startPoint = GeoPoint(16.41197, 120.59341)
        val mapController = currentMapView.controller
        mapController.setZoom(18.0)
        mapController.setCenter(startPoint)

        // Add marker click listener
        currentMapView.overlays.add(object : org.osmdroid.views.overlay.Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
                Log.d("MapFragment", "✅ Map tapped at coordinates: ${e?.x}, ${e?.y}")

                if (e != null && mapView != null) {
                    val projection = mapView.projection
                    val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt())
                    Log.d("MapFragment", "📍 GeoPoint: $geoPoint")

                    // Check if a marker was clicked
                    for (overlay in mapView.overlays.reversed()) {
                        if (overlay is Marker) {
                            val markerPoint = overlay.position
                            val distance = markerPoint.distanceToAsDouble(geoPoint)
                            Log.d("MapFragment", "📌 Checking marker: ${overlay.title}, distance: $distance meters")

                            // If click is near the marker (within 50m) - reduced threshold
                            if (distance < 0.0001) { // Reduced from 0.0005 to 0.0001
                                val pharmacyId = overlay.relatedObject as? String
                                Log.d("MapFragment", "🎯 Marker clicked! Pharmacy ID: $pharmacyId")
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