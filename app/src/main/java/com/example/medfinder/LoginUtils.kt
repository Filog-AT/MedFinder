package com.example.medfinder

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import android.content.SharedPreferences

object LoginUtils {
    private const val GUEST_USER_ID = "GUEST_USER"
    fun isUserLoggedIn(context: Context): Boolean {
        val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

        val userId = sharedPref.getString("USER_ID", null) ?: sharedPref.getString("user_id", null)
        val userRole = sharedPref.getString("USER_ROLE", null) ?: sharedPref.getString("role", null)
        val isLoggedIn = sharedPref.getBoolean("IS_LOGGED_IN", false)

        Log.d("LoginUtils", "Checking login - userId: $userId, userRole: $userRole, IS_LOGGED_IN: $isLoggedIn")

        if (userId == GUEST_USER_ID) {
            Log.d("LoginUtils", "❌ User is GUEST, not logged in")
            return false
        }

        val isValid = userId != null && userRole != null && isLoggedIn && userId != GUEST_USER_ID

        if (!isValid) {
            Log.d("LoginUtils", "❌ NOT logged in")
        } else {
            Log.d("LoginUtils", "✅ User IS logged in")
        }

        return isValid
    }

    fun getCurrentUserName(context: Context): String? {
        val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        return sharedPref.getString("username", null)
    }

    fun isGuestUser(context: Context): Boolean {
        val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val userId = sharedPref.getString("USER_ID", null)
        return userId == GUEST_USER_ID
    }

    fun clearConflictingSession(context: Context) {
        val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

        val hasCustomerData = sharedPref.contains("USER_ID")
        val hasPharmacyData = sharedPref.contains("user_id")

        if (hasCustomerData && hasPharmacyData) {
            Log.d("LoginUtils", "⚠️ Found conflicting session data!")

            with(sharedPref.edit()) {
                // Clear pharmacy data and keep customer data
                remove("role")
                remove("user_id")
                remove("pharmacy_id")
                remove("email")
                remove("username")
                apply()
            }
            Log.d("LoginUtils", "Cleared conflicting pharmacy data")
        }
    }

    fun setAsGuest(context: Context) {
        val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("USER_ID", GUEST_USER_ID)
            putBoolean("IS_LOGGED_IN", false)
            apply()
        }
        Log.d("LoginUtils", "User set as guest")
    }

    fun redirectToLogin(context: Context, message: String = "Please login to continue") {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        Log.d("LoginUtils", "Redirecting to login: $message")

        val intent = Intent(context, CustomerLoginFragment::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun getCurrentUserId(context: Context): String? {
        val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

        val userId = sharedPref.getString("USER_ID", null) ?: sharedPref.getString("user_id", null)

        return if (userId == GUEST_USER_ID) null else userId
    }

    fun getCurrentUserType(context: Context): String? {
        val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val userType = sharedPref.getString("USER_ROLE", null) ?: sharedPref.getString("role", null)

        return if (isGuestUser(context)) null else userType
    }

    fun isCustomer(context: Context): Boolean {
        if (isGuestUser(context)) return false
        val userType = getCurrentUserType(context)
        return userType?.lowercase() == "customer"
    }

    fun isPharmacy(context: Context): Boolean {
        if (isGuestUser(context)) return false
        val userType = getCurrentUserType(context)
        return userType?.lowercase() == "pharmacy"
    }

    fun logout(context: Context) {
        val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            clear()
            apply()
        }
        Log.d("LoginUtils", "User logged out, session cleared")
        redirectToLogin(context, "Logged out successfully")
    }
}