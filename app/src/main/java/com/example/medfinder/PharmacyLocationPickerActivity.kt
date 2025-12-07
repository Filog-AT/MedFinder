package com.example.medfinder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class PharmacyLocationPickerActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var selectedLocation: GeoPoint? = null

    companion object {
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pharmacy_location_picker_osm)

        // Initialize OSMDroid configuration - SAME as MapFragment
        Configuration.getInstance().load(this, getSharedPreferences("osm_prefs", 0))

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Create MapView programmatically
        mapView = MapView(this)
        val mapContainer = findViewById<FrameLayout>(R.id.map_container)
        mapContainer.addView(mapView)

        setupMap()

        findViewById<Button>(R.id.btn_select_location).setOnClickListener {
            selectedLocation?.let { location ->
                val intent = Intent().apply {
                    putExtra(EXTRA_LATITUDE, location.latitude)
                    putExtra(EXTRA_LONGITUDE, location.longitude)
                }
                setResult(RESULT_OK, intent)
                finish()
            } ?: run {
                Toast.makeText(this, "Please select a location on the map", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun setupMap() {
        // Use EXACTLY the same tile source as MapFragment
        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.XYTileSource(
            "CartoDB Voyager",
            0, 18, 256, ".png",
            arrayOf("https://a.basemaps.cartocdn.com/rastertiles/voyager/"),
            "© OpenStreetMap contributors © CartoDB"
        ))

        mapView.setMultiTouchControls(true)

        // Set default location (Baguio City) - SAME coordinates as MapFragment
        val defaultLocation = GeoPoint(16.41197, 120.59341)
        mapView.controller.setZoom(18.0)  // Same zoom level
        mapView.controller.setCenter(defaultLocation)

        // Map click listener
        mapView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val projection = mapView.projection
                val geoPoint = projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                selectLocation(geoPoint)
            }
            false
        }
    }

    private fun selectLocation(geoPoint: GeoPoint) {
        selectedLocation = geoPoint

        // Clear existing markers
        mapView.overlays.clear()

        // Add new marker with same style as MapFragment
        val marker = Marker(mapView).apply {
            position = geoPoint
            title = "Pharmacy Location"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            // Create marker with same style as in MapFragment
            val markerDrawable = createColoredCircleMarker(android.graphics.Color.BLUE)
            setIcon(markerDrawable)
        }

        mapView.overlays.add(marker)
        mapView.controller.animateTo(geoPoint)
        mapView.invalidate()

        // Show coordinates in toast
        Toast.makeText(this,
            "Location: ${String.format("%.6f", geoPoint.latitude)}, ${String.format("%.6f", geoPoint.longitude)}",
            Toast.LENGTH_SHORT
        ).show()
    }

    // Same marker creation function as in MapFragment
    private fun createColoredCircleMarker(color: Int): android.graphics.drawable.Drawable {
        val size = 40
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val paint = android.graphics.Paint().apply {
            this.color = color
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }

        val borderPaint = android.graphics.Paint().apply {
            this.color = android.graphics.Color.WHITE
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f
        }

        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, borderPaint)

        val drawable = android.graphics.drawable.BitmapDrawable(resources, bitmap)
        drawable.setBounds(0, 0, size, size)

        return drawable
    }

    private fun getCurrentLocation() {
        if (checkLocationPermission()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val currentLocation = GeoPoint(it.latitude, it.longitude)
                    mapView.controller.animateTo(currentLocation)
                    selectLocation(currentLocation)
                }
            }
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
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