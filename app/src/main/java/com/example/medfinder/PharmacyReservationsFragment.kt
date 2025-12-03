// PharmacyReservationsFragment.kt
package com.example.medfinder

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class PharmacyReservationsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private val reservationList = mutableListOf<Reservation>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pharmacy_reservations, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.reservation_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        loadPharmacyReservations()
    }

    private fun loadPharmacyReservations() {
        val pharmacyId = LoginUtils.getCurrentUserId(requireContext())
        if (pharmacyId == null) {
            Log.e("PharmacyReservations", "Pharmacy not logged in")
            return
        }

        db.collection("Reservations")
            .whereEqualTo("pharmacy_id", pharmacyId)
            .whereEqualTo("status", "pending") // Only show pending reservations
            .orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                reservationList.clear()
                for (document in documents) {
                    val reservation = document.toObject(Reservation::class.java)
                    reservation.id = document.id
                    reservationList.add(reservation)
                }
                // Update adapter
            }
    }
}