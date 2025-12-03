package com.example.medfinder

import Medicine
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class MedicineReservationFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var reserveButton: Button
    private lateinit var pharmacyNameTextView: TextView
    private lateinit var backButton: ImageButton
    private lateinit var medicineAdapter: ReservationMedicineAdapter
    private val medicineList = mutableListOf<Medicine>()
    private val db = FirebaseFirestore.getInstance()
    private var pharmacyId: String = ""

    companion object {
        private const val ARG_PHARMACY_ID = "pharmacy_id"

        fun newInstance(pharmacyId: String): MedicineReservationFragment {
            val fragment = MedicineReservationFragment()
            val args = Bundle()
            args.putString(ARG_PHARMACY_ID, pharmacyId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            pharmacyId = it.getString(ARG_PHARMACY_ID) ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_med_reserve, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        recyclerView = view.findViewById(R.id.reservation_recycler_view)
        reserveButton = view.findViewById(R.id.btn_reserve)
        pharmacyNameTextView = view.findViewById(R.id.tv_pharmacy_name)
        backButton = view.findViewById(R.id.btn_back)


        // Setup back button
        backButton.setOnClickListener {
            Log.d("MedicineReservation", "Back button clicked")
            parentFragmentManager.popBackStack()
        }

        setupRecyclerView()
        loadPharmacyInfo()
        loadMedicines()
        setupReserveButton()
    }

    private fun setupRecyclerView() {
        // Check if user is logged in AND is a customer
        val isLoggedIn = LoginUtils.isUserLoggedIn(requireContext())
        val isCustomer = LoginUtils.isCustomer(requireContext())
        val canReserve = isLoggedIn && isCustomer

        Log.d("MedicineReservation", "Setup recycler - Logged in: $isLoggedIn, Is customer: $isCustomer, Can reserve: $canReserve")

        medicineAdapter = ReservationMedicineAdapter(medicineList, canReserve)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = medicineAdapter
    }

    private fun setupReserveButton() {
        reserveButton.setOnClickListener {
            Log.d("MedicineReservation", "Reserve button clicked")

            // Check 0: Is user a guest?
            if (LoginUtils.isGuestUser(requireContext())) {
                Log.d("MedicineReservation", "❌ User is GUEST, cannot reserve")
                Toast.makeText(requireContext(),
                    "Guests cannot make reservations. Please create an account or login.",
                    Toast.LENGTH_LONG).show()
                LoginUtils.redirectToLogin(requireContext())
                return@setOnClickListener
            }

            // Check 1: Is user logged in?
            if (!LoginUtils.isUserLoggedIn(requireContext())) {
                Log.d("MedicineReservation", "❌ User not logged in")
                LoginUtils.redirectToLogin(requireContext(), "Please login to make a reservation")
                return@setOnClickListener
            }

            // Check 2: Is user a CUSTOMER? (not pharmacy)
            if (!LoginUtils.isCustomer(requireContext())) {
                Log.d("MedicineReservation", "❌ User is not a customer")
                Toast.makeText(requireContext(),
                    "Only customers can make reservations. Please login as a customer.",
                    Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Check 3: Has medicines selected?
            val selectedMedicines = medicineAdapter.getSelectedMedicines()
            if (selectedMedicines.isEmpty()) {
                Toast.makeText(requireContext(), "Please select at least one medicine", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Log.d("MedicineReservation", "✅ All checks passed, creating reservation...")
            createReservation(selectedMedicines)
        }
    }

    private fun createReservation(selectedMedicines: List<Pair<Medicine, Int>>) {
        // Get current user - already verified in setupReserveButton
        val userId = LoginUtils.getCurrentUserId(requireContext())

        if (userId == null) {
            LoginUtils.redirectToLogin(requireContext(), "Session expired, please login again")
            return
        }

        Log.d("MedicineReservation", "Creating reservation for user: $userId, pharmacy: $pharmacyId")

        val reservation = hashMapOf(
            "user_id" to userId,
            "pharmacy_id" to pharmacyId,
            "medicines" to selectedMedicines.map {
                hashMapOf(
                    "medicine_id" to it.first.id,
                    "medicine_name" to it.first.medicine_name,
                    "quantity" to it.second,
                    "price" to it.first.price
                )
            },
            "status" to "pending",
            "created_at" to System.currentTimeMillis(),
            "total_price" to selectedMedicines.sumOf { it.first.price * it.second }
        )

        db.collection("Reservations")
            .add(reservation)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Reservation created successfully!", Toast.LENGTH_SHORT).show()
                // Close fragment
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to create reservation: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadPharmacyInfo() {
        if (pharmacyId.isEmpty()) return

        db.collection("Pharmacies").document(pharmacyId)
            .get()
            .addOnSuccessListener { document ->
                val pharmacyName = document.getString("pharmacy_name") ?: "Pharmacy"
                pharmacyNameTextView.text = pharmacyName
            }
            .addOnFailureListener { e ->
                Log.e("MedicineReservation", "Error loading pharmacy info", e)
            }
    }

    private fun loadMedicines() {
        if (pharmacyId.isEmpty()) {
            Toast.makeText(requireContext(), "Pharmacy not found", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("Pharmacies").document(pharmacyId).collection("Medicines")
            .get()
            .addOnSuccessListener { documents ->
                medicineList.clear()
                for (document in documents) {
                    val medicine = document.toObject(Medicine::class.java)
                    medicine.id = document.id
                    medicineList.add(medicine)
                }
                medicineAdapter.notifyDataSetChanged()

                if (medicineList.isEmpty()) {
                    Toast.makeText(requireContext(), "No medicines available in this pharmacy", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to load medicines", Toast.LENGTH_SHORT).show()
            }
    }
}