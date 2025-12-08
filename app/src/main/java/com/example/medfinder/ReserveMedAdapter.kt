package com.example.medfinder

import Medicine
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class ReservationMedicineAdapter(
    private val medicineList: List<Medicine>,
    private val canReserve: Boolean = false
) : RecyclerView.Adapter<ReservationMedicineAdapter.ReservationViewHolder>() {

    private val selectedMedicines = mutableMapOf<String, Pair<Medicine, Int>>()

    // Clear all selections
    fun clearAllSelections() {
        selectedMedicines.clear()
        notifyDataSetChanged()
    }

    // Select all available medicines (excluding prescription-required)
    fun selectAllMedicines() {
        selectedMedicines.clear()
        medicineList.forEach { medicine ->
            val medicineId = medicine.id
            // Only select medicines that don't require prescription
            if (medicine.stock > 0 && medicineId != null && !medicine.requires_prescription) {
                selectedMedicines[medicineId] = Pair(medicine, 1)
            }
        }
        notifyDataSetChanged()
    }

    // Deselect all medicines
    fun deselectAllMedicines() {
        selectedMedicines.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reserve_med, parent, false)
        return ReservationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReservationViewHolder, position: Int) {
        val medicine = medicineList[position]
        val medicineId = medicine.id

        holder.name.text = medicine.medicine_name
        holder.brand.text = medicine.brand_name
        holder.price.text = "Price: ₱${medicine.price}"
        holder.stock.text = "Available: ${medicine.stock}"

        // Show prescription requirement warning
        if (medicine.requires_prescription) {
            holder.prescriptionWarning.visibility = View.VISIBLE
            holder.prescriptionWarning.text = "📋 Prescription Required"
        } else {
            holder.prescriptionWarning.visibility = View.GONE
        }

        // Check if medicine requires prescription
        val requiresPrescription = medicine.requires_prescription

        if (!canReserve) {
            // User not logged in or not customer
            holder.checkBox.isEnabled = false
            holder.quantityEditText.isEnabled = false
            holder.checkBox.alpha = 0.5f
            holder.quantityEditText.alpha = 0.5f

            holder.itemView.setOnClickListener {
                Toast.makeText(holder.itemView.context,
                    "Please login as customer to select medicines",
                    Toast.LENGTH_SHORT).show()
            }
        } else if (requiresPrescription) {
            // Medicine requires prescription - disable selection
            holder.checkBox.isEnabled = false
            holder.quantityEditText.isEnabled = false
            holder.checkBox.alpha = 0.5f
            holder.quantityEditText.alpha = 0.5f

            // Show why it's disabled
            holder.itemView.setOnClickListener {
                Toast.makeText(holder.itemView.context,
                    "This medicine requires a doctor's prescription. Please visit the pharmacy with your prescription.",
                    Toast.LENGTH_LONG).show()
            }

            // Change text color to indicate prescription requirement
            holder.name.setTextColor(holder.itemView.context.getColor(android.R.color.holo_red_dark))
        } else {
            // Medicine doesn't require prescription - enable selection
            holder.checkBox.isEnabled = true
            holder.quantityEditText.isEnabled = holder.checkBox.isChecked
            holder.checkBox.alpha = 1f
            holder.quantityEditText.alpha = 1f

            // Reset text color
            holder.name.setTextColor(holder.itemView.context.getColor(android.R.color.black))
        }

        holder.checkBox.setOnCheckedChangeListener(null) // Clear previous listener

        // Reset UI state
        val isSelected = if (medicineId != null) selectedMedicines.containsKey(medicineId) else false
        holder.checkBox.isChecked = isSelected
        holder.quantityEditText.isEnabled = holder.checkBox.isChecked && !requiresPrescription

        holder.quantityEditText.setText(
            if (isSelected && medicineId != null)
                selectedMedicines[medicineId]!!.second.toString()
            else "1"
        )

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (medicineId == null) {
                holder.checkBox.isChecked = false
                Toast.makeText(holder.itemView.context,
                    "Medicine ID is missing",
                    Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }

            if (requiresPrescription) {
                holder.checkBox.isChecked = false
                Toast.makeText(holder.itemView.context,
                    "Cannot reserve: This medicine requires a doctor's prescription",
                    Toast.LENGTH_LONG).show()
                return@setOnCheckedChangeListener
            }

            if (isChecked) {
                val quantity = holder.quantityEditText.text.toString().toIntOrNull() ?: 1
                if (quantity > 0 && quantity <= medicine.stock) {
                    selectedMedicines[medicineId] = Pair(medicine, quantity)
                    holder.quantityEditText.isEnabled = true
                } else {
                    holder.checkBox.isChecked = false
                    Toast.makeText(holder.itemView.context,
                        "Invalid quantity. Available: ${medicine.stock}",
                        Toast.LENGTH_SHORT).show()
                }
            } else {
                selectedMedicines.remove(medicineId)
                holder.quantityEditText.isEnabled = false
            }
        }

        holder.quantityEditText.setOnFocusChangeListener { _, hasFocus ->
            if (medicineId == null || !holder.checkBox.isChecked || requiresPrescription)
                return@setOnFocusChangeListener

            if (!hasFocus) {
                val quantity = holder.quantityEditText.text.toString().toIntOrNull() ?: 1
                if (quantity > 0 && quantity <= medicine.stock) {
                    selectedMedicines[medicineId] = Pair(medicine, quantity)
                } else {
                    holder.quantityEditText.setText("1")
                    selectedMedicines[medicineId] = Pair(medicine, 1)
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
        val prescriptionWarning: TextView = itemView.findViewById(R.id.tv_prescription_warning)
        val quantityEditText: EditText = itemView.findViewById(R.id.et_quantity)
    }
}