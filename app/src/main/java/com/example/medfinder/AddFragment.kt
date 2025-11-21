package com.example.medfinder

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore

class AddFragment : Fragment() {

    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add, container, false)

        db = FirebaseFirestore.getInstance()

        val medBrandName = view.findViewById<EditText>(R.id.brand_name)
        val medCategory = view.findViewById<EditText>(R.id.med_category)
        val medName = view.findViewById<EditText>(R.id.med_name)
        val medPrice = view.findViewById<EditText>(R.id.med_price)
        val medStock = view.findViewById<EditText>(R.id.med_stock)
        val saveButton = view.findViewById<Button>(R.id.btn_save)

        saveButton.setOnClickListener {
            val brand = medBrandName.text.toString().trim()
            val name = medName.text.toString().trim()
            val category = medCategory.text.toString().trim()
            val price = medPrice.text.toString().toIntOrNull() ?: 0
            val stock = medStock.text.toString().toIntOrNull() ?: 0

            if (name.isEmpty() || category.isEmpty()) {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val medicine = hashMapOf(
                "brand_name" to brand,
                "category" to category,
                "medicine_name" to name,
                "price" to price,
                "stock" to stock
            )

            db.collection("Medicines")
                .add(medicine)
                .addOnSuccessListener {
                    Toast.makeText(context, "Medicine added!", Toast.LENGTH_SHORT).show()
                    medName.text.clear()
                    medCategory.text.clear()
                    medPrice.text.clear()
                    medStock.text.clear()
                }
                .addOnFailureListener { e ->
                    Log.w("Firestore", "Error adding document", e)
                    Toast.makeText(context, "Error adding medicine", Toast.LENGTH_SHORT).show()
                }
        }

        return view
    }
}
