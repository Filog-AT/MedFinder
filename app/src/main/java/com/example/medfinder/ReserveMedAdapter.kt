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

    // Clear all selections
    fun clearAllSelections() {
        selectedMedicines.clear()
        notifyDataSetChanged()
    }

    // Select all available medicines (including prescription-required)
    fun selectAllMedicines() {
        selectedMedicines.clear()
        medicineList.forEach { medicine ->
            val medicineId = medicine.id
            if (medicine.stock > 0 && medicineId != null) {
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

        // Show prescription requirement as information, not restriction
        if (medicine.requires_prescription) {
            holder.prescriptionInfo.visibility = View.VISIBLE
            holder.prescriptionInfo.text = "⚠️ Bring prescription when claiming"
            // Optional: Change text color to indicate it's a reminder
            holder.prescriptionInfo.setTextColor(holder.itemView.context.getColor(android.R.color.holo_orange_dark))
        } else {
            holder.prescriptionInfo.visibility = View.GONE
        }

        // Check if medicine requires prescription (for information only)
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
        } else {
            // ALLOW selection of ALL medicines, including prescription ones
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
        holder.quantityEditText.isEnabled = holder.checkBox.isChecked

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

            if (isChecked) {
                val quantity = holder.quantityEditText.text.toString().toIntOrNull() ?: 1
                if (quantity > 0 && quantity <= medicine.stock) {
                    selectedMedicines[medicineId] = Pair(medicine, quantity)
                    holder.quantityEditText.isEnabled = true

                    // Show reminder for prescription medicines
                    if (requiresPrescription) {
                        Toast.makeText(holder.itemView.context,
                            "Reminder: Bring your prescription when claiming this medicine",
                            Toast.LENGTH_LONG).show()
                    }
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
            if (medicineId == null || !holder.checkBox.isChecked)
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

        // Optional: Show info when clicking on prescription medicine
        if (requiresPrescription) {
            holder.itemView.setOnClickListener {
                if (!holder.checkBox.isChecked && canReserve) {
                    Toast.makeText(holder.itemView.context,
                        "Remember to bring your prescription when claiming this medicine",
                        Toast.LENGTH_LONG).show()
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
        val prescriptionInfo: TextView = itemView.findViewById(R.id.tv_prescription_warning)
        val quantityEditText: EditText = itemView.findViewById(R.id.et_quantity)
    }
}