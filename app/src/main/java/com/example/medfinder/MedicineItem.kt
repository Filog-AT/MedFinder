package com.example.medfinder

data class MedicineItem(
    var medicine_id: String = "",
    var medicine_name: String = "",
    var quantity: Int = 0,
    var price: Int = 0,
    var requires_prescription: Boolean = false
)