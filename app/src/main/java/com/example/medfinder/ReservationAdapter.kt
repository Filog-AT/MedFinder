package com.example.medfinder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class ReservationAdapter(
    private val reservationList: List<Reservation>,
    private val onCancelClickListener: (Reservation) -> Unit,
    private val onTrackClickListener: (Reservation) -> Unit
) : RecyclerView.Adapter<ReservationAdapter.ReservationViewHolder>() {

    // Store expanded positions
    private val expandedPositions = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reservation, parent, false)
        return ReservationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReservationViewHolder, position: Int) {
        val reservation = reservationList[position]
        val isExpanded = expandedPositions.contains(position)

        // Format date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
        val date = Date(reservation.created_at)

        holder.dateText.text = dateFormat.format(date)

        // FIX: Update status text to show time limit
        updateStatusText(holder.statusText, reservation)

        holder.totalText.text = "Total: ₱${reservation.total_price}"

        // Set status color
        val statusColor = when (reservation.status.lowercase()) {
            "pending" -> android.graphics.Color.parseColor("#FFA726") // Orange
            "time_limit_set" -> android.graphics.Color.parseColor("#2196F3") // Blue for time limit
            "confirmed" -> android.graphics.Color.parseColor("#4CAF50") // Green
            "completed" -> android.graphics.Color.parseColor("#2196F3") // Blue
            "cancelled" -> android.graphics.Color.parseColor("#F44336") // Red
            "received" -> android.graphics.Color.parseColor("#9C27B0") // Purple for received
            else -> android.graphics.Color.GRAY
        }
        holder.statusText.setTextColor(statusColor)

        // FIX: Show total items count, not just medicine types
        val totalItems = reservation.medicines.sumOf { it.quantity }
        holder.medicineCount.text = "$totalItems item(s)"

        // Also update the medicine display to show quantities
        if (isExpanded) {
            setupMedicinesList(holder, reservation)
        }

        // Load pharmacy name - you might want to fetch pharmacy name from Firestore
        holder.pharmacyName.text = "Pharmacy ID: ${reservation.pharmacy_id}"

        // Handle expand/collapse
        holder.expandedSection.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.viewDetailsBtn.text = if (isExpanded) "Hide Details" else "View Details"

        // Set up button click listeners
        holder.viewDetailsBtn.setOnClickListener {
            if (isExpanded) {
                expandedPositions.remove(position)
            } else {
                expandedPositions.add(position)
            }
            notifyItemChanged(position)
        }

        holder.cancelBtn.setOnClickListener {
            onCancelClickListener(reservation)
        }

        holder.trackBtn.setOnClickListener {
            onTrackClickListener(reservation)
        }

        // Hide cancel button if order is already cancelled or completed
        if (reservation.status.lowercase() == "cancelled" ||
            reservation.status.lowercase() == "completed" ||
            reservation.status.lowercase() == "received" ||
            reservation.status.lowercase() == "confirmed") {
            holder.cancelBtn.visibility = View.GONE
        } else {
            holder.cancelBtn.visibility = View.VISIBLE
        }

        // Hide track button for cancelled orders
        if (reservation.status.lowercase() == "cancelled") {
            holder.trackBtn.visibility = View.GONE
        } else {
            holder.trackBtn.visibility = View.VISIBLE
        }
    }

    // FIX: New method to properly display status text
    private fun updateStatusText(statusView: TextView, reservation: Reservation) {
        when (reservation.status.lowercase()) {
            "pending" -> {
                statusView.text = "PENDING"
            }
            "time_limit_set" -> {
                // Show time limit if available
                if (reservation.time_limit_minutes > 0) {
                    statusView.text = "${reservation.time_limit_minutes} MIN"
                } else {
                    statusView.text = "CONFIRMED"
                }
            }
            "confirmed" -> {
                statusView.text = "CONFIRMED"
            }
            "cancelled" -> {
                statusView.text = "CANCELLED"
            }
            "received" -> {
                statusView.text = "RECEIVED"
            }
            "completed" -> {
                statusView.text = "COMPLETED"
            }
            else -> {
                statusView.text = reservation.status.uppercase()
            }
        }
    }

    private fun setupMedicinesList(holder: ReservationViewHolder, reservation: Reservation) {
        // Clear existing views
        holder.medicinesContainer.removeAllViews()

        // Inflate and add medicine items
        reservation.medicines.forEach { medicine ->
            val medicineView = LayoutInflater.from(holder.itemView.context)
                .inflate(R.layout.item_medicine, holder.medicinesContainer, false)

            val medicineName = medicineView.findViewById<TextView>(R.id.tv_medicine_name)
            val medicineQuantity = medicineView.findViewById<TextView>(R.id.tv_medicine_quantity)
            val medicinePrice = medicineView.findViewById<TextView>(R.id.tv_medicine_price)

            // Check if these views exist, if not, use alternative approach
            if (medicinePrice != null) {
                medicinePrice.text = "₱${medicine.price * medicine.quantity}"
            }

            medicineName.text = medicine.medicine_name
            medicineQuantity.text = "Qty: ${medicine.quantity}"

            holder.medicinesContainer.addView(medicineView)
        }
    }

    override fun getItemCount(): Int = reservationList.size

    class ReservationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateText: TextView = itemView.findViewById(R.id.tv_date)
        val statusText: TextView = itemView.findViewById(R.id.tv_status)
        val totalText: TextView = itemView.findViewById(R.id.tv_total)
        val medicineCount: TextView = itemView.findViewById(R.id.tv_medicine_count)
        val pharmacyName: TextView = itemView.findViewById(R.id.tv_pharmacy_name)
        val viewDetailsBtn: Button = itemView.findViewById(R.id.btn_view_details)
        val expandedSection: LinearLayout = itemView.findViewById(R.id.expanded_section)
        val medicinesContainer: LinearLayout = itemView.findViewById(R.id.medicines_container)
        val cancelBtn: Button = itemView.findViewById(R.id.btn_cancel)
        val trackBtn: Button = itemView.findViewById(R.id.btn_track)
    }
}