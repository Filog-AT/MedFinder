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

class ToBeClaimedAdapter(
    private val reservationList: List<Reservation>,
    private val onMarkReceived: (String) -> Unit
) : RecyclerView.Adapter<ToBeClaimedAdapter.ToBeClaimedViewHolder>() {

    private var expandedPosition = -1  // Track which item is expanded

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToBeClaimedViewHolder {
        Log.d("ToBeClaimedAdapter", "Creating view holder")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_to_be_claimed, parent, false)
        return ToBeClaimedViewHolder(view)
    }

    override fun onBindViewHolder(holder: ToBeClaimedViewHolder, position: Int) {
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

        // Show time limit if set
        if (reservation.time_limit_minutes > 0) {
            holder.timeLimitText.visibility = View.VISIBLE
            holder.timeLimitText.text = "⏰ ${reservation.time_limit_minutes} min limit"
        } else {
            holder.timeLimitText.visibility = View.GONE
        }

        // Show detailed medicines list in expanded view
        val medicinesDetails = reservation.medicines.joinToString("\n") { medicine ->
            "• ${medicine.medicine_name} x${medicine.quantity} - ₱${medicine.price * medicine.quantity}" +
                    if (medicine.requires_prescription) " (Prescription)" else ""
        }
        holder.medicinesDetailsText.text = medicinesDetails

        // Show prescription warning in expanded view
        val hasPrescriptionMeds = reservation.medicines.any { it.requires_prescription }
        Log.d("ToBeClaimedAdapter", "Reservation ${reservation.id} has prescription meds: $hasPrescriptionMeds")
        holder.prescriptionWarning.visibility = if (hasPrescriptionMeds) View.VISIBLE else View.GONE

// Debug log:
        reservation.medicines.forEach { medicine ->
            Log.d("ToBeClaimedAdapter", "Medicine: ${medicine.medicine_name}, Requires prescription: ${medicine.requires_prescription}")
        }
        holder.prescriptionWarning.visibility = if (hasPrescriptionMeds) View.VISIBLE else View.GONE

        // Setup expanded/collapsed state
        holder.expandedView.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.expandIndicator.setImageResource(
            if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
        )

        // Update button text
        holder.btnToggleDetails.text = if (isExpanded) "Hide Details" else "Show Details"

        // Setup button listeners
        holder.btnToggleDetails.setOnClickListener {
            if (isExpanded) {
                collapseItem()
            } else {
                expandItem(position)
            }
        }

        holder.btnReceived.setOnClickListener {
            Log.d("ToBeClaimedAdapter", "Received clicked for reservation ${reservation.id}")
            onMarkReceived(reservation.id!!)
        }

        // Expand indicator click listener
        holder.expandIndicator.setOnClickListener {
            if (isExpanded) {
                collapseItem()
            } else {
                expandItem(position)
            }
        }
    }

    private fun expandItem(position: Int) {
        val previousExpanded = expandedPosition
        expandedPosition = position
        if (previousExpanded != -1) {
            notifyItemChanged(previousExpanded)
        }
        notifyItemChanged(position)
    }

    private fun collapseItem() {
        val previousExpanded = expandedPosition
        expandedPosition = -1
        if (previousExpanded != -1) {
            notifyItemChanged(previousExpanded)
        }
    }

    override fun getItemCount(): Int {
        Log.d("ToBeClaimedAdapter", "Item count: ${reservationList.size}")
        return reservationList.size
    }

    class ToBeClaimedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Collapsed view
        val dateText: TextView = itemView.findViewById(R.id.tv_date)
        val totalText: TextView = itemView.findViewById(R.id.tv_total)
        val medicineCount: TextView = itemView.findViewById(R.id.tv_medicine_count)
        val medicinesText: TextView = itemView.findViewById(R.id.tv_medicines)
        val timeLimitText: TextView = itemView.findViewById(R.id.tv_time_limit)
        val btnToggleDetails: Button = itemView.findViewById(R.id.btn_toggle_details)
        val btnReceived: Button = itemView.findViewById(R.id.btn_received)

        // Expanded view
        val expandedView: LinearLayout = itemView.findViewById(R.id.expanded_view)
        val expandIndicator: ImageView = itemView.findViewById(R.id.iv_expand_indicator)
        val medicinesDetailsText: TextView = itemView.findViewById(R.id.tv_medicines_details)
        val prescriptionWarning: TextView = itemView.findViewById(R.id.tv_prescription_warning)
        val customerInfo: TextView = itemView.findViewById(R.id.tv_customer_info)

        init {
            Log.d("ToBeClaimedViewHolder", "ViewHolder created")
        }
    }
}