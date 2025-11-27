package com.example.medfinder

import Medicine
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MedicineAdapter
    private val medicines = mutableListOf<Medicine>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        recyclerView = view.findViewById(R.id.low_stock_list)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val sharedPref = requireContext().getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
        val pharmacyId = sharedPref.getString("pharmacy_id", "") ?: ""

        adapter = MedicineAdapter(medicines, pharmacyId, showActions = false)
        recyclerView.adapter = adapter

        loadLowStockMedicines()
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
                adapter.notifyDataSetChanged()
                Log.d("HomeFragment", "Loaded ${medicines.size} low stock medicines")
            }
            .addOnFailureListener { e ->
                Log.e("HomeFragment", "Error loading low stock medicines", e)
            }
    }
}