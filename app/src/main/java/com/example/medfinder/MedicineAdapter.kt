package com.example.medfinder

import Medicine
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class MedicineAdapter(
    private val medicineList: MutableList<Medicine>,
    private val pharmacyId: String,
    private val showActions: Boolean = true
) : RecyclerView.Adapter<MedicineAdapter.MedicineViewHolder>() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicineViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.medicine_item, parent, false)
        return MedicineViewHolder(view)
    }

    override fun onBindViewHolder(holder: MedicineViewHolder, position: Int) {
        val medicine = medicineList[position]
        holder.name.text = medicine.medicine_name
        holder.price.text = medicine.price.toString()
        holder.quantity.text = medicine.stock.toString()
        holder.brand.text = medicine.brand_name

        if (showActions) {
            holder.editBtn.visibility = View.VISIBLE
            holder.deleteBtn.visibility = View.VISIBLE
        } else {
            holder.editBtn.visibility = View.GONE
            holder.deleteBtn.visibility = View.GONE
        }

        holder.deleteBtn.setOnClickListener {
            medicine.id?.let { id ->
                val sharedPref = holder.itemView.context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
                val currentPharmacyId = sharedPref.getString("pharmacy_id", null)

                if (currentPharmacyId != null) {
                    db.collection("Pharmacies").document(currentPharmacyId).collection("Medicines").document(id)
                        .delete()
                        .addOnSuccessListener {
                            medicineList.removeAt(position)
                            notifyItemRemoved(position)
                            notifyItemRangeChanged(position, medicineList.size)
                            Toast.makeText(holder.itemView.context, "Medicine deleted", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(holder.itemView.context, "Error deleting medicine", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }

        holder.editBtn.setOnClickListener {
            showEditDialog(holder, medicine, position)
        }
    }

    override fun getItemCount(): Int {
        return medicineList.size
    }

    private fun showEditDialog(holder: MedicineViewHolder, medicine: Medicine, position: Int) {
        val context = holder.itemView.context

        val nameInput = EditText(context)
        val brandInput = EditText(context)
        val quantityInput = EditText(context)
        val priceInput = EditText(context)

        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)

            // Medicine Name
            addView(TextView(context).apply {
                text = "Medicine Name"
            })
            nameInput.apply {
                setText(medicine.medicine_name)
                hint = "Enter medicine name"
            }
            addView(nameInput)

            // Brand Name
            addView(TextView(context).apply {
                text = "Brand Name"
                setPadding(0, 30, 0, 0)
            })
            brandInput.apply {
                setText(medicine.brand_name)
                hint = "Enter brand name"
            }
            addView(brandInput)

            // Quantity
            addView(TextView(context).apply {
                text = "Quantity"
                setPadding(0, 30, 0, 0)
            })
            quantityInput.apply {
                setText(medicine.stock.toString())
                hint = "Enter quantity"
                inputType = InputType.TYPE_CLASS_NUMBER
            }
            addView(quantityInput)

            // Price
            addView(TextView(context).apply {
                text = "Price"
                setPadding(0, 30, 0, 0)
            })
            priceInput.apply {
                setText(medicine.price.toString())
                hint = "Enter price"
                inputType = InputType.TYPE_CLASS_NUMBER
            }
            addView(priceInput)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Edit Medicine")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = nameInput.text.toString().trim()
                val newBrand = brandInput.text.toString().trim()
                val newQuantity = quantityInput.text.toString().toIntOrNull() ?: medicine.stock
                val newPrice = priceInput.text.toString().toIntOrNull() ?: medicine.price

                updateMedicine(medicine, newName, newBrand, newQuantity, newPrice, position, context)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun updateMedicine(
        medicine: Medicine,
        newName: String,
        newBrand: String,
        newQuantity: Int,
        newPrice: Int,
        position: Int,
        context: Context
    ) {
        val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val pharmacyId = sharedPref.getString("pharmacy_id", null)

        if (pharmacyId != null && medicine.id != null) {
            val updates = hashMapOf<String, Any>(
                "medicine_name" to newName,
                "brand_name" to newBrand,
                "stock" to newQuantity,
                "price" to newPrice
            )

            db.collection("Pharmacies")
                .document(pharmacyId)
                .collection("Medicines")
                .document(medicine.id!!)
                .update(updates)
                .addOnSuccessListener {
                    medicine.medicine_name = newName
                    medicine.brand_name = newBrand
                    medicine.stock = newQuantity
                    medicine.price = newPrice
                    notifyItemChanged(position)
                    Toast.makeText(context, "Medicine updated", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Error updating medicine", Toast.LENGTH_SHORT).show()
                }
        }
    }

    class MedicineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.med_name)
        val price: TextView = itemView.findViewById(R.id.med_price)
        val quantity: TextView = itemView.findViewById(R.id.med_stock)
        val brand: TextView = itemView.findViewById(R.id.med_brand)
        val editBtn: ImageButton = itemView.findViewById(R.id.btn_edit)
        val deleteBtn: ImageButton = itemView.findViewById(R.id.btn_delete)
    }
}