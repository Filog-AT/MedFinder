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
import android.view.View
import android.view.ViewGroup
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

        // Get the shared ViewModel
        sharedViewModel = ViewModelProvider(requireActivity())[SharedViewModel::class.java]

        setupMap()

        // Wait a bit longer to ensure everything is initialized
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
        }, 1000) // Increased delay to 1 second
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

        // TEMPORARY: Query only by medicine_name first, then filter stock locally
        db.collection("Pharmacies")
            .document(pharmacyId)
            .collection("Medicines")
            .whereEqualTo("medicine_name", medicineName)
            .get()
            .addOnSuccessListener { inventoryResult ->
                if (!isFragmentActive) return@addOnSuccessListener

                // Filter for stock > 0 locally
                val hasMedicine = inventoryResult.any { document ->
                    val stock = document.getLong("stock") ?: 0
                    stock > 0
                }

                Log.d("MapFragment", "🎯 RESULT: $pharmacyName - $medicineName available: $hasMedicine (Found ${inventoryResult.size()} items)")

                addPharmacyMarker(pharmacyName, location, hasMedicine, medicineName)
                onComplete(hasMedicine)
            }
            .addOnFailureListener { e ->
                if (!isFragmentActive) return@addOnFailureListener
                Log.e("MapFragment", "Error checking inventory for $pharmacyName", e)
                addPharmacyMarker(pharmacyName, location, false, medicineName)
                onComplete(false)
            }
    }

    private fun addPharmacyMarker(
        name: String,
        location: com.google.firebase.firestore.GeoPoint,
        hasMedicine: Boolean,
        medicineName: String
    ) {
        try {
            val currentMapView = mapView
            if (currentMapView == null) {
                Log.e("MapFragment", "MapView is null when adding marker for $name")
                return
            }

            val marker = Marker(currentMapView)
            marker.position = GeoPoint(location.latitude, location.longitude)

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

                    if (location != null) {
                        addSimpleMarker(name, location)
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

    private fun addSimpleMarker(name: String, location: com.google.firebase.firestore.GeoPoint) {
        try {
            val currentMapView = mapView
            if (currentMapView == null) {
                Log.e("MapFragment", "MapView is null when adding simple marker for $name")
                return
            }

            val marker = Marker(currentMapView)
            marker.position = GeoPoint(location.latitude, location.longitude)
            marker.title = name

            val neutralMarker = createColoredCircleMarker(Color.BLUE)
            marker.setIcon(neutralMarker)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            marker.setPanToView(false)

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