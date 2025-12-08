package com.example.medfinder

import java.io.Serializable

data class MedicineItem(
    var medicine_id: String = "",
    var medicine_name: String = "",
    var quantity: Int = 0,
    var price: Int = 0,
    var requires_prescription: Boolean = false
) : Serializable