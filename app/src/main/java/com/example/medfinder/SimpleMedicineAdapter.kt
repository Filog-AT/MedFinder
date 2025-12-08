package com.example.medfinder

import Medicine
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SimpleMedicineAdapter(
    private val medicineList: List<Medicine>
) : RecyclerView.Adapter<SimpleMedicineAdapter.SimpleMedicineViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleMedicineViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_simple_medicine, parent, false)
        return SimpleMedicineViewHolder(view)
    }

    override fun onBindViewHolder(holder: SimpleMedicineViewHolder, position: Int) {
        val medicine = medicineList[position]
        holder.name.text = medicine.medicine_name
        holder.brand.text = medicine.brand_name
        holder.price.text = "Price: ₱${medicine.price}"
        holder.stock.text = "Stock: ${medicine.stock}"
    }

    override fun getItemCount(): Int = medicineList.size

    class SimpleMedicineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.tv_medicine_name)
        val brand: TextView = itemView.findViewById(R.id.tv_brand)
        val price: TextView = itemView.findViewById(R.id.tv_price)
        val stock: TextView = itemView.findViewById(R.id.tv_stock)
    }
}