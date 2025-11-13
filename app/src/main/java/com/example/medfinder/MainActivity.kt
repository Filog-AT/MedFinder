package com.example.medfinder

import android.content.ContentValues.TAG
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
        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {
            val pharmacyRef = db.collection("Pharmacies").document(user.uid)
            pharmacyRef.get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val name = document.getString("pharmacy_name")
                        pharmacyNameText.text = name ?: "Pharmacy"
                    } else {
                        pharmacyNameText.text = "Pharmacy"
                    }
                }
                .addOnFailureListener {
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