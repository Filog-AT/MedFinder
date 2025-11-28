package com.example.medfinder

import android.content.ContentValues.TAG
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        val pharmacyNameText = findViewById<TextView>(R.id.pharma_name)
        val db = FirebaseFirestore.getInstance()
        val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val pharmacyId = sharedPref.getString("pharmacy_id", null)
        val username = sharedPref.getString("username", null)

        Log.d("MainActivity", "📋 Retrieved from SharedPreferences:")
        Log.d("MainActivity", "   pharmacy_id: $pharmacyId")
        Log.d("MainActivity", "   username: $username")

        if (pharmacyId == null) {
            Log.e("MainActivity", "❌ No pharmacy_id found in SharedPreferences")
            pharmacyNameText.text = "Pharmacy" // Default fallback
        } else {
            // Load pharmacy name using the pharmacy_id from SharedPreferences
            val pharmacyRef = db.collection("Pharmacies").document(pharmacyId)
            pharmacyRef.get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val name = document.getString("pharmacy_name")
                        Log.d("MainActivity", "✅ Loaded pharmacy name: $name")
                        pharmacyNameText.text = name ?: "Pharmacy"
                    } else {
                        Log.e("MainActivity", "❌ Pharmacy document not found for ID: $pharmacyId")
                        pharmacyNameText.text = "Pharmacy"
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("MainActivity", "❌ Error loading pharmacy", e)
                    pharmacyNameText.text = "Pharmacy"
                }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_layout, ListFragment())
            .commit()

        replaceFragment(HomeFragment())
        findViewById<BottomNavigationView>(R.id.btm_nav).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> {
                    replaceFragment(HomeFragment())
                }
                R.id.add -> {
                    replaceFragment(AddFragment())
                }
                R.id.list -> {
                    replaceFragment(ListFragment())
                }
            }
            return@setOnItemSelectedListener true
        }

    }
    private fun replaceFragment(fragment: Fragment){
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.frame_layout, fragment)
        fragmentTransaction.commit()
    }
}