package com.example.medfinder

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore

class AddFragment : Fragment() {

    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add, container, false)

        db = FirebaseFirestore.getInstance()

        val medBrandName = view.findViewById<EditText>(R.id.brand_name)
        val medName = view.findViewById<EditText>(R.id.medicine_name)
        val medPrice = view.findViewById<EditText>(R.id.price)
        val medStock = view.findViewById<EditText>(R.id.stock)
        val medPrescriptionRequired = view.findViewById<CheckBox>(R.id.prescription_required)
        val prescriptionTypeSpinner = view.findViewById<Spinner>(R.id.prescription_type_spinner)
        val saveButton = view.findViewById<Button>(R.id.btn_save)

        // Setup prescription type spinner
        setupPrescriptionTypeSpinner(prescriptionTypeSpinner, medPrescriptionRequired)

        saveButton.setOnClickListener {
            val brand = medBrandName.text.toString().trim()
            val name = medName.text.toString().trim()
            val price = medPrice.text.toString().toIntOrNull() ?: 0
            val stock = medStock.text.toString().toIntOrNull() ?: 0
            val requiresPrescription = medPrescriptionRequired.isChecked
            val prescriptionType = if (requiresPrescription) {
                prescriptionTypeSpinner.selectedItem.toString()
            } else {
                "Not Required"
            }

            if (brand.isEmpty() || name.isEmpty()) {
                Toast.makeText(context, "Please fill in brand and name fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sharedPref = requireContext().getSharedPreferences("user_session", android.content.Context.MODE_PRIVATE)
            val pharmacyId = sharedPref.getString("pharmacy_id", null)

            if (pharmacyId == null) {
                Toast.makeText(context, "Error: Pharmacy not found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val medicine = hashMapOf(
                "brand_name" to brand,
                "medicine_name" to name,
                "price" to price,
                "stock" to stock,
                "requires_prescription" to requiresPrescription,
                "prescription_type" to prescriptionType,
                "timestamp" to System.currentTimeMillis()
            )

            db.collection("Pharmacies")
                .document(pharmacyId)
                .collection("Medicines")
                .add(medicine)
                .addOnSuccessListener {
                    Toast.makeText(context, "Medicine added!", Toast.LENGTH_SHORT).show()
                    clearForm(medBrandName, medName, medPrice, medStock, medPrescriptionRequired, prescriptionTypeSpinner)
                }
                .addOnFailureListener { e ->
                    Log.w("Firestore", "Error adding document", e)
                    Toast.makeText(context, "Error adding medicine", Toast.LENGTH_SHORT).show()
                }
        }

        return view
    }

    private fun setupPrescriptionTypeSpinner(spinner: Spinner, prescriptionCheckBox: CheckBox) {
        val prescriptionTypes = arrayOf(
            "General Prescription",
            "Controlled Substance",
            "Antibiotic",
            "Psychotropic",
            "Narcotic"
        )

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, prescriptionTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Initially disable spinner if checkbox is unchecked
        spinner.isEnabled = prescriptionCheckBox.isChecked

        // Update spinner enabled state based on checkbox
        prescriptionCheckBox.setOnCheckedChangeListener { _, isChecked ->
            spinner.isEnabled = isChecked
            if (!isChecked) {
                spinner.setSelection(0) // Reset to first item
            }
        }
    }

    private fun clearForm(
        brandName: EditText,
        medicineName: EditText,
        price: EditText,
        stock: EditText,
        prescriptionCheckBox: CheckBox,
        prescriptionSpinner: Spinner
    ) {
        brandName.text.clear()
        medicineName.text.clear()
        price.text.clear()
        stock.text.clear()
        prescriptionCheckBox.isChecked = false
        prescriptionSpinner.setSelection(0)
        prescriptionSpinner.isEnabled = false
    }
}