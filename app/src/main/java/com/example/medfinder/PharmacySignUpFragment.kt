package com.example.medfinder

import android.content.Intent
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class PharmacySignUpFragment : AppCompatActivity() {

    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null

    // Activity result launcher for location picker
    private val locationPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            selectedLatitude = data?.getDoubleExtra(PharmacyLocationPickerActivity.EXTRA_LATITUDE, 0.0)
            selectedLongitude = data?.getDoubleExtra(PharmacyLocationPickerActivity.EXTRA_LONGITUDE, 0.0)

            // Update UI to show location is selected
            val tvLocationSelected = findViewById<TextView>(R.id.tv_location_selected)
            tvLocationSelected.visibility = View.VISIBLE
            tvLocationSelected.text = "Location selected: ${selectedLatitude?.let { "%.6f".format(it) }}, ${selectedLongitude?.let { "%.6f".format(it) }}"

            // Also update button text
            val btnSetLocation = findViewById<Button>(R.id.btn_set_location)
            btnSetLocation.text = "📍 Change Location"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_pharmacy_sign_up)

        val backButton = findViewById<ImageView>(R.id.btn_back)
        val pharmacyNameInput = findViewById<EditText>(R.id.et_pharmacy_name)
        val usernameInput = findViewById<EditText>(R.id.et_username)
        val emailInput = findViewById<EditText>(R.id.et_email)
        val phoneInput = findViewById<EditText>(R.id.et_phone)
        val passwordInput = findViewById<EditText>(R.id.et_password)
        val confirmPasswordInput = findViewById<EditText>(R.id.et_confirm_password)
        val showPasswordToggle = findViewById<ImageView>(R.id.iv_show_password)
        val showConfirmPasswordToggle = findViewById<ImageView>(R.id.iv_show_confirm_password)
        val signUpButton = findViewById<Button>(R.id.btn_signup)
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        val tvLogin = findViewById<TextView>(R.id.tv_login)
        val cbTerms = findViewById<CheckBox>(R.id.cb_terms)
        val btnSetLocation = findViewById<Button>(R.id.btn_set_location)

        // Back button
        backButton.setOnClickListener {
            finish()
        }

        // Set Location Button
        btnSetLocation.setOnClickListener {
            val intent = Intent(this, PharmacyLocationPickerActivity::class.java)
            locationPickerLauncher.launch(intent)
        }

        // Toggle password visibility
        var isPasswordVisible = false
        showPasswordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                passwordInput.transformationMethod = null
                showPasswordToggle.setImageResource(R.drawable.ic_visibility_off)
            } else {
                passwordInput.transformationMethod = PasswordTransformationMethod()
                showPasswordToggle.setImageResource(R.drawable.ic_visibility)
            }
            passwordInput.setSelection(passwordInput.text.length)
        }

        var isConfirmPasswordVisible = false
        showConfirmPasswordToggle.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            if (isConfirmPasswordVisible) {
                confirmPasswordInput.transformationMethod = null
                showConfirmPasswordToggle.setImageResource(R.drawable.ic_visibility_off)
            } else {
                confirmPasswordInput.transformationMethod = PasswordTransformationMethod()
                showConfirmPasswordToggle.setImageResource(R.drawable.ic_visibility)
            }
            confirmPasswordInput.setSelection(confirmPasswordInput.text.length)
        }

        // Sign up button
        signUpButton.setOnClickListener {
            val pharmacyName = pharmacyNameInput.text.toString().trim()
            val username = usernameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()
            val termsAccepted = cbTerms.isChecked

            // Validation (including location)
            if (pharmacyName.isEmpty() || username.isEmpty() || email.isEmpty() ||
                phone.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedLatitude == null || selectedLongitude == null) {
                Toast.makeText(this, "Please set pharmacy location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!termsAccepted) {
                Toast.makeText(this, "Please accept the terms and conditions", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show progress
            progressBar.visibility = View.VISIBLE
            signUpButton.isEnabled = false

            // Sign up using Firestore only (with location)
            signUpPharmacyWithLocation(
                pharmacyName = pharmacyName,
                username = username,
                email = email,
                phone = phone,
                password = password,
                latitude = selectedLatitude!!,
                longitude = selectedLongitude!!,
                termsAccepted = termsAccepted
            )
        }

        // Login link
        tvLogin.setOnClickListener {
            val intent = Intent(this, LoginFragment::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun signUpPharmacyWithLocation(
        pharmacyName: String,
        username: String,
        email: String,
        phone: String,
        password: String,
        latitude: Double,
        longitude: Double,
        termsAccepted: Boolean
    ) {
        FirebaseAuthHelper.signUpUser(
            context = this,
            email = email,
            password = password,
            username = username,
            role = "pharmacy",
            pharmacyName = pharmacyName,
            phone = phone,
            onSuccess = { userId ->
                // Now save location information
                savePharmacyLocation(userId, pharmacyName, latitude, longitude) { success, message ->
                    val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
                    val signUpButton = findViewById<Button>(R.id.btn_signup)

                    progressBar.visibility = View.GONE
                    signUpButton.isEnabled = true

                    if (success) {
                        // Save user session
                        val sharedPref = getSharedPreferences("user_session", MODE_PRIVATE)
                        with(sharedPref.edit()) {
                            putString("user_id", userId)
                            putString("role", "pharmacy")
                            putString("username", username)
                            putString("email", email)
                            putString("pharmacy_name", pharmacyName)
                            putBoolean("is_logged_in", true)
                            apply()
                        }

                        Toast.makeText(this, "Pharmacy registered successfully!", Toast.LENGTH_SHORT).show()

                        // Go to pharmacy main activity
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onFailure = { errorMessage ->
                val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
                val signUpButton = findViewById<Button>(R.id.btn_signup)

                progressBar.visibility = View.GONE
                signUpButton.isEnabled = true
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun savePharmacyLocation(
        pharmacyId: String,
        pharmacyName: String,
        latitude: Double,
        longitude: Double,
        callback: (Boolean, String) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()

        // Create or update pharmacy document with location
        val pharmacyData = hashMapOf(
            "pharmacy_id" to pharmacyId,
            "pharmacy_name" to pharmacyName,
            "Location" to com.google.firebase.firestore.GeoPoint(latitude, longitude),
            "latitude" to latitude,
            "longitude" to longitude,
            "updated_at" to System.currentTimeMillis()
        )

        // Update the existing pharmacy document
        db.collection("Pharmacies").document(pharmacyId)
            .update(pharmacyData as Map<String, Any>)
            .addOnSuccessListener {
                callback(true, "Location saved successfully")
            }
            .addOnFailureListener { e ->
                // If update fails, try set (create)
                db.collection("Pharmacies").document(pharmacyId)
                    .set(pharmacyData)
                    .addOnSuccessListener {
                        callback(true, "Pharmacy location saved")
                    }
                    .addOnFailureListener { e2 ->
                        callback(false, "Failed to save location: ${e2.message}")
                    }
            }
    }
}