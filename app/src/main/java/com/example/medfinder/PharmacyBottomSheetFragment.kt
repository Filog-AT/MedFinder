package com.example.medfinder

import Medicine
import android.app.Dialog
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import java.text.DecimalFormat

class PharmacyBottomSheetFragment : DialogFragment() {

    private lateinit var pharmacyData: MapFragment.PharmacyData
    private val db = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "PharmacyBottomSheet"

        fun newInstance(pharmacyData: MapFragment.PharmacyData): PharmacyBottomSheetFragment {
            val fragment = PharmacyBottomSheetFragment()
            val args = Bundle()
            args.putSerializable("pharmacy_data", pharmacyData)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), R.style.BottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.pharmacy_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get pharmacy data
        pharmacyData = arguments?.getSerializable("pharmacy_data") as MapFragment.PharmacyData

        setupUI(view)

        // Make dialog appear from bottom
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog?.window?.setGravity(android.view.Gravity.BOTTOM)
    }

    private fun setupUI(view: View) {
        // Find ALL views using findViewById
        val tvPharmacyName = view.findViewById<TextView>(R.id.tv_pharmacy_name)
        val tvDistance = view.findViewById<TextView>(R.id.tv_distance)
        val tvStatus = view.findViewById<TextView>(R.id.tv_status)
        val btnDirections = view.findViewById<Button>(R.id.btn_directions)
        val expandedContent = view.findViewById<ScrollView>(R.id.expanded_content)
        val headerLayout = view.findViewById<LinearLayout>(R.id.header_layout)
        val rvMedicines = view.findViewById<RecyclerView>(R.id.rv_medicines)
        val tvReservationInfo = view.findViewById<TextView>(R.id.tv_reservation_info)
        val btnSelectAll = view.findViewById<Button>(R.id.btn_select_all)
        val btnReserve = view.findViewById<Button>(R.id.btn_reserve)

        // Try to find optional views (they might not exist)
        val layoutSelectedSummary = view.findViewById<LinearLayout?>(R.id.layout_selected_summary)
        val tvSelectedCount = view.findViewById<TextView?>(R.id.tv_selected_count)
        val tvTotalPrice = view.findViewById<TextView?>(R.id.tv_total_price)

        // Set pharmacy info
        tvPharmacyName.text = pharmacyData.name

        val decimalFormat = DecimalFormat("#.#")
        val distance = if (pharmacyData.distance > 0) {
            "${decimalFormat.format(pharmacyData.distance)} km"
        } else {
            "Distance unknown"
        }
        tvDistance.text = distance

        // Set status based on medicine search
        if (pharmacyData.medicineName.isNotEmpty()) {
            val status = if (pharmacyData.hasMedicine) {
                "✅ Has ${pharmacyData.medicineName}"
            } else {
                "❌ No ${pharmacyData.medicineName}"
            }
            tvStatus.text = status
            tvStatus.visibility = View.VISIBLE
        } else {
            tvStatus.visibility = View.GONE
        }

        // Setup directions button - TWO OPTIONS:
        btnDirections.setOnClickListener {
            // Option 1: Open internal directions (recommended)
            openInternalDirections()

            // Option 2: Uncomment below to use Google Maps
            // openDirectionsToPharmacy()
        }

        // Setup header click to expand/collapse
        headerLayout.setOnClickListener {
            if (expandedContent.visibility == View.VISIBLE) {
                expandedContent.visibility = View.GONE
            } else {
                expandedContent.visibility = View.VISIBLE
                loadPharmacyMedicines(rvMedicines, tvReservationInfo)
            }
        }

        // Setup reservation buttons
        btnSelectAll.setOnClickListener {
            Toast.makeText(requireContext(), "Select All - To be implemented", Toast.LENGTH_SHORT).show()
        }

        btnReserve.setOnClickListener {
            Toast.makeText(requireContext(), "Reserve - To be implemented", Toast.LENGTH_SHORT).show()
        }

        // Initialize optional views if they exist
        layoutSelectedSummary?.visibility = View.GONE
        tvSelectedCount?.text = "Selected: 0 items"
        tvTotalPrice?.text = "Total: ₱0"
    }

    private fun loadPharmacyMedicines(recyclerView: RecyclerView, infoTextView: TextView) {
        db.collection("Pharmacies")
            .document(pharmacyData.id)
            .collection("Medicines")
            .get()
            .addOnSuccessListener { result ->
                val medicinesList = mutableListOf<Medicine>()
                for (document in result) {
                    val medicine = document.toObject(Medicine::class.java)
                    medicine.id = document.id
                    medicinesList.add(medicine)
                }

                if (medicinesList.isEmpty()) {
                    infoTextView.text = "No medicines available at this pharmacy"
                    recyclerView.visibility = View.GONE
                } else {
                    infoTextView.text = "Available medicines: ${medicinesList.size}"
                    recyclerView.layoutManager = LinearLayoutManager(requireContext())
                    recyclerView.adapter = SimpleMedicineAdapter(medicinesList)
                    recyclerView.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading medicines", e)
                infoTextView.text = "Error loading medicines"
            }
    }

    // NEW FUNCTION: Open internal directions
    private fun openInternalDirections() {
        // Always get location directly - simplest solution
        getFreshLocation()
    }

    private fun getFreshLocation() {
        Log.d(TAG, "getFreshLocation() called")

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // Check if we have location permission
        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.app.ActivityCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Location permission NOT granted")
            requestLocationPermission()
            return
        }

        Log.d(TAG, "Location permission granted, getting last location")

        // Try to get last known location
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            Log.d(TAG, "Last location received: $location")
            if (location != null) {
                Log.d(TAG, "We have location: ${location.latitude}, ${location.longitude}")
                // We have location, show directions
                showDirectionFragment(location)
            } else {
                Log.d(TAG, "No last location available, requesting fresh location")
                // No last location, request a fresh one
                requestFreshLocation()
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Error getting location: ${e.message}", e)
            Toast.makeText(requireContext(),
                "Error getting location",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun requestFreshLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // Check permission again before requesting updates
        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.app.ActivityCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(),
                "Location permission required",
                Toast.LENGTH_LONG).show()
            return
        }

        val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
            priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
            numUpdates = 1
            interval = 10000
            fastestInterval = 5000
        }

        val locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                locationResult.lastLocation?.let { location ->
                    showDirectionFragment(location)
                } ?: run {
                    Toast.makeText(requireContext(),
                        "Could not get location. Please try again.",
                        Toast.LENGTH_LONG).show()
                }
                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        // This is safe now because we checked permissions
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            android.os.Looper.getMainLooper()
        )

        Toast.makeText(requireContext(),
            "Getting your current location...",
            Toast.LENGTH_SHORT).show()
    }

    private fun requestLocationPermission() {
        val permissions = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Check if we should show explanation
        if (androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                requireActivity(),
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )) {
            // Show explanation dialog
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Location Permission Needed")
                .setMessage("This app needs location permission to show directions to the pharmacy.")
                .setPositiveButton("OK") { _, _ ->
                    // Request permission after explanation
                    androidx.core.app.ActivityCompat.requestPermissions(
                        requireActivity(),
                        permissions,
                        1001
                    )
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    Toast.makeText(requireContext(),
                        "Location permission denied. Cannot show directions.",
                        Toast.LENGTH_LONG).show()
                }
                .create()
                .show()
        } else {
            // Request permission directly
            androidx.core.app.ActivityCompat.requestPermissions(
                requireActivity(),
                permissions,
                1001
            )
        }
    }

    // Handle permission result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Permission granted, try to get location again
                getFreshLocation()
            } else {
                Toast.makeText(requireContext(),
                    "Location permission denied. Cannot show directions.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDirectionFragment(location: android.location.Location) {
        // Start NavigationActivity with primitive types
        try {
            NavigationActivity.startNavigation(
                requireContext(),
                pharmacyData.id,
                pharmacyData.name,
                pharmacyData.location.latitude,
                pharmacyData.location.longitude,
                location
            )
            dismiss()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting navigation: ${e.message}", e)
            Toast.makeText(requireContext(),
                "Error starting navigation",
                Toast.LENGTH_SHORT).show()
        }
    }
}