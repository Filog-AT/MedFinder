package com.example.medfinder

data class Reservation(
    var id: String? = null,
    var user_id: String = "",
    var pharmacy_id: String = "",
    var medicines: List<MedicineItem> = emptyList(),
    var status: String = "pending", // pending, confirmed, completed, cancelled
    var total_price: Int = 0,
    var created_at: Long = 0,
    var has_prescription_meds: Boolean = false
)