// CustomerReservationDetailFragment.kt
package com.example.medfinder

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class CustomerReservationDetailFragment : Fragment() {

    private lateinit var reservation: Reservation
    private lateinit var tvTimer: TextView
    private lateinit var tvStatus: TextView
    private var countDownTimer: CountDownTimer? = null
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_customer_reservation_detail, container, false)

        tvTimer = view.findViewById(R.id.tv_timer)
        tvStatus = view.findViewById(R.id.tv_status)

        reservation = arguments?.getSerializable("reservation") as Reservation

        setupViews(view)
        updateStatusUI()  // Make sure to call this
        startTimer()      // And this

        return view
    }

    private fun setupViews(view: View) {
        view.findViewById<TextView>(R.id.tv_reservation_id).text = "Reservation #${reservation.id?.take(8)}"
        view.findViewById<TextView>(R.id.tv_total_price).text = "Total: ₱${reservation.total_price}"
        view.findViewById<TextView>(R.id.tv_pharmacy).text = "Pharmacy ID: ${reservation.pharmacy_id}"

        Log.d("CustomerReservation", "Status: ${reservation.status}")
        Log.d("CustomerReservation", "Time limit minutes: ${reservation.time_limit_minutes}")
        Log.d("CustomerReservation", "Time limit set at: ${reservation.time_limit_set_at}")

        val medicinesText = reservation.medicines.joinToString("\n") { medicine ->
            "${medicine.medicine_name} x${medicine.quantity} - ₱${medicine.price * medicine.quantity}" +
                    if (medicine.requires_prescription) " (Requires prescription)" else ""
        }
        view.findViewById<TextView>(R.id.tv_medicines).text = medicinesText

        updateStatusUI()
    }

    private fun updateStatusUI() {
        when (reservation.status.lowercase()) {  // Use lowercase for consistency
            "pending" -> {
                tvStatus.text = "⏳ Waiting for pharmacy confirmation"
                tvStatus.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
            }
            "time_limit_set" -> {
                // FIX: Show time limit information
                val timeLimit = reservation.time_limit_minutes
                if (timeLimit > 0) {
                    tvStatus.text = "⏰ Pharmacy set a ${timeLimit}-minute time limit. Claim within this time!"
                } else {
                    tvStatus.text = "✅ Reservation confirmed by pharmacy"
                }
                tvStatus.setTextColor(requireContext().getColor(android.R.color.holo_blue_dark))
            }
            "confirmed" -> {
                tvStatus.text = "✅ Reservation confirmed by pharmacy"
                tvStatus.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
            }
            "cancelled" -> {
                tvStatus.text = "❌ Reservation cancelled"
                tvStatus.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
            }
            else -> {
                // Fallback for any other status
                tvStatus.text = "Status: ${reservation.status}"
                tvStatus.setTextColor(requireContext().getColor(android.R.color.darker_gray))
            }
        }
    }

    private fun startTimer() {
        // Start timer for both "time_limit_set" and "confirmed" statuses with time limit
        if ((reservation.status == "time_limit_set" || reservation.status == "confirmed") &&
            reservation.time_limit_minutes > 0) {

            val timeLimitMillis = reservation.time_limit_minutes * 60 * 1000L
            val timeLimitSetAt = reservation.time_limit_set_at

            // If time_limit_set_at is 0, use created_at as fallback
            val startTime = if (timeLimitSetAt > 0) timeLimitSetAt else reservation.created_at
            val now = System.currentTimeMillis()
            val elapsed = now - startTime
            val remaining = timeLimitMillis - elapsed

            if (remaining <= 0) {
                tvTimer.text = "⏰ Time expired!"
                tvTimer.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
                return
            }

            countDownTimer = object : CountDownTimer(remaining, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60

                    tvTimer.text = String.format("⏰ Time left: %02d:%02d:%02d", hours, minutes, seconds)

                    // Change color as time runs low
                    when {
                        millisUntilFinished < 5 * 60 * 1000 -> // Less than 5 minutes
                            tvTimer.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
                        millisUntilFinished < 15 * 60 * 1000 -> // Less than 15 minutes
                            tvTimer.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
                        else ->
                            tvTimer.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
                    }
                }

                override fun onFinish() {
                    tvTimer.text = "⏰ Time expired!"
                    tvTimer.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
                    Toast.makeText(requireContext(), "Time limit expired! Please contact pharmacy.", Toast.LENGTH_LONG).show()

                    // Auto-cancel if time expires
                    autoCancelReservation()
                }
            }.start()
        } else {
            // No timer needed
            tvTimer.visibility = View.GONE
        }
    }

    private fun autoCancelReservation() {
        db.collection("Reservations")
            .document(reservation.id!!)
            .update("status", "cancelled")
            .addOnSuccessListener {
                tvStatus.text = "❌ Reservation auto-cancelled (time expired)"
                tvStatus.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}