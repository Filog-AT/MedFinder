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

class CustomerMainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_customer_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.customer_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        replaceFragment(CustomerHomeFragment())

        replaceFragment(HomeFragment())
        findViewById<BottomNavigationView>(R.id.bottomNavigationView).setOnItemSelectedListener {item ->
            when(item.itemId){
                R.id.home -> {
                    replaceFragment(CustomerHomeFragment())
                }
                R.id.map -> {
                    replaceFragment(MapFragment ())
                }
            }
            return@setOnItemSelectedListener true
        }
    }
    private fun replaceFragment(fragment: Fragment){
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.customer_nav_host, fragment)
        fragmentTransaction.commit()
    }
}