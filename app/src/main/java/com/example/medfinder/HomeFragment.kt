package com.example.medfinder

import Medicine
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {

    private lateinit var lowStockRecyclerView: RecyclerView
    private lateinit var reservationsRecyclerView: RecyclerView
    private lateinit var lowStockAdapter: MedicineAdapter
    private lateinit var reservationAdapter: ReservationItemAdapter
    private val medicines = mutableListOf<Medicine>()
    private val reservations = mutableListOf<Reservation>()
    private val db = FirebaseFirestore.getInstance()

    // UI Elements
    private lateinit var tvNoLowStock: TextView
    private lateinit var tvNoReservations: TextView
    private lateinit var tvLowStockCount: TextView
    private lateinit var tvReservationCount: TextView
    private lateinit var btnViewAllReservations: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize views
        lowStockRecyclerView = view.findViewById(R.id.low_stock_list)
        reservationsRecyclerView = view.findViewById(R.id.reservations_list)
        tvNoLowStock = view.findViewById(R.id.tv_no_low_stock)
        tvNoReservations = view.findViewById(R.id.tv_no_reservations)
        tvLowStockCount = view.findViewById(R.id.tv_low_stock_count)
        tvReservationCount = view.findViewById(R.id.tv_reservation_count)
        btnViewAllReservations = view.findViewById(R.id.btn_view_all_reservations)

        // Setup layouts
        lowStockRecyclerView.layoutManager = LinearLayoutManager(context)
        reservationsRecyclerView.layoutManager = LinearLayoutManager(context)

        // Get pharmacy ID
        val sharedPref = requireContext().getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
        val pharmacyId = sharedPref.getString("pharmacy_id", "") ?: ""

        // Setup adapters
        lowStockAdapter = MedicineAdapter(medicines, pharmacyId, showActions = false)
        lowStockRecyclerView.adapter = lowStockAdapter

        reservationAdapter = ReservationItemAdapter(reservations) { reservationId, newStatus ->
            updateReservationStatus(reservationId, newStatus)
        }
        reservationsRecyclerView.adapter = reservationAdapter

        // Setup button click
        btnViewAllReservations.setOnClickListener {
            // Navigate to full reservations list (you can create this later)
            Toast.makeText(requireContext(), "View All Reservations", Toast.LENGTH_SHORT).show()
        }

        // Load data
        loadLowStockMedicines()
        loadPendingReservations()

        return view
    }

    private fun loadLowStockMedicines() {
        val sharedPref = requireContext().getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
        val pharmacyId = sharedPref.getString("pharmacy_id", null)

        if (pharmacyId == null) {
            Log.e("HomeFragment", "No pharmacy ID found in session")
            return
        }

        db.collection("Pharmacies")
            .document(pharmacyId)
            .collection("Medicines")
            .get()
            .addOnSuccessListener { result ->
                medicines.clear()
                for (document in result) {
                    val stock = document.getLong("stock") ?: 0
                    if (stock < 10) {
                        val medicine = Medicine(
                            id = document.id,
                            brand_name = document.getString("brand_name") ?: "",
                            medicine_name = document.getString("medicine_name") ?: "",
                            pharmacy_id = pharmacyId,
                            price = (document.getLong("price") ?: 0L).toInt(),
                            stock = stock.toInt()
                        )
                        medicines.add(medicine)
                    }
                }
                lowStockAdapter.notifyDataSetChanged()

                // Update UI
                tvLowStockCount.text = "(${medicines.size})"
                tvNoLowStock.visibility = if (medicines.isEmpty()) View.VISIBLE else View.GONE

                Log.d("HomeFragment", "Loaded ${medicines.size} low stock medicines")
            }
            .addOnFailureListener { e ->
                Log.e("HomeFragment", "Error loading low stock medicines", e)
            }
    }

    private fun loadPendingReservations() {
        val sharedPref = requireContext().getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
        val pharmacyId = sharedPref.getString("pharmacy_id", null)

        if (pharmacyId == null) {
            Log.e("HomeFragment", "No pharmacy ID found in session")
            return
        }

        db.collection("Reservations")
            .whereEqualTo("pharmacy_id", pharmacyId)
            .whereEqualTo("status", "pending")
            .orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(5) // Show only 5 most recent pending reservations
            .get()
            .addOnSuccessListener { documents ->
                reservations.clear()
                for (document in documents) {
                    val reservation = document.toObject(Reservation::class.java)
                    reservation.id = document.id
                    reservations.add(reservation)
                }
                reservationAdapter.notifyDataSetChanged()

                // Update UI
                tvReservationCount.text = "(${reservations.size})"
                tvNoReservations.visibility = if (reservations.isEmpty()) View.VISIBLE else View.GONE

                Log.d("HomeFragment", "Loaded ${reservations.size} pending reservations")
            }
            .addOnFailureListener { e ->
                Log.e("HomeFragment", "Error loading reservations", e)
                // If index error, try without orderBy
                if (e.message?.contains("index") == true) {
                    loadReservationsWithoutOrder(pharmacyId)
                }
            }
    }

    private fun loadReservationsWithoutOrder(pharmacyId: String) {
        db.collection("Reservations")
            .whereEqualTo("pharmacy_id", pharmacyId)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { documents ->
                reservations.clear()
                for (document in documents) {
                    val reservation = document.toObject(Reservation::class.java)
                    reservation.id = document.id
                    reservations.add(reservation)
                }
                // Sort manually by date (newest first)
                reservations.sortByDescending { it.created_at }
                // Keep only 5
                if (reservations.size > 5) {
                    reservations.subList(5, reservations.size).clear()
                }

                reservationAdapter.notifyDataSetChanged()
                tvReservationCount.text = "(${reservations.size})"
                tvNoReservations.visibility = if (reservations.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun updateReservationStatus(reservationId: String, newStatus: String) {
        val updates = hashMapOf<String, Any>(
            "status" to newStatus,
            "updated_at" to System.currentTimeMillis()
        )

        db.collection("Reservations")
            .document(reservationId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Reservation $newStatus", Toast.LENGTH_SHORT).show()

                // Remove from list if not pending anymore
                if (newStatus != "pending") {
                    val index = reservations.indexOfFirst { it.id == reservationId }
                    if (index != -1) {
                        reservations.removeAt(index)
                        reservationAdapter.notifyItemRemoved(index)
                        tvReservationCount.text = "(${reservations.size})"
                        tvNoReservations.visibility = if (reservations.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("HomeFragment", "Error updating reservation", e)
            }
    }
}