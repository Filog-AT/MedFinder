package com.example.medfinder

import android.app.Dialog
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.firebase.firestore.GeoPoint
import java.text.DecimalFormat

class DirectionFragment : DialogFragment() {

    private lateinit var pharmacyData: MapFragment.PharmacyData
    private lateinit var userLocation: Location

    companion object {
        private const val TAG = "DirectionFragment"

        fun newInstance(pharmacyData: MapFragment.PharmacyData, userLocation: Location): DirectionFragment {
            val fragment = DirectionFragment()
            val args = Bundle()
            args.putSerializable("pharmacy_data", pharmacyData)
            args.putParcelable("user_location", userLocation)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), R.style.FullScreenDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_direction, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get data
        pharmacyData = arguments?.getSerializable("pharmacy_data") as MapFragment.PharmacyData
        userLocation = arguments?.getParcelable("user_location")!!

        setupUI(view)

        // Make dialog full screen
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun setupUI(view: View) {
        val tvPharmacyName = view.findViewById<TextView>(R.id.tv_pharmacy_name)
        val tvDistance = view.findViewById<TextView>(R.id.tv_distance)
        val tvDirections = view.findViewById<TextView>(R.id.tv_directions)
        val tvEstimatedTime = view.findViewById<TextView>(R.id.tv_estimated_time)
        val btnClose = view.findViewById<Button>(R.id.btn_close)

        // Set pharmacy name
        tvPharmacyName.text = "Directions to ${pharmacyData.name}"

        // Calculate distance
        val distance = calculateDistance(
            userLocation.latitude,
            userLocation.longitude,
            pharmacyData.location.latitude,
            pharmacyData.location.longitude
        )

        val decimalFormat = DecimalFormat("#.#")
        val distanceKm = decimalFormat.format(distance)
        tvDistance.text = "Distance: $distanceKm km"

        // Calculate estimated time (assuming 30 km/h average speed)
        val estimatedMinutes = (distance / 30.0 * 60.0).toInt()
        tvEstimatedTime.text = "Estimated time: $estimatedMinutes minutes"

        // Generate simple directions
        val directions = generateSimpleDirections(
            userLocation.latitude,
            userLocation.longitude,
            pharmacyData.location.latitude,
            pharmacyData.location.longitude
        )

        tvDirections.text = directions

        // Close button
        btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    private fun generateSimpleDirections(startLat: Double, startLng: Double,
                                         endLat: Double, endLng: Double): String {
        val directions = StringBuilder()
        directions.append("Route to Pharmacy:\n\n")

        // Basic cardinal direction
        val latDiff = endLat - startLat
        val lngDiff = endLng - startLng

        if (Math.abs(latDiff) > Math.abs(lngDiff)) {
            if (latDiff > 0) {
                directions.append("1. Head North\n")
            } else {
                directions.append("1. Head South\n")
            }
        } else {
            if (lngDiff > 0) {
                directions.append("1. Head East\n")
            } else {
                directions.append("1. Head West\n")
            }
        }

        directions.append("2. Continue straight for ${String.format("%.1f", calculateDistance(startLat, startLng, endLat, endLng))} km\n")
        directions.append("3. You will arrive at your destination\n\n")
        directions.append("Note: For detailed turn-by-turn navigation, please use Google Maps.")

        return directions.toString()
    }
}