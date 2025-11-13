package com.example.medfinder

import Medicine
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log

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
        adapter = MedicineAdapter(medicines, showActions = true)
        recyclerView.adapter = adapter
        loadData()
        return view
    }

    private fun loadData() {
        db.collection("Medicines")
            .get()
            .addOnSuccessListener { result ->
                medicines.clear()
                for (document in result) {
                    val med = document.toObject(Medicine::class.java)
                    medicines.add(med)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Log.w("Firestore", "Error getting documents", e)
            }
    }
}

