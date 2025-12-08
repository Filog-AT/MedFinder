package com.example.medfinder

import Medicine
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var lowStockRecyclerView: RecyclerView
    private lateinit var reservationsRecyclerView: RecyclerView
    private lateinit var toBeClaimedRecyclerView: RecyclerView
    private lateinit var lowStockAdapter: MedicineAdapter
    private lateinit var reservationAdapter: ReservationItemAdapter
    private lateinit var toBeClaimedAdapter: ToBeClaimedAdapter
    private val medicines = mutableListOf<Medicine>()
    private val reservations = mutableListOf<Reservation>()
    private val toBeClaimedReservations = mutableListOf<Reservation>()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var tvNoLowStock: TextView
    private lateinit var tvNoReservations: TextView
    private lateinit var tvNoToBeClaimed: TextView
    private lateinit var tvLowStockCount: TextView
    private lateinit var tvReservationCount: TextView
    private lateinit var tvToBeClaimedCount: TextView
    private lateinit var btnViewAllReservations: Button
    private lateinit var toBeClaimedSection: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize views with null safety
        try {
            lowStockRecyclerView = view.findViewById(R.id.low_stock_list)
            reservationsRecyclerView = view.findViewById(R.id.reservations_list)
            toBeClaimedRecyclerView = view.findViewById(R.id.to_be_claimed_list) ?: throw NullPointerException("toBeClaimedRecyclerView not found")
            tvNoLowStock = view.findViewById(R.id.tv_no_low_stock)
            tvNoReservations = view.findViewById(R.id.tv_no_reservations)
            tvNoToBeClaimed = view.findViewById(R.id.tv_no_to_be_claimed) ?: throw NullPointerException("tvNoToBeClaimed not found")
            tvLowStockCount = view.findViewById(R.id.tv_low_stock_count)
            tvReservationCount = view.findViewById(R.id.tv_reservation_count)
            tvToBeClaimedCount = view.findViewById(R.id.tv_to_be_claimed_count) ?: throw NullPointerException("tvToBeClaimedCount not found")
            btnViewAllReservations = view.findViewById(R.id.btn_view_all_reservations)
            toBeClaimedSection = view.findViewById(R.id.to_be_claimed_section) ?: throw NullPointerException("toBeClaimedSection not found")

            Log.d("HomeFragment", "✓ All views initialized")
        } catch (e: Exception) {
            Log.e("HomeFragment", "✗ Error initializing views: ${e.message}")
            e.printStackTrace()
            // Show a toast so we can see the error
            Toast.makeText(requireContext(), "View error: ${e.message}", Toast.LENGTH_LONG).show()
        }

        // Setup layouts
        lowStockRecyclerView.layoutManager = LinearLayoutManager(context)
        reservationsRecyclerView.layoutManager = LinearLayoutManager(context)
        toBeClaimedRecyclerView.layoutManager = LinearLayoutManager(context)

        // Get pharmacy ID
        val sharedPref = requireContext().getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
        val pharmacyId = sharedPref.getString("pharmacy_id", "") ?: ""

        // Setup adapters
        lowStockAdapter = MedicineAdapter(medicines, pharmacyId, showActions = false)
        lowStockRecyclerView.adapter = lowStockAdapter

        reservationAdapter = ReservationItemAdapter(reservations) { reservationId, newStatus, timeLimitMinutes ->
            updateReservationStatus(reservationId, newStatus, timeLimitMinutes)
        }
        reservationsRecyclerView.adapter = reservationAdapter

        // Setup to-be-claimed adapter
        toBeClaimedAdapter = ToBeClaimedAdapter(toBeClaimedReservations,
            onMarkReceived = { reservationId ->
                markReservationAsReceived(reservationId)
            }
        )
        toBeClaimedRecyclerView.adapter = toBeClaimedAdapter

        // Setup button click
        btnViewAllReservations.setOnClickListener {
            Toast.makeText(requireContext(), "View All Reservations", Toast.LENGTH_SHORT).show()
        }

        // Load data
        loadLowStockMedicines()
        loadPendingReservations()
        loadToBeClaimedReservations()

        return view
    }

    private fun loadToBeClaimedReservations() {
        Log.d("HomeFragment", "loadToBeClaimedReservations() called")

        // First, show that we're trying to load
        activity?.runOnUiThread {
            toBeClaimedSection.visibility = View.VISIBLE  // Always show while loading
            tvNoToBeClaimed.text = "Loading..."
            tvNoToBeClaimed.visibility = View.VISIBLE
        }

        val sharedPref = requireContext().getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
        val pharmacyId = sharedPref.getString("pharmacy_id", null)

        Log.d("HomeFragment", "Pharmacy ID: $pharmacyId")

        if (pharmacyId.isNullOrEmpty()) {
            Log.e("HomeFragment", "No pharmacy ID found")
            activity?.runOnUiThread {
                tvNoToBeClaimed.text = "Please login as a pharmacy"
                toBeClaimedSection.visibility = View.VISIBLE
            }
            return
        }

        // SIMPLIFIED: Just get all reservations and filter locally
        db.collection("Reservations")
            .whereEqualTo("pharmacy_id", pharmacyId)
            .get()
            .addOnSuccessListener { documents ->
                Log.d("HomeFragment", "Got ${documents.size()} total reservations")

                toBeClaimedReservations.clear()

                for (document in documents) {
                    val reservation = document.toObject(Reservation::class.java)
                    reservation.id = document.id

                    Log.d("HomeFragment", "Checking reservation: ${reservation.id}, Status: ${reservation.status}")

                    // Filter locally for confirmed reservations
                    if (reservation.status == "confirmed" || reservation.status == "time_limit_set") {
                        Log.d("HomeFragment", "✓ Adding to to-be-claimed list")
                        toBeClaimedReservations.add(reservation)
                    }
                }

                // Sort by date (newest first)
                toBeClaimedReservations.sortByDescending { it.created_at }

                // Limit to 5
                if (toBeClaimedReservations.size > 5) {
                    toBeClaimedReservations.subList(5, toBeClaimedReservations.size).clear()
                }

                Log.d("HomeFragment", "Filtered to ${toBeClaimedReservations.size} to-be-claimed reservations")

                // Update UI on main thread
                activity?.runOnUiThread {
                    toBeClaimedAdapter.notifyDataSetChanged()
                    tvToBeClaimedCount.text = "(${toBeClaimedReservations.size})"

                    // ALWAYS show the section
                    toBeClaimedSection.visibility = View.VISIBLE

                    if (toBeClaimedReservations.isEmpty()) {
                        tvNoToBeClaimed.text = "No reservations to be claimed"
                        tvNoToBeClaimed.visibility = View.VISIBLE
                    } else {
                        tvNoToBeClaimed.visibility = View.GONE
                    }

                    Log.d("HomeFragment", "UI updated. Section visible: ${toBeClaimedSection.visibility}, Items: ${toBeClaimedReservations.size}")
                }
            }
            .addOnFailureListener { e ->
                Log.e("HomeFragment", "Error loading reservations: ${e.message}")
                activity?.runOnUiThread {
                    tvNoToBeClaimed.text = "Error loading: ${e.message}"
                    toBeClaimedSection.visibility = View.VISIBLE
                }
            }
    }

    private fun markReservationAsReceived(reservationId: String) {
        Log.d("HomeFragment", "Marking reservation $reservationId as received")

        db.collection("Reservations")
            .document(reservationId)
            .update("status", "received")
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Reservation marked as received", Toast.LENGTH_SHORT).show()

                // Refresh both lists
                loadPendingReservations()
                loadToBeClaimedReservations()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(),
                    "Failed to mark as received: ${e.message}",
                    Toast.LENGTH_SHORT).show()
                Log.e("HomeFragment", "Error marking as received", e)
            }
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

    // CHANGE THIS FROM PRIVATE TO PUBLIC
    fun loadPendingReservations() {
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
            .limit(5)
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

    private fun updateReservationStatus(reservationId: String, newStatus: String, timeLimitMinutes: Int? = null) {
        val updates = hashMapOf<String, Any>(
            "status" to newStatus,
            "updated_at" to System.currentTimeMillis()
        )

        if (timeLimitMinutes != null) {
            updates["time_limit_minutes"] = timeLimitMinutes
            updates["time_limit_set_at"] = System.currentTimeMillis()

            if (newStatus == "time_limit_set") {
                updates["confirmed_at"] = System.currentTimeMillis()
            }
        }

        if (newStatus == "confirmed") {
            updates["confirmed_at"] = System.currentTimeMillis()
        }

        db.collection("Reservations")
            .document(reservationId)
            .update(updates)
            .addOnSuccessListener {
                val message = when {
                    timeLimitMinutes != null && timeLimitMinutes > 0 ->
                        "Time limit set: $timeLimitMinutes minutes"
                    newStatus == "confirmed" -> "Reservation confirmed!"
                    else -> "Reservation $newStatus"
                }

                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

                // Refresh BOTH lists
                loadPendingReservations()
                loadToBeClaimedReservations()  // NEW: Refresh to-be-claimed list too
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(),
                    "Failed to update: ${e.message}",
                    Toast.LENGTH_SHORT).show()
                Log.e("HomeFragment", "Error updating reservation", e)
            }
    }
}