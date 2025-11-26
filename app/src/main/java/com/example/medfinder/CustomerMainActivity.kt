package com.example.medfinder

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class CustomerMainActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val medicineSuggestions = mutableListOf<String>()
    private var suggestionsAdapter: ArrayAdapter<String>? = null
    private lateinit var sharedViewModel: SharedViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_customer_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.customer_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize ViewModel
        sharedViewModel = ViewModelProvider(this)[SharedViewModel::class.java]

        // Initialize with default medicines
        medicineSuggestions.addAll(getDefaultMedicines())
        suggestionsAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, medicineSuggestions)

        setupViews()
        loadMedicineSuggestionsFromFirestore()

        // Load initial fragment
        if (savedInstanceState == null) {
            replaceFragment(CustomerHomeFragment())
        }
    }

    private fun setupViews() {
        val searchView = findViewById<SearchView>(R.id.search)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        // Setup search view
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { performSearch(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let { filterMedicineSuggestions(it) }
                return true
            }
        })

        // Setup search suggestions
        setupSearchSuggestions(searchView)

        // Setup bottom navigation - SIMPLIFIED
        bottomNav.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.home -> {
                    searchView.setQuery("", false)
                    searchView.clearFocus()
                    replaceFragment(CustomerHomeFragment())
                }
                R.id.map -> {
                    // Always create new MapFragment to ensure it gets the latest ViewModel state
                    replaceFragment(MapFragment())
                }
            }
            true
        }
    }

    private fun setupSearchSuggestions(searchView: SearchView) {
        try {
            val searchAutoComplete = searchView.findViewById<android.widget.AutoCompleteTextView>(
                androidx.appcompat.R.id.search_src_text
            )
            searchAutoComplete?.apply {
                setAdapter(suggestionsAdapter)
                threshold = 1
                setOnItemClickListener { parent, view, position, id ->
                    val selectedMedicine = suggestionsAdapter?.getItem(position)
                    selectedMedicine?.let {
                        setText(it)
                        performSearch(it)
                        searchView.clearFocus()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SearchView", "Error setting up search suggestions", e)
        }
    }

    private fun filterMedicineSuggestions(query: String) {
        try {
            val filtered = if (query.isEmpty()) {
                medicineSuggestions
            } else {
                medicineSuggestions.filter {
                    it.lowercase(Locale.getDefault()).contains(query.lowercase(Locale.getDefault()))
                }
            }

            val filteredAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filtered)
            val searchAutoComplete = findViewById<SearchView>(R.id.search)
                .findViewById<android.widget.AutoCompleteTextView>(androidx.appcompat.R.id.search_src_text)

            searchAutoComplete?.setAdapter(filteredAdapter)
            if (query.isNotEmpty() && filtered.isNotEmpty()) {
                searchAutoComplete?.showDropDown()
            } else if (query.isEmpty()) {
                searchAutoComplete?.showDropDown()
            }
        } catch (e: Exception) {
            Log.e("SearchView", "Error filtering suggestions", e)
        }
    }

    private fun loadMedicineSuggestionsFromFirestore() {
        db.collection("Medicines").get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    medicineSuggestions.clear()
                    result.documents.forEach { doc ->
                        doc.getString("name")?.let { medicineSuggestions.add(it) }
                    }
                    suggestionsAdapter?.notifyDataSetChanged()
                    Log.d("MedicineSuggestions", "Loaded ${medicineSuggestions.size} medicines from Firestore")
                }
            }
            .addOnFailureListener { e ->
                Log.e("MedicineSuggestions", "Error loading from Firestore", e)
            }
    }

    private fun getDefaultMedicines(): List<String> {
        return listOf(
            "Paracetamol", "Ibuprofen", "Amoxicillin", "Vitamin C", "Aspirin",
            "Metformin", "Atorvastatin", "Lisinopril", "Levothyroxine", "Albuterol"
        )
    }

    private fun performSearch(medicineName: String) {
        Log.d("Search", "Performing search for: $medicineName")
        Toast.makeText(this, "Searching for: $medicineName", Toast.LENGTH_SHORT).show()

        // Store search query in ViewModel
        sharedViewModel.searchQuery = medicineName
        Log.d("Search", "Saved to ViewModel: $medicineName")

        // Navigate to MapFragment
        replaceFragment(MapFragment())

        // Switch to map tab
        findViewById<BottomNavigationView>(R.id.bottomNavigationView).selectedItemId = R.id.map

        // Clear search
        findViewById<SearchView>(R.id.search).setQuery("", false)
        findViewById<SearchView>(R.id.search).clearFocus()
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.customer_nav_host, fragment)
            .commitNow() // Use commitNow to execute immediately
    }
}