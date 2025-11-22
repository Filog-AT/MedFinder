package com.example.medfinder

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class CustomerLoginFragment : AppCompatActivity() {

    private var selectedRole: String = "customer"
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_customer_login)

        db = FirebaseFirestore.getInstance()

        // UI references
        val username = findViewById<EditText>(R.id.username_input)
        val password = findViewById<EditText>(R.id.password_input)
        val loginBtn = findViewById<Button>(R.id.login_btn)
        val guestloginBtn = findViewById<Button>(R.id.guest_login)

        selectedRole = "customer"


        // Login button
        loginBtn.setOnClickListener {
            val user = username.text.toString().trim()
            val pass = password.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Query Users collection instead of Pharmacies
            db.collection("Users")
                .whereEqualTo("username", user)
                .whereEqualTo("password", pass) // In production, use Firebase Auth
                .whereEqualTo("is_active", true)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val doc = documents.documents[0]
                        val role = doc.getString("role") ?: "customer"

                        if (role != selectedRole) {
                            Toast.makeText(
                                this,
                                "Selected role does not match account role",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@addOnSuccessListener
                        }

                        // Save user data to SharedPreferences or pass to next activity
                        val userId = doc.getString("user_id") ?: doc.id
                        saveUserSession(userId, role, doc.data)

                        // Navigate to appropriate activity
                        startActivity(Intent(this, CustomerMainActivity::class.java))

                        finish()
                    } else {
                        Toast.makeText(this, "Invalid username or password", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        guestloginBtn.setOnClickListener {
            val intent = Intent(this, CustomerMainActivity::class.java)
            startActivity(intent)
            finish()
        }

    } private fun saveUserSession(userId: String, role: String, userData: Map<String, Any>?) {
        val sharedPreferences = getSharedPreferences("user_session", MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        editor.putString("USER_ID", userId)
        editor.putString("USER_ROLE", role)
        editor.putBoolean("IS_LOGGED_IN", true)

        // Optionally save other user data if needed
        // For example, save the username
        userData?.get("username")?.let { username ->
            editor.putString("USERNAME", username.toString())
        }

        editor.apply() // Use apply() to save changes asynchronously
    }
}