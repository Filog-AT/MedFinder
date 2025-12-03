package com.example.medfinder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class ReservationItemAdapter(
    private val reservationList: List<Reservation>,
    private val onStatusUpdate: (reservationId: String, newStatus: String) -> Unit
) : RecyclerView.Adapter<ReservationItemAdapter.ReservationItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservationItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pharmacy_reservation, parent, false)
        return ReservationItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReservationItemViewHolder, position: Int) {
        val reservation = reservationList[position]

        // Format date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
        val date = Date(reservation.created_at)

        holder.dateText.text = dateFormat.format(date)
        holder.totalText.text = "₱${reservation.total_price}"
        holder.medicineCount.text = "${reservation.medicines.size} item(s)"

        // Show medicines summary
        val medicinesSummary = reservation.medicines.joinToString(", ") {
            "${it.medicine_name} (x${it.quantity})"
        }
        holder.medicinesText.text = medicinesSummary

        // Setup buttons
        holder.btnConfirm.setOnClickListener {
            onStatusUpdate(reservation.id!!, "confirmed")
        }

        holder.btnComplete.setOnClickListener {
            onStatusUpdate(reservation.id!!, "completed")
        }

        holder.btnCancel.setOnClickListener {
            onStatusUpdate(reservation.id!!, "cancelled")
        }

        // Disable buttons based on current status
        when (reservation.status.lowercase()) {
            "confirmed" -> {
                holder.btnConfirm.isEnabled = false
                holder.btnComplete.isEnabled = true
                holder.btnCancel.isEnabled = true
                holder.btnConfirm.text = "Confirmed"
                holder.btnConfirm.alpha = 0.5f
            }
            "completed" -> {
                holder.btnConfirm.isEnabled = false
                holder.btnComplete.isEnabled = false
                holder.btnCancel.isEnabled = false
                holder.btnComplete.text = "Completed"
                holder.btnConfirm.alpha = 0.5f
                holder.btnComplete.alpha = 0.5f
                holder.btnCancel.alpha = 0.5f
            }
            "cancelled" -> {
                holder.btnConfirm.isEnabled = false
                holder.btnComplete.isEnabled = false
                holder.btnCancel.isEnabled = false
                holder.btnCancel.text = "Cancelled"
                holder.btnConfirm.alpha = 0.5f
                holder.btnComplete.alpha = 0.5f
                holder.btnCancel.alpha = 0.5f
            }
        }
    }

    override fun getItemCount(): Int = reservationList.size

    class ReservationItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateText: TextView = itemView.findViewById(R.id.tv_date)
        val totalText: TextView = itemView.findViewById(R.id.tv_total)
        val medicineCount: TextView = itemView.findViewById(R.id.tv_medicine_count)
        val medicinesText: TextView = itemView.findViewById(R.id.tv_medicines)
        val btnConfirm: Button = itemView.findViewById(R.id.btn_confirm)
        val btnComplete: Button = itemView.findViewById(R.id.btn_complete)
        val btnCancel: Button = itemView.findViewById(R.id.btn_cancel)
    }
}