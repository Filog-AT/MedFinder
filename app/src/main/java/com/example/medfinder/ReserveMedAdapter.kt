package com.example.medfinder

import Medicine
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.CheckBox
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class ReservationMedicineAdapter(
    private val medicineList: List<Medicine>,
    private val canReserve: Boolean = false
) : RecyclerView.Adapter<ReservationMedicineAdapter.ReservationViewHolder>() {

    private val selectedMedicines = mutableMapOf<String, Pair<Medicine, Int>>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reserve_med, parent, false)
        return ReservationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReservationViewHolder, position: Int) {
        val medicine = medicineList[position]

        holder.name.text = medicine.medicine_name
        holder.brand.text = medicine.brand_name
        holder.price.text = "Price: ₱${medicine.price}"
        holder.stock.text = "Available: ${medicine.stock}"

        if (!canReserve) {
            holder.checkBox.isEnabled = false
            holder.quantityEditText.isEnabled = false
            holder.checkBox.alpha = 0.5f
            holder.quantityEditText.alpha = 0.5f

            // Show message why
            holder.itemView.setOnClickListener {
                Toast.makeText(holder.itemView.context,
                    "Please login as customer to select medicines",
                    Toast.LENGTH_SHORT).show()
            }
        } else {
            holder.checkBox.isEnabled = true
            holder.quantityEditText.isEnabled = holder.checkBox.isChecked
            holder.checkBox.alpha = 1f
            holder.quantityEditText.alpha = 1f
        }

        holder.checkBox.setOnCheckedChangeListener(null) // Clear previous listener

        // Reset UI state
        holder.checkBox.isChecked = selectedMedicines.containsKey(medicine.id)
        holder.quantityEditText.isEnabled = holder.checkBox.isChecked
        holder.quantityEditText.setText(
            if (selectedMedicines.containsKey(medicine.id))
                selectedMedicines[medicine.id]!!.second.toString()
            else "1"
        )

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val quantity = holder.quantityEditText.text.toString().toIntOrNull() ?: 1
                if (quantity > 0 && quantity <= medicine.stock) {
                    selectedMedicines[medicine.id!!] = Pair(medicine, quantity)
                    holder.quantityEditText.isEnabled = true
                } else {
                    holder.checkBox.isChecked = false
                    Toast.makeText(holder.itemView.context,
                        "Invalid quantity. Available: ${medicine.stock}",
                        Toast.LENGTH_SHORT).show()
                }
            } else {
                selectedMedicines.remove(medicine.id)
                holder.quantityEditText.isEnabled = false
            }
        }

        holder.quantityEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && holder.checkBox.isChecked) {
                val quantity = holder.quantityEditText.text.toString().toIntOrNull() ?: 1
                if (quantity > 0 && quantity <= medicine.stock) {
                    selectedMedicines[medicine.id!!] = Pair(medicine, quantity)
                } else {
                    holder.quantityEditText.setText("1")
                    selectedMedicines[medicine.id!!] = Pair(medicine, 1)
                    Toast.makeText(holder.itemView.context,
                        "Quantity set to 1. Max available: ${medicine.stock}",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun getItemCount(): Int = medicineList.size

    fun getSelectedMedicines(): List<Pair<Medicine, Int>> {
        return selectedMedicines.values.toList()
    }

    class ReservationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkBox: CheckBox = itemView.findViewById(R.id.checkbox_medicine)
        val name: TextView = itemView.findViewById(R.id.tv_medicine_name)
        val brand: TextView = itemView.findViewById(R.id.tv_brand)
        val price: TextView = itemView.findViewById(R.id.tv_price)
        val stock: TextView = itemView.findViewById(R.id.tv_stock)
        val quantityEditText: EditText = itemView.findViewById(R.id.et_quantity)
    }
}