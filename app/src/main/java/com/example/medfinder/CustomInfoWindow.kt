package com.example.medfinder

import android.view.View
import android.widget.TextView
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.infowindow.InfoWindow

class CustomInfoWindow(
    layoutResId: Int,
    mapView: MapView
) : InfoWindow(layoutResId, mapView) {

    override fun onOpen(item: Any?) {
        if (item is MapFragment.PharmacyData) {
            val titleView = mView.findViewById<TextView>(R.id.title)
            val distanceView = mView.findViewById<TextView>(R.id.distance)
            val statusView = mView.findViewById<TextView>(R.id.status)

            titleView.text = item.name

            val decimalFormat = java.text.DecimalFormat("#.#")
            val distanceStr = if (item.distance > 0) {
                "${decimalFormat.format(item.distance)} km away"
            } else {
                "Distance unknown"
            }
            distanceView.text = distanceStr

            if (item.medicineName.isNotEmpty()) {
                val status = if (item.hasMedicine) "✅ HAS ${item.medicineName}" else "❌ NO ${item.medicineName}"
                statusView.text = status
                statusView.visibility = View.VISIBLE
            } else {
                statusView.visibility = View.GONE
            }
        }
    }

    override fun onClose() {
    }
}