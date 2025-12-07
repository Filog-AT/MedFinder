package com.example.medfinder

import android.content.Intent
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class CustomerSignUpFragment : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_customer_sign_up)

        val backButton = findViewById<ImageView>(R.id.btn_back)
        val usernameInput = findViewById<EditText>(R.id.et_username)
        val emailInput = findViewById<EditText>(R.id.et_email)
        val passwordInput = findViewById<EditText>(R.id.et_password)
        val confirmPasswordInput = findViewById<EditText>(R.id.et_confirm_password)
        val showPasswordToggle = findViewById<ImageView>(R.id.iv_show_password)
        val showConfirmPasswordToggle = findViewById<ImageView>(R.id.iv_show_confirm_password)
        val signUpButton = findViewById<Button>(R.id.btn_signup)
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        val tvLogin = findViewById<TextView>(R.id.tv_login)

        // Back button
        backButton.setOnClickListener {
            finish()
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

        // Toggle confirm password visibility
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
            val username = usernameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()

            // Validation (simplified for testing)
            if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
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

            // Show progress
            progressBar.visibility = View.VISIBLE
            signUpButton.isEnabled = false

            // Sign up using Firestore only
            FirebaseAuthHelper.signUpUser(
                context = this,
                email = email,
                password = password,
                username = username,
                role = "customer",
                onSuccess = { userId ->
                    progressBar.visibility = View.GONE
                    signUpButton.isEnabled = true

                    // Save user session
                    val sharedPref = getSharedPreferences("user_session", MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putString("user_id", userId)
                        putString("role", "customer")
                        putString("username", username)
                        putString("email", email)
                        putBoolean("is_logged_in", true)
                        apply()
                    }

                    Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()

                    // Go to customer main activity
                    val intent = Intent(this, CustomerMainActivity::class.java)
                    startActivity(intent)
                    finish()
                },
                onFailure = { errorMessage ->
                    progressBar.visibility = View.GONE
                    signUpButton.isEnabled = true
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            )
        }

        // Login link
        tvLogin.setOnClickListener {
            val intent = Intent(this, CustomerLoginFragment::class.java)
            startActivity(intent)
            finish()
        }
    }
}