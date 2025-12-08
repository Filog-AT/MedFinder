package com.example.medfinder

import java.io.Serializable

data class Reservation(
    var id: String? = null,
    var user_id: String = "",
    var pharmacy_id: String = "",
    var medicines: List<MedicineItem> = emptyList(),
    var status: String = "pending",
    var total_price: Int = 0,
    var created_at: Long = 0,
    var has_prescription_meds: Boolean = false,
    var time_limit_minutes: Int = 0,
    var time_limit_set_at: Long = 0,
    var confirmed_at: Long = 0,
    var received_at: Long = 0,
    var customer_name: String = "",
    var customer_phone: String = "",
    var updated_at: Long = 0
) : Serializable