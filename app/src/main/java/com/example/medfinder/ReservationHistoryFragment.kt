// ReservationHistoryFragment.kt
package com.example.medfinder

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class ReservationHistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var adapter: ReservationAdapter
    private val reservationList = mutableListOf<Reservation>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reservation_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.reservation_recycler_view)
        emptyText = view.findViewById(R.id.tv_empty)

        adapter = ReservationAdapter(
            reservationList = reservationList,
            onCancelClickListener = { reservation ->
                showCancelConfirmationDialog(reservation)
            },
            onTrackClickListener = { reservation ->
                showTrackOrderDialog(reservation)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        loadReservations()
    }

    private fun loadReservations() {
        val userId = LoginUtils.getCurrentUserId(requireContext())
        if (userId == null) {
            Toast.makeText(requireContext(), "Please login to view reservations", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("Reservations")
            .whereEqualTo("user_id", userId)
            .orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                reservationList.clear()
                for (document in documents) {
                    val reservation = document.toObject(Reservation::class.java)
                    reservation.id = document.id
                    reservationList.add(reservation)
                }
                adapter.notifyDataSetChanged()

                // Show/hide empty state
                if (reservationList.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error loading reservations", Toast.LENGTH_SHORT).show()
                Log.e("ReservationHistory", "Error loading reservations", e)
            }
    }

    private fun showCancelConfirmationDialog(reservation: Reservation) {
        AlertDialog.Builder(requireContext())
            .setTitle("Cancel Order")
            .setMessage("Are you sure you want to cancel this order? This action cannot be undone.")
            .setPositiveButton("Yes, Cancel") { dialog, which ->
                cancelOrder(reservation)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun cancelOrder(reservation: Reservation) {
        if (reservation.id == null) return

        // Show loading
        Toast.makeText(requireContext(), "Cancelling order...", Toast.LENGTH_SHORT).show()

        db.collection("Reservations")
            .document(reservation.id!!)
            .update("status", "cancelled")
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Order cancelled successfully", Toast.LENGTH_SHORT).show()
                // Refresh the list
                loadReservations()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to cancel order", Toast.LENGTH_SHORT).show()
                Log.e("ReservationHistory", "Error cancelling order", e)
            }
    }

    private fun showTrackOrderDialog(reservation: Reservation) {
        val message = when (reservation.status.lowercase()) {
            "pending" -> "Your order is pending confirmation from the pharmacy."
            "confirmed" -> "Your order has been confirmed. Please proceed to the pharmacy to pick up your medicines."
            "completed" -> "Your order has been completed. Thank you for your purchase!"
            else -> "Tracking information not available."
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Order Status")
            .setMessage("Status: ${reservation.status.uppercase()}\n\n$message")
            .setPositiveButton("OK", null)
            .show()
    }
}