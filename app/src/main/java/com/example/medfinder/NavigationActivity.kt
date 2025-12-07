package com.example.medfinder

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.IOException
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.atan2

class NavigationActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var pharmacyName: String

    private lateinit var tvDistance: TextView
    private lateinit var tvInstruction: TextView
    private lateinit var tvEta: TextView

    private var currentLocation: Location? = null
    private var destinationLat: Double = 0.0
    private var destinationLng: Double = 0.0
    private var routePolyline: Polyline? = null

    // Your API key
    private val OPEN_ROUTE_SERVICE_API_KEY = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6IjA2N2QyZjIyZmUwMjRkM2U5YmY3NWRhMjY5NjhjNGViIiwiaCI6Im11cm11cjY0In0="

    companion object {
        private const val TAG = "NavigationActivity"

        fun startNavigation(
            context: Context,
            pharmacyId: String,
            pharmacyName: String,
            destinationLat: Double,
            destinationLng: Double,
            userLocation: Location
        ) {
            val intent = Intent(context, NavigationActivity::class.java)
            intent.putExtra("pharmacy_id", pharmacyId)
            intent.putExtra("pharmacy_name", pharmacyName)
            intent.putExtra("destination_lat", destinationLat)
            intent.putExtra("destination_lng", destinationLng)
            intent.putExtra("user_location", userLocation)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation)

        // Keep screen on during navigation
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Get data from intent
        pharmacyName = intent.getStringExtra("pharmacy_name") ?: ""
        destinationLat = intent.getDoubleExtra("destination_lat", 0.0)
        destinationLng = intent.getDoubleExtra("destination_lng", 0.0)
        currentLocation = intent.getParcelableExtra<Location>("user_location")

        // Log what we received
        Log.d(TAG, "=== NAVIGATION START ===")
        Log.d(TAG, "To: $pharmacyName")
        Log.d(TAG, "Destination: $destinationLat, $destinationLng")
        Log.d(TAG, "User: ${currentLocation?.latitude}, ${currentLocation?.longitude}")

        if (currentLocation == null || destinationLat == 0.0 || destinationLng == 0.0) {
            Toast.makeText(this, "Navigation data missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        setupMap()

        // Draw a straight line first (immediate feedback)
        drawStraightLine()

        // Then fetch road route in background
        fetchRoadRoute()
    }

    private fun setupUI() {
        mapView = findViewById(R.id.nav_map_view)
        tvDistance = findViewById(R.id.tv_nav_distance)
        tvInstruction = findViewById(R.id.tv_nav_instruction)
        tvEta = findViewById(R.id.tv_nav_eta)

        // Set pharmacy name in title
        findViewById<TextView>(R.id.tv_nav_title).text = "Navigating to $pharmacyName"

        // Setup end navigation button
        findViewById<View>(R.id.btn_end_navigation).setOnClickListener {
            finish()
        }
    }

    private fun setupMap() {
        Configuration.getInstance().load(this, getSharedPreferences("osm_prefs", 0))

        mapView.setTileSource(XYTileSource(
            "CartoDB Voyager",
            0, 18, 256, ".png",
            arrayOf("https://a.basemaps.cartocdn.com/rastertiles/voyager/"),
            "© OpenStreetMap contributors © CartoDB"
        ))

        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)

        // Center on user location
        currentLocation?.let { location ->
            val userPoint = GeoPoint(location.latitude, location.longitude)
            mapView.controller.setZoom(16.0)
            mapView.controller.setCenter(userPoint)

            // Add user marker (simple circle)
            Marker(mapView).apply {
                position = userPoint
                title = "You are here"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                mapView.overlays.add(this)
            }
        }

        // Add destination marker
        Marker(mapView).apply {
            position = GeoPoint(destinationLat, destinationLng)
            title = pharmacyName
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            mapView.overlays.add(this)
        }

        Log.d(TAG, "Map setup complete")
    }

    private fun drawStraightLine() {
        Log.d(TAG, "Drawing straight line (immediate feedback)")

        currentLocation?.let { location ->
            val points = listOf(
                GeoPoint(location.latitude, location.longitude),
                GeoPoint(destinationLat, destinationLng)
            )

            routePolyline = Polyline().apply {
                setPoints(points)
                color = Color.RED
                width = 20.0f
            }

            mapView.overlays.add(routePolyline)
            mapView.invalidate()

            // Update UI with straight line distance
            val distance = calculateDistance(
                location.latitude, location.longitude,
                destinationLat, destinationLng
            )
            val decimalFormat = DecimalFormat("#.#")
            tvDistance.text = "${decimalFormat.format(distance)} km"
            tvEta.text = "ETA: Calculating..."
            tvInstruction.text = "Getting road route..."

            Toast.makeText(this, "Calculating road route...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchRoadRoute() {
        currentLocation?.let { userLocation ->
            val start = "${userLocation.longitude},${userLocation.latitude}"
            val end = "$destinationLng,$destinationLat"

            Log.d(TAG, "Fetching road route: $start -> $end")

            val url = "https://api.openrouteservice.org/v2/directions/driving-car?start=$start&end=$end"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", OPEN_ROUTE_SERVICE_API_KEY)
                .addHeader("Accept", "application/geo+json;charset=UTF-8")
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Network error: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this@NavigationActivity, "Network error", Toast.LENGTH_SHORT).show()
                        // Keep the red straight line as fallback
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string()
                            if (!responseBody.isNullOrEmpty()) {
                                parseAndDrawRoute(responseBody)
                            } else {
                                runOnUiThread {
                                    Toast.makeText(this@NavigationActivity, "Empty response", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Log.e(TAG, "API error: ${response.code}")
                            runOnUiThread {
                                Toast.makeText(this@NavigationActivity, "API error ${response.code}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            })
        }
    }

    private fun parseAndDrawRoute(jsonString: String) {
        try {
            val json = JSONObject(jsonString)

            if (json.has("features")) {
                val features = json.getJSONArray("features")
                if (features.length() > 0) {
                    val feature = features.getJSONObject(0)
                    val geometry = feature.getJSONObject("geometry")

                    if (geometry.getString("type") == "LineString") {
                        val coordinates = geometry.getJSONArray("coordinates")

                        // Extract points
                        val points = mutableListOf<GeoPoint>()
                        for (i in 0 until coordinates.length()) {
                            val coord = coordinates.getJSONArray(i)
                            val lon = coord.getDouble(0)
                            val lat = coord.getDouble(1)
                            points.add(GeoPoint(lat, lon))
                        }

                        // Get route info
                        var distance = 0.0
                        var duration = 0.0

                        val properties = feature.optJSONObject("properties")
                        if (properties != null) {
                            val summary = properties.optJSONObject("summary")
                            if (summary != null) {
                                distance = summary.optDouble("distance", 0.0)
                                duration = summary.optDouble("duration", 0.0)
                            }
                        }

                        runOnUiThread {
                            drawRoadRoute(points, distance, duration)
                        }
                        return
                    }
                }
            }

            runOnUiThread {
                Toast.makeText(this@NavigationActivity, "Could not parse route", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
            runOnUiThread {
                Toast.makeText(this@NavigationActivity, "Parse error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun drawRoadRoute(points: List<GeoPoint>, distanceMeters: Double, durationSeconds: Double) {
        Log.d(TAG, "Drawing road route with ${points.size} points")

        // Remove the straight line
        routePolyline?.let {
            mapView.overlays.remove(it)
        }

        // Create road route
        routePolyline = Polyline().apply {
            setPoints(points)
            color = Color.BLUE
            width = 25.0f
        }

        mapView.overlays.add(routePolyline)
        mapView.invalidate()

        // Update UI
        val distanceKm = distanceMeters / 1000.0
        val decimalFormat = DecimalFormat("#.#")
        tvDistance.text = "${decimalFormat.format(distanceKm)} km"

        val etaMinutes = (durationSeconds / 60).toInt()
        tvEta.text = "ETA: $etaMinutes min"

        tvInstruction.text = "Road route loaded"

        // Zoom to show route
        if (points.size >= 2) {
            try {
                val boundingBox = BoundingBox.fromGeoPoints(points)
                val paddedBox = boundingBox.increaseByScale(1.3f)
                mapView.zoomToBoundingBox(paddedBox, false, 100)
            } catch (e: Exception) {
                // Ignore zoom errors
            }
        }

        Toast.makeText(this, "✅ Road route loaded!", Toast.LENGTH_LONG).show()
        Log.d(TAG, "✅ Road route drawn successfully")
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return earthRadius * c
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