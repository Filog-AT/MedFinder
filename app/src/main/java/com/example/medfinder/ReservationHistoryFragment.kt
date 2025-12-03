// ReservationHistoryFragment.kt
package com.example.medfinder

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

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

        adapter = ReservationAdapter(reservationList)
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
}

// Reservation data class
data class Reservation(
    var id: String? = null,
    var user_id: String = "",
    var pharmacy_id: String = "",
    var medicines: List<MedicineItem> = emptyList(),
    var status: String = "pending", // pending, confirmed, completed, cancelled
    var total_price: Int = 0,
    var created_at: Long = 0
)

data class MedicineItem(
    var medicine_id: String = "",
    var medicine_name: String = "",
    var quantity: Int = 0,
    var price: Int = 0
)