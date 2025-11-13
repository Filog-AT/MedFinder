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
        adapter = MedicineAdapter(medicines, showActions = false)
        recyclerView.adapter = adapter
        loadLowStockMedicines()
        return view
    }

    private fun loadLowStockMedicines() {
        db.collection("Medicines")
            .get()
            .addOnSuccessListener { result ->
                medicines.clear()
                for (document in result) {
                    val stock = document.getLong("stock") ?: 0
                    if (stock < 10) {
                        val medicine = Medicine(
                            brand_name = document.getString("brand_name") ?: "",
                            medicine_name = document.getString("medicine_name") ?: "",
                            category = document.getString("category") ?: "",
                            pharmacy_id = document.getString("pharmacy_id") ?: "",
                            price = (document.getLong("price") ?: 0L).toInt(),
                            stock = (document.getLong("stock") ?: 0L).toInt()
                        )
                        medicines.add(medicine)
                    }
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Log.w("Firestore", "Error getting documents", e)
            }
    }
}
