package com.example.medfinder

import android.content.Context
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

object FirebaseAuthHelper {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    fun signUpUser(
        context: Context,
        email: String,
        password: String,
        username: String,
        role: String,
        pharmacyName: String = "",
        phone: String = "",
        latitude: Double? = null,  // Added latitude parameter
        longitude: Double? = null,  // Added longitude parameter
        onSuccess: (userId: String) -> Unit,
        onFailure: (message: String) -> Unit
    ) {
        if (email.isEmpty() || password.isEmpty() || username.isEmpty()) {
            onFailure("Please fill all fields")
            return
        }

        if (password.length < 6) {
            onFailure("Password must be at least 6 characters")
            return
        }

        // Check if username already exists
        db.collection("Users")
            .whereEqualTo("username", username)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    onFailure("Username already taken")
                } else {
                    // Check if email already exists
                    db.collection("Users")
                        .whereEqualTo("email", email)
                        .get()
                        .addOnSuccessListener { emailDocs ->
                            if (!emailDocs.isEmpty) {
                                onFailure("Email already registered")
                            } else {
                                // Generate unique user ID
                                val userId = generateUserId()

                                // Save user to Firestore (store password as plain text for testing)
                                saveUserToFirestore(
                                    userId,
                                    email,
                                    password,  // Store as plain text for testing
                                    username,
                                    role,
                                    pharmacyName,
                                    phone,
                                    latitude,    // Pass latitude
                                    longitude,   // Pass longitude
                                    onSuccess,
                                    onFailure
                                )
                            }
                        }
                        .addOnFailureListener {
                            onFailure("Network error. Please try again")
                        }
                }
            }
            .addOnFailureListener {
                onFailure("Network error. Please try again")
            }
    }

    private fun generateUserId(): String {
        // Generate a unique ID using UUID
        return "user_" + UUID.randomUUID().toString().substring(0, 8)
    }

    private fun saveUserToFirestore(
        userId: String,
        email: String,
        password: String,
        username: String,
        role: String,
        pharmacyName: String,
        phone: String,
        latitude: Double? = null,
        longitude: Double? = null,
        onSuccess: (userId: String) -> Unit,
        onFailure: (message: String) -> Unit
    ) {
        val userData = hashMapOf(
            "user_id" to userId,
            "email" to email,
            "password" to password,
            "username" to username,
            "role" to role,
            "is_active" to true,
            "created_at" to System.currentTimeMillis()
        )

        // Add pharmacy-specific fields
        if (role == "pharmacy") {
            userData["pharmacy_name"] = pharmacyName
            userData["phone"] = phone
            userData["pharmacy_id"] = userId

            // Save pharmacy document with location if provided
            savePharmacyDocument(userId, pharmacyName, phone, email, username, latitude, longitude)
        }

        db.collection("Users").document(userId)
            .set(userData)
            .addOnSuccessListener {
                onSuccess(userId)
            }
            .addOnFailureListener { e ->
                onFailure("Failed to save user data: ${e.message}")
            }
    }

    private fun savePharmacyDocument(
        pharmacyId: String,
        pharmacyName: String,
        phone: String,
        email: String,
        username: String,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        // Create a mutable map instead of hashMapOf
        val pharmacyData = mutableMapOf<String, Any>(
            "pharmacy_id" to pharmacyId,
            "pharmacy_name" to pharmacyName,
            "phone" to phone,
            "email" to email,
            "owner_username" to username,
            "is_active" to true,
            "created_at" to System.currentTimeMillis()
        )

        // Add location if provided
        if (latitude != null && longitude != null) {
            // Create Firestore GeoPoint object
            val geoPoint = com.google.firebase.firestore.GeoPoint(latitude, longitude)
            pharmacyData["Location"] = geoPoint
            pharmacyData["latitude"] = latitude
            pharmacyData["longitude"] = longitude
        }

        db.collection("Pharmacies").document(pharmacyId)
            .set(pharmacyData)
            .addOnFailureListener { e ->
                println("Failed to save pharmacy document: ${e.message}")
            }
    }
}