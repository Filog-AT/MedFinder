package com.example.medfinder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class ReservationAdapter(
    private val reservationList: List<Reservation>
) : RecyclerView.Adapter<ReservationAdapter.ReservationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reservation, parent, false)
        return ReservationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReservationViewHolder, position: Int) {
        val reservation = reservationList[position]

        // Format date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
        val date = Date(reservation.created_at)

        holder.dateText.text = dateFormat.format(date)
        holder.statusText.text = reservation.status.uppercase()
        holder.totalText.text = "Total: ₱${reservation.total_price}"

        // Set status color
        val statusColor = when (reservation.status.lowercase()) {
            "pending" -> android.graphics.Color.parseColor("#FFA726") // Orange
            "confirmed" -> android.graphics.Color.parseColor("#4CAF50") // Green
            "completed" -> android.graphics.Color.parseColor("#2196F3") // Blue
            "cancelled" -> android.graphics.Color.parseColor("#F44336") // Red
            else -> android.graphics.Color.GRAY
        }
        holder.statusText.setTextColor(statusColor)

        // Show medicine count
        holder.medicineCount.text = "${reservation.medicines.size} medicine(s)"

        // Load pharmacy name (optional - you can fetch this separately)
        holder.pharmacyName.text = "Pharmacy ID: ${reservation.pharmacy_id}"
    }

    override fun getItemCount(): Int = reservationList.size

    class ReservationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateText: TextView = itemView.findViewById(R.id.tv_date)
        val statusText: TextView = itemView.findViewById(R.id.tv_status)
        val totalText: TextView = itemView.findViewById(R.id.tv_total)
        val medicineCount: TextView = itemView.findViewById(R.id.tv_medicine_count)
        val pharmacyName: TextView = itemView.findViewById(R.id.tv_pharmacy_name)
    }
}