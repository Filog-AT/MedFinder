package com.example.medfinder

import Medicine
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import androidx.appcompat.app.AlertDialog
import android.widget.Button

class MedicineAdapter(
    private val medicineList: MutableList<Medicine>,
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
        holder.name.text = medicine.brand_name
        holder.category.text = medicine.category
        if (showActions) {
            holder.editBtn.visibility = View.VISIBLE
            holder.deleteBtn.visibility = View.VISIBLE
        } else {
            holder.editBtn.visibility = View.GONE
            holder.deleteBtn.visibility = View.GONE
        }

        holder.deleteBtn.setOnClickListener {
            medicine.id?.let { id ->
                db.collection("medicines").document(id)
                    .delete()
                    .addOnSuccessListener {
                        medicineList.removeAt(position)
                        notifyItemRemoved(position)
                        notifyItemRangeChanged(position, medicineList.size)
                    }
            }
        }

        holder.editBtn.setOnClickListener {
            val context = holder.itemView.context
            val dialog = AlertDialog.Builder(context)
            val input = EditText(context)
            input.setText(medicine.brand_name)

            dialog.setTitle("Edit Medicine Name")
            dialog.setView(input)

            dialog.setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString()
                medicine.id?.let { id ->
                    db.collection("medicines").document(id)
                        .update("name", newName)
                        .addOnSuccessListener {
                            medicine.brand_name = newName
                            notifyItemChanged(position)
                        }
                }
            }

            dialog.setNegativeButton("Cancel", null)
            dialog.show()
        }
    }

    override fun getItemCount(): Int = medicineList.size

    class MedicineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.med_name)
        val category: TextView = itemView.findViewById(R.id.med_category)
        val editBtn: Button = itemView.findViewById(R.id.btn_edit)
        val deleteBtn: Button = itemView.findViewById(R.id.btn_delete)
    }
}
