package com.example.medfinder

import Medicine
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class ListFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MedicineAdapter
    private val medicines = mutableListOf<Medicine>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_list, container, false)
        recyclerView = view.findViewById(R.id.med_list)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val sharedPref = requireContext().getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
        val pharmacyId = sharedPref.getString("pharmacy_id", "") ?: ""

        adapter = MedicineAdapter(medicines, pharmacyId, showActions = true)
        recyclerView.adapter = adapter

        loadPharmacyInventory()
        return view
    }

    private fun loadPharmacyInventory() {
        val sharedPref = requireContext().getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
        val pharmacyId = sharedPref.getString("pharmacy_id", null)

        if (pharmacyId == null) {
            Log.e("ListFragment", "No pharmacy ID found in session")
            return
        }

        Log.d("ListFragment", "Loading inventory for pharmacy: $pharmacyId")

        db.collection("Pharmacies")
            .document(pharmacyId)
            .collection("Medicines")
            .get()
            .addOnSuccessListener { result ->
                medicines.clear()
                for (document in result) {
                    try {
                        val brandName = document.getString("brand_name") ?: ""
                        val medicineName = document.getString("medicine_name") ?: "Unknown Medicine"
                        val category = document.getString("category") ?: ""
                        val price = document.getLong("price")?.toInt() ?: 0
                        val stock = document.getLong("stock")?.toInt() ?: 0

                        val med = Medicine(
                            id = document.id,
                            brand_name = brandName,
                            medicine_name = medicineName,
                            category = category,
                            pharmacy_id = pharmacyId,
                            price = price,
                            stock = stock
                        )
                        medicines.add(med)
                        Log.d("ListFragment", "Loaded medicine: $medicineName, Stock: $stock, Price: $price")
                    } catch (e: Exception) {
                        Log.e("ListFragment", "Error parsing medicine document: ${e.message}")
                    }
                }
                adapter.notifyDataSetChanged()
                Log.d("ListFragment", "Loaded ${medicines.size} medicines")
            }
            .addOnFailureListener { e ->
                Log.e("ListFragment", "Error loading pharmacy inventory", e)
            }
    }

    override fun onResume() {
        super.onResume()
        loadPharmacyInventory()
    }
}