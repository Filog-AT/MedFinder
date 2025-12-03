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
            // Go back to the map
            parentFragmentManager.popBackStack()
        }

        setupRecyclerView()
        loadPharmacyInfo()
        loadMedicines()
        setupReserveButton()
    }

    private fun setupRecyclerView() {
        medicineAdapter = ReservationMedicineAdapter(medicineList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = medicineAdapter
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

    private fun setupReserveButton() {
        reserveButton.setOnClickListener {
            val selectedMedicines = medicineAdapter.getSelectedMedicines()

            if (selectedMedicines.isEmpty()) {
                Toast.makeText(requireContext(), "Please select at least one medicine", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            createReservation(selectedMedicines)
        }
    }

    private fun createReservation(selectedMedicines: List<Pair<Medicine, Int>>) {
        // Get current user
        val sharedPref = requireContext().getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
        val userId = sharedPref.getString("user_id", null)

        if (userId == null) {
            Toast.makeText(requireContext(), "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

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
                Toast.makeText(requireContext(), "Failed to create reservation", Toast.LENGTH_SHORT).show()
            }
    }
}