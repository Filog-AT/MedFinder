package com.example.medfinder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MapFragment : Fragment() {

    private lateinit var mapView: MapView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_map, container, false)

        Configuration.getInstance().load(requireContext(),
            requireContext().getSharedPreferences("osm_prefs", 0))

        mapView = view.findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        mapView.setBuiltInZoomControls(true)

        val startPoint = GeoPoint(14.5995, 120.9842) // manila
        val mapController = mapView.controller
        mapController.setZoom(15.0)
        mapController.setCenter(startPoint)

        val db = FirebaseFirestore.getInstance()
        db.collection("Pharmacies")
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val location = document.getGeoPoint("Location")
                    val name = document.getString("pharmacy_name") ?: "Unknown"

                    if (location != null) {
                        val marker = Marker(mapView)
                        marker.position = GeoPoint(location.latitude, location.longitude)
                        marker.title = name
                        mapView.overlays.add(marker)
                    }
                }
                mapView.invalidate()
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }

        return view
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
