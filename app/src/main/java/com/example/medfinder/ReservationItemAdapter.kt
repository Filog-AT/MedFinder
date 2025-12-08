package com.example.medfinder

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class ReservationItemAdapter(
    private val reservationList: List<Reservation>,
    private val onStatusUpdate: (reservationId: String, newStatus: String, timeLimitMinutes: Int?) -> Unit
) : RecyclerView.Adapter<ReservationItemAdapter.ReservationItemViewHolder>() {

    private var expandedPosition = -1  // Track which item is expanded

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservationItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pharmacy_reservation, parent, false)
        return ReservationItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReservationItemViewHolder, position: Int) {
        val reservation = reservationList[position]
        val isExpanded = position == expandedPosition

        // Format date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
        val date = Date(reservation.created_at)

        holder.dateText.text = dateFormat.format(date)
        holder.totalText.text = "₱${reservation.total_price}"

        // FIX: Show total items, not number of medicine types
        val totalItems = reservation.medicines.sumOf { it.quantity }
        holder.medicineCount.text = "$totalItems item(s)"

        holder.dateText.text = dateFormat.format(date)
        holder.totalText.text = "₱${reservation.total_price}"
        holder.medicineCount.text = "${reservation.medicines.size} medicine(s)"

        // Show medicines summary (preview)
        val medicinesSummary = if (reservation.medicines.size <= 2) {
            reservation.medicines.joinToString(", ") {
                "${it.medicine_name} (x${it.quantity})"
            }
        } else {
            val firstTwo = reservation.medicines.take(2).joinToString(", ") {
                "${it.medicine_name} (x${it.quantity})"
            }
            val remainingCount = reservation.medicines.size - 2
            val totalItems = reservation.medicines.sumOf { it.quantity }
            "$firstTwo + $remainingCount more ($totalItems items total)"
        }

        holder.medicinesText.text = medicinesSummary

        // Show detailed medicines list in expanded view
        val medicinesDetails = reservation.medicines.joinToString("\n") { medicine ->
            "• ${medicine.medicine_name} x${medicine.quantity} - ₱${medicine.price * medicine.quantity}" +
                    if (medicine.requires_prescription) " (Prescription)" else ""
        }
        holder.medicinesDetailsText.text = medicinesDetails

        // Show prescription warning in expanded view
        val hasPrescriptionMeds = reservation.medicines.any { it.requires_prescription }
        Log.d("ReservationItemAdapter", "Reservation ${reservation.id} has prescription meds: $hasPrescriptionMeds")
        holder.prescriptionWarning.visibility = if (hasPrescriptionMeds) View.VISIBLE else View.GONE

// Also log the medicines to debug:
        reservation.medicines.forEach { medicine ->
            Log.d("ReservationItemAdapter", "Medicine: ${medicine.medicine_name}, Requires prescription: ${medicine.requires_prescription}")
        }
        holder.prescriptionWarning.visibility = if (hasPrescriptionMeds) View.VISIBLE else View.GONE

        // Show time limit if set
        if (reservation.time_limit_minutes > 0) {
            holder.timeLimitText.visibility = View.VISIBLE
            holder.timeLimitText.text = "⏰ ${reservation.time_limit_minutes} min limit"
        } else {
            holder.timeLimitText.visibility = View.GONE
        }

        // Setup expanded/collapsed state
        holder.expandedView.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.expandIndicator.setImageResource(
            if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
        )

        // Setup button listeners
        holder.btnConfirm.setOnClickListener {
            if (isExpanded) {
                // Confirm without time limit
                onStatusUpdate(reservation.id!!, "confirmed", null)
            } else {
                // Expand to show details
                expandItem(position)
            }
        }

        holder.btnDetails.setOnClickListener {
            // Toggle expand/collapse
            if (isExpanded) {
                collapseItem()
            } else {
                expandItem(position)
            }
        }

        holder.btnCancel.setOnClickListener {
            onStatusUpdate(reservation.id!!, "cancelled", null)
        }

        // Time limit button listeners
        holder.btn15Min.setOnClickListener {
            setSelectedTimeLimit(holder, 15)
            holder.btnConfirmWithTime.visibility = View.VISIBLE
        }

        holder.btn30Min.setOnClickListener {
            setSelectedTimeLimit(holder, 30)
            holder.btnConfirmWithTime.visibility = View.VISIBLE
        }

        holder.btn60Min.setOnClickListener {
            setSelectedTimeLimit(holder, 60)
            holder.btnConfirmWithTime.visibility = View.VISIBLE
        }

        holder.btnNoLimit.setOnClickListener {
            setSelectedTimeLimit(holder, 0)
            holder.btnConfirmWithTime.visibility = View.VISIBLE
        }

        holder.btnConfirmWithTime.setOnClickListener {
            val timeLimit = getSelectedTimeLimit(holder)
            onStatusUpdate(reservation.id!!, if (timeLimit > 0) "time_limit_set" else "confirmed", timeLimit)
        }

        // Expand indicator click listener
        holder.expandIndicator.setOnClickListener {
            if (isExpanded) {
                collapseItem()
            } else {
                expandItem(position)
            }
        }

        // Disable buttons based on current status
        updateButtonStates(holder, reservation.status)
    }

    private fun setSelectedTimeLimit(holder: ReservationItemViewHolder, minutes: Int) {
        // Reset all buttons
        val timeButtons = listOf(holder.btn15Min, holder.btn30Min, holder.btn60Min, holder.btnNoLimit)
        timeButtons.forEach { button ->
            button.alpha = 0.7f
        }

        // Highlight selected button
        when (minutes) {
            15 -> holder.btn15Min.alpha = 1f
            30 -> holder.btn30Min.alpha = 1f
            60 -> holder.btn60Min.alpha = 1f
            0 -> holder.btnNoLimit.alpha = 1f
        }

        holder.selectedTimeLimit = minutes
    }

    private fun getSelectedTimeLimit(holder: ReservationItemViewHolder): Int {
        return holder.selectedTimeLimit
    }

    private fun expandItem(position: Int) {
        val previousExpanded = expandedPosition
        expandedPosition = position
        notifyItemChanged(previousExpanded)
        notifyItemChanged(position)
    }

    private fun collapseItem() {
        val previousExpanded = expandedPosition
        expandedPosition = -1
        notifyItemChanged(previousExpanded)
    }

    private fun updateButtonStates(holder: ReservationItemViewHolder, status: String) {
        when (status.lowercase()) {
            "time_limit_set" -> {
                holder.btnConfirm.text = "Time Limit Set"
                holder.btnConfirm.isEnabled = true
                holder.btnConfirm.alpha = 1f
                holder.btnCancel.isEnabled = true
                holder.timeLimitButtons.visibility = View.GONE
                holder.btnConfirmWithTime.visibility = View.GONE
            }
            "confirmed" -> {
                holder.btnConfirm.text = "Confirmed"
                holder.btnConfirm.isEnabled = false
                holder.btnConfirm.alpha = 0.5f
                holder.btnCancel.isEnabled = false
                holder.btnCancel.alpha = 0.5f
                holder.btnDetails.isEnabled = false
                holder.btnDetails.alpha = 0.5f
                holder.timeLimitButtons.visibility = View.GONE
                holder.btnConfirmWithTime.visibility = View.GONE
            }
            "cancelled" -> {
                holder.btnCancel.text = "Cancelled"
                holder.btnConfirm.isEnabled = false
                holder.btnConfirm.alpha = 0.5f
                holder.btnCancel.isEnabled = false
                holder.btnCancel.alpha = 0.5f
                holder.btnDetails.isEnabled = false
                holder.btnDetails.alpha = 0.5f
                holder.timeLimitButtons.visibility = View.GONE
                holder.btnConfirmWithTime.visibility = View.GONE
            }
            else -> {
                // pending status - enable all
                holder.btnConfirm.text = "Confirm"
                holder.btnConfirm.isEnabled = true
                holder.btnDetails.isEnabled = true
                holder.btnCancel.isEnabled = true
            }
        }
    }

    override fun getItemCount(): Int = reservationList.size

    class ReservationItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Collapsed view
        val dateText: TextView = itemView.findViewById(R.id.tv_date)
        val totalText: TextView = itemView.findViewById(R.id.tv_total)
        val medicineCount: TextView = itemView.findViewById(R.id.tv_medicine_count)
        val medicinesText: TextView = itemView.findViewById(R.id.tv_medicines)
        val timeLimitText: TextView = itemView.findViewById(R.id.tv_time_limit)
        val btnConfirm: Button = itemView.findViewById(R.id.btn_confirm)
        val btnDetails: Button = itemView.findViewById(R.id.btn_details)
        val btnCancel: Button = itemView.findViewById(R.id.btn_cancel)

        // Expanded view
        val expandedView: LinearLayout = itemView.findViewById(R.id.expanded_view)
        val expandIndicator: ImageView = itemView.findViewById(R.id.iv_expand_indicator)
        val medicinesDetailsText: TextView = itemView.findViewById(R.id.tv_medicines_details)
        val prescriptionWarning: TextView = itemView.findViewById(R.id.tv_prescription_warning)

        // Time limit buttons
        val timeLimitButtons: LinearLayout = itemView.findViewById(R.id.time_limit_buttons)
        val btn15Min: Button = itemView.findViewById(R.id.btn_15min)
        val btn30Min: Button = itemView.findViewById(R.id.btn_30min)
        val btn60Min: Button = itemView.findViewById(R.id.btn_60min)
        val btnNoLimit: Button = itemView.findViewById(R.id.btn_no_limit)
        val btnConfirmWithTime: Button = itemView.findViewById(R.id.btn_confirm_with_time)

        var selectedTimeLimit: Int = 0
    }
}