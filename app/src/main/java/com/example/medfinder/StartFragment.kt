package com.example.medfinder

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity


class StartActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_start)

        val btnCustomer = findViewById<Button?>(R.id.btnCustomer)
        val btnPharmacy = findViewById<Button?>(R.id.btnPharmacy)

        btnCustomer.setOnClickListener(View.OnClickListener { v: View? ->
            val i = Intent(this, CustomerLoginFragment::class.java)
            i.putExtra("role", "customer")
            startActivity(i)
        })

        btnPharmacy.setOnClickListener(View.OnClickListener { v: View? ->
            val i = Intent(this, LoginFragment::class.java)
            i.putExtra("role", "pharmacy")
            startActivity(i)
        })
    }
}