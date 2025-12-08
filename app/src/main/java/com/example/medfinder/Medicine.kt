data class Medicine(
    var id: String? = null,
    var brand_name: String = "",
    var medicine_name: String = "",
    val category: String = "",
    val pharmacy_id: String = "",
    var price: Int = 0,
    var stock: Int = 0,
    var distance: Double = 0.0,
    var requires_prescription: Boolean = false
)
