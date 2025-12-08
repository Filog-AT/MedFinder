package com.example.medfinder

import Medicine
import android.app.Dialog
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.FirebaseFirestore
import java.text.DecimalFormat
import java.util.*
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import android.location.Location as AndroidLocation
import androidx.core.app.ActivityCompat

class PharmacyBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var pharmacyData: MapFragment.PharmacyData
    private val db = FirebaseFirestore.getInstance()
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>
    private lateinit var bottomSheet: View
    private lateinit var nonPrescriptionAdapter: ReservationMedicineAdapter
    private lateinit var prescriptionAdapter: ReservationMedicineAdapter
    private lateinit var simpleMedicineAdapter: SimpleMedicineAdapter
    private var allMedicinesList = mutableListOf<Medicine>()
    private var nonPrescriptionMeds = mutableListOf<Medicine>()
    private var prescriptionMeds = mutableListOf<Medicine>()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
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

        pharmacyData = arguments?.getSerializable("pharmacy_data") as MapFragment.PharmacyData
        setupUI(view)
        setupBottomSheetBehavior(view)
        loadPharmacyMedicines()
    }

    private fun setupUI(view: View) {
        // Find views
        val tvPharmacyName = view.findViewById<TextView>(R.id.tv_pharmacy_name)
        val tvDistance = view.findViewById<TextView>(R.id.tv_distance)
        val tvStatus = view.findViewById<TextView>(R.id.tv_status)
        val btnDirections = view.findViewById<Button>(R.id.btn_directions)
        val dragHandleContainer = view.findViewById<LinearLayout>(R.id.drag_handle_container)
        val headerLayout = view.findViewById<LinearLayout>(R.id.header_layout)
        val mainTabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
        val categoryTabLayout = view.findViewById<TabLayout>(R.id.med_category_tabs)
        val inventorySection = view.findViewById<LinearLayout>(R.id.inventory_section)
        val reservationSection = view.findViewById<LinearLayout>(R.id.reservation_section)
        val rvInventory = view.findViewById<RecyclerView>(R.id.rv_inventory)
        val rvNonPrescription = view.findViewById<RecyclerView>(R.id.rv_non_prescription)
        val rvPrescription = view.findViewById<RecyclerView>(R.id.rv_prescription)
        val tvInventoryInfo = view.findViewById<TextView>(R.id.tv_inventory_info)
        val tvReservationInfo = view.findViewById<TextView>(R.id.tv_reservation_info)
        val btnReserve = view.findViewById<Button>(R.id.btn_reserve)
        val layoutSelectedSummary = view.findViewById<LinearLayout>(R.id.layout_selected_summary)
        val tvSelectedCount = view.findViewById<TextView>(R.id.tv_selected_count)
        val tvTotalPrice = view.findViewById<TextView>(R.id.tv_total_price)
        val tvPrescriptionReminder = view.findViewById<TextView>(R.id.tv_prescription_reminder)

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

        // Setup directions button
        btnDirections.setOnClickListener {
            openInternalDirections()
        }

        // Setup drag handle and header
        dragHandleContainer.setOnClickListener {
            toggleBottomSheet()
        }

        headerLayout.setOnClickListener {
            toggleBottomSheet()
        }

        // Setup main tab layout (Inventory/Reservation)
        mainTabLayout.addTab(mainTabLayout.newTab().setText("Inventory"))
        mainTabLayout.addTab(mainTabLayout.newTab().setText("Reservation"))

        // Setup category tabs (Non-Prescription/Prescription)
        categoryTabLayout.addTab(categoryTabLayout.newTab().setText("Non-Prescription"))
        categoryTabLayout.addTab(categoryTabLayout.newTab().setText("Prescription"))

        // Setup RecyclerViews
        rvInventory.layoutManager = LinearLayoutManager(requireContext())
        rvNonPrescription.layoutManager = LinearLayoutManager(requireContext())
        rvPrescription.layoutManager = LinearLayoutManager(requireContext())

        // Show initial content
        showInventorySection(inventorySection, reservationSection)

        // Main tab listener
        mainTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // Inventory tab
                        showInventorySection(inventorySection, reservationSection)
                    }
                    1 -> { // Reservation tab
                        showReservationSection(inventorySection, reservationSection)
                        updateSelectedSummary(layoutSelectedSummary, tvSelectedCount, tvTotalPrice, tvPrescriptionReminder)
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Medicine category tab listener
        categoryTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // Non-Prescription tab
                        rvNonPrescription.visibility = View.VISIBLE
                        rvPrescription.visibility = View.GONE
                        tvReservationInfo.text = "Non-prescription medicines: ${nonPrescriptionMeds.size} available"
                    }
                    1 -> { // Prescription tab
                        rvNonPrescription.visibility = View.GONE
                        rvPrescription.visibility = View.VISIBLE
                        tvReservationInfo.text = "Prescription medicines: ${prescriptionMeds.size} available"
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Setup Reserve button
        btnReserve.setOnClickListener {
            createReservation(layoutSelectedSummary, tvSelectedCount, tvTotalPrice, tvPrescriptionReminder)
        }

        // Initialize selected summary
        layoutSelectedSummary.visibility = View.GONE
        tvSelectedCount.text = "Selected: 0 items"
        tvTotalPrice.text = "Total: ₱0"
        tvPrescriptionReminder.visibility = View.GONE
    }

    private fun updateSelectedSummary(
        summaryLayout: LinearLayout,
        selectedCountText: TextView,
        totalPriceText: TextView,
        prescriptionReminderText: TextView
    ) {
        // Get selected medicines from BOTH adapters
        val nonPrescriptionSelected = nonPrescriptionAdapter.getSelectedMedicines()
        val prescriptionSelected = prescriptionAdapter.getSelectedMedicines()
        val allSelectedMedicines = nonPrescriptionSelected + prescriptionSelected

        if (allSelectedMedicines.isNotEmpty()) {
            val totalItems = allSelectedMedicines.sumOf { it.second }
            val totalPrice = allSelectedMedicines.sumOf { (medicine, quantity) ->
                medicine.price * quantity
            }

            val hasPrescriptionMeds = prescriptionSelected.isNotEmpty()

            selectedCountText.text = "Selected: $totalItems item(s)"
            totalPriceText.text = "Total: ₱$totalPrice"
            summaryLayout.visibility = View.VISIBLE

            // Show prescription reminder if any prescription medicines are selected
            if (hasPrescriptionMeds) {
                prescriptionReminderText.visibility = View.VISIBLE
            } else {
                prescriptionReminderText.visibility = View.GONE
            }
        } else {
            summaryLayout.visibility = View.GONE
            prescriptionReminderText.visibility = View.GONE
        }
    }

    private fun createReservation(
        summaryLayout: LinearLayout,
        selectedCountText: TextView,
        totalPriceText: TextView,
        prescriptionReminderText: TextView
    ) {
        // Get selected medicines from BOTH adapters
        val nonPrescriptionSelected = nonPrescriptionAdapter.getSelectedMedicines()
        val prescriptionSelected = prescriptionAdapter.getSelectedMedicines()
        val allSelectedMedicines = nonPrescriptionSelected + prescriptionSelected

        if (allSelectedMedicines.isEmpty()) {
            Toast.makeText(requireContext(), "Please select at least one medicine", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if user is logged in as customer
        if (!LoginUtils.isUserLoggedIn(requireContext())) {
            LoginUtils.redirectToLogin(requireContext(), "Please login to reserve medicines")
            return
        }

        if (!LoginUtils.isCustomer(requireContext())) {
            Toast.makeText(requireContext(),
                "Only customers can make reservations",
                Toast.LENGTH_LONG).show()
            return
        }

        val userId = LoginUtils.getCurrentUserId(requireContext())
        if (userId == null) {
            Toast.makeText(requireContext(), "User not found. Please login again.", Toast.LENGTH_SHORT).show()
            return
        }

        // Calculate total price and check for prescription medicines
        val totalPrice = allSelectedMedicines.sumOf { (medicine, quantity) ->
            medicine.price * quantity
        }

        // Check if any selected medicines require prescription
        val hasPrescriptionMeds = prescriptionSelected.isNotEmpty()

        // Create reservation object
        val reservation = Reservation(
            user_id = userId,
            pharmacy_id = pharmacyData.id,
            medicines = allSelectedMedicines.map { (medicine, quantity) ->
                MedicineItem(
                    medicine_id = medicine.id ?: "",
                    medicine_name = medicine.medicine_name,
                    quantity = quantity,
                    price = medicine.price,
                    requires_prescription = medicine.requires_prescription
                )
            },
            status = "pending",
            total_price = totalPrice,
            created_at = System.currentTimeMillis(),
            has_prescription_meds = hasPrescriptionMeds
        )

        // Save to Firestore
        db.collection("Reservations")
            .add(reservation)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Reservation created with ID: ${documentReference.id}")

                // Update medicine stock
                updateMedicineStock(allSelectedMedicines)

                // Clear selections from both adapters
                nonPrescriptionAdapter.clearAllSelections()
                prescriptionAdapter.clearAllSelections()
                summaryLayout.visibility = View.GONE
                prescriptionReminderText.visibility = View.GONE

                // Show appropriate message
                val message = if (hasPrescriptionMeds) {
                    "Reservation created! Remember to bring your prescription(s) when claiming."
                } else {
                    "Reservation created successfully!"
                }

                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

                // Refresh the lists to update stock
                loadPharmacyMedicines()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error creating reservation", e)
                Toast.makeText(requireContext(),
                    "Error creating reservation: ${e.message}",
                    Toast.LENGTH_LONG).show()
            }
    }

    private fun updateMedicineStock(selectedMedicines: List<Pair<Medicine, Int>>) {
        val batch = db.batch()

        selectedMedicines.forEach { (medicine, quantity) ->
            val medicineId = medicine.id
            if (medicineId != null) {
                val medicineRef = db.collection("Pharmacies")
                    .document(pharmacyData.id)
                    .collection("Medicines")
                    .document(medicineId)

                val newStock = medicine.stock - quantity
                batch.update(medicineRef, "stock", newStock)
            }
        }

        batch.commit()
            .addOnSuccessListener {
                Log.d(TAG, "Medicine stock updated successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating medicine stock", e)
            }
    }

    private fun showInventorySection(inventorySection: LinearLayout, reservationSection: LinearLayout) {
        inventorySection.visibility = View.VISIBLE
        reservationSection.visibility = View.GONE
    }

    private fun showReservationSection(inventorySection: LinearLayout, reservationSection: LinearLayout) {
        inventorySection.visibility = View.GONE
        reservationSection.visibility = View.VISIBLE
    }

    private fun loadPharmacyMedicines() {
        db.collection("Pharmacies")
            .document(pharmacyData.id)
            .collection("Medicines")
            .get()
            .addOnSuccessListener { result ->
                allMedicinesList.clear()
                nonPrescriptionMeds.clear()
                prescriptionMeds.clear()

                for (document in result) {
                    val medicine = document.toObject(Medicine::class.java)
                    medicine.id = document.id
                    allMedicinesList.add(medicine)

                    // Separate into prescription and non-prescription
                    if (medicine.requires_prescription) {
                        prescriptionMeds.add(medicine)
                    } else {
                        nonPrescriptionMeds.add(medicine)
                    }
                }

                updateAdapters()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading medicines", e)
                view?.findViewById<TextView>(R.id.tv_inventory_info)?.text = "Error loading medicines"
                view?.findViewById<TextView>(R.id.tv_reservation_info)?.text = "Error loading medicines"
            }
    }


    private fun updateAdapters() {
        val view = requireView()
        val tvInventoryInfo = view.findViewById<TextView>(R.id.tv_inventory_info)
        val tvReservationInfo = view.findViewById<TextView>(R.id.tv_reservation_info)
        val rvInventory = view.findViewById<RecyclerView>(R.id.rv_inventory)
        val rvNonPrescription = view.findViewById<RecyclerView>(R.id.rv_non_prescription)
        val rvPrescription = view.findViewById<RecyclerView>(R.id.rv_prescription)
        val categoryTabLayout = view.findViewById<TabLayout>(R.id.med_category_tabs)

        if (allMedicinesList.isEmpty()) {
            tvInventoryInfo.text = "No medicines available at this pharmacy"
            tvReservationInfo.text = "No medicines available for reservation"
            rvInventory.visibility = View.GONE
            rvNonPrescription.visibility = View.GONE
            rvPrescription.visibility = View.GONE
        } else {
            tvInventoryInfo.text = "Available medicines: ${allMedicinesList.size}"

            // Update category tabs count
            val nonPrescriptionTab = categoryTabLayout.getTabAt(0)
            val prescriptionTab = categoryTabLayout.getTabAt(1)

            nonPrescriptionTab?.text = "Non-Prescription (${nonPrescriptionMeds.size})"
            prescriptionTab?.text = "Prescription (${prescriptionMeds.size})"

            // Set initial reservation info
            tvReservationInfo.text = "Non-prescription medicines: ${nonPrescriptionMeds.size} available"

            // Inventory tab: Simple adapter (view only)
            simpleMedicineAdapter = SimpleMedicineAdapter(allMedicinesList)
            rvInventory.adapter = simpleMedicineAdapter
            rvInventory.visibility = View.VISIBLE

            // Reservation tabs: Separate adapters
            val canReserve = LoginUtils.isCustomer(requireContext())

            // Non-prescription adapter
            nonPrescriptionAdapter = ReservationMedicineAdapter(nonPrescriptionMeds, canReserve)
            rvNonPrescription.adapter = nonPrescriptionAdapter
            rvNonPrescription.visibility = View.VISIBLE

            // Prescription adapter
            prescriptionAdapter = ReservationMedicineAdapter(prescriptionMeds, canReserve)
            rvPrescription.adapter = prescriptionAdapter
            rvPrescription.visibility = View.GONE // Hidden initially
        }
    }


    private fun loadPharmacyReservations(
        recyclerView: RecyclerView,
        infoTextView: TextView,
        noReservationsText: TextView
    ) {
        // Check if current user is pharmacy owner
        val currentUserId = LoginUtils.getCurrentUserId(requireContext())
        val isPharmacyOwner = LoginUtils.isPharmacy(requireContext())

        if (currentUserId == null) {
            infoTextView.text = "Please login to view reservations"
            recyclerView.visibility = View.GONE
            noReservationsText.visibility = View.VISIBLE
            noReservationsText.text = "Please login to view reservations"
            return
        }

        if (!isPharmacyOwner) {
            infoTextView.text = "Only pharmacy owners can view reservations"
            recyclerView.visibility = View.GONE
            noReservationsText.visibility = View.VISIBLE
            noReservationsText.text = "Only pharmacy owners can view reservations"
            return
        }

        // First, check if user is the owner of this pharmacy
        db.collection("Pharmacies")
            .document(pharmacyData.id)
            .get()
            .addOnSuccessListener { pharmacyDoc ->
                if (pharmacyDoc.exists()) {
                    val ownerId = pharmacyDoc.getString("owner_id")

                    if (ownerId != currentUserId) {
                        infoTextView.text = "You are not the owner of this pharmacy"
                        recyclerView.visibility = View.GONE
                        noReservationsText.visibility = View.VISIBLE
                        noReservationsText.text = "You are not the owner of this pharmacy"
                        return@addOnSuccessListener
                    }

                    // User is the owner, load reservations
                    loadReservationsForPharmacy(recyclerView, infoTextView, noReservationsText)
                } else {
                    infoTextView.text = "Pharmacy not found"
                    recyclerView.visibility = View.GONE
                    noReservationsText.visibility = View.VISIBLE
                    noReservationsText.text = "Pharmacy not found"
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking pharmacy ownership", e)
                infoTextView.text = "Error checking permissions"
                recyclerView.visibility = View.GONE
                noReservationsText.visibility = View.VISIBLE
                noReservationsText.text = "Error checking permissions"
            }
    }

    private fun loadReservationsForPharmacy(
        recyclerView: RecyclerView,
        infoTextView: TextView,
        noReservationsText: TextView
    ) {
        // Load reservations for this pharmacy
        db.collection("Reservations")
            .whereEqualTo("pharmacy_id", pharmacyData.id)
            .whereEqualTo("status", "pending")
            .orderBy("created_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                Log.d(TAG, "Found ${result.size()} pending reservations")

                val reservationsList = mutableListOf<Reservation>()
                for (document in result) {
                    try {
                        val reservation = document.toObject(Reservation::class.java)
                        reservation.id = document.id
                        reservationsList.add(reservation)
                        Log.d(TAG, "Reservation added: ${reservation.id}, Status: ${reservation.status}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing reservation document: ${e.message}")
                    }
                }

                if (reservationsList.isEmpty()) {
                    infoTextView.text = "No pending reservations"
                    recyclerView.visibility = View.GONE
                    noReservationsText.visibility = View.VISIBLE
                    noReservationsText.text = "No pending reservations found"
                } else {
                    infoTextView.text = "Pending reservations: ${reservationsList.size}"
                    recyclerView.layoutManager = LinearLayoutManager(requireContext())

                    // Use the ReservationItemAdapter
                    recyclerView.adapter = ReservationItemAdapter(reservationsList) { reservationId, newStatus ->
                        updateReservationStatus(reservationId, newStatus, recyclerView, infoTextView, noReservationsText)
                    }
                    recyclerView.visibility = View.VISIBLE
                    noReservationsText.visibility = View.GONE

                    Log.d(TAG, "Loaded ${reservationsList.size} reservations into RecyclerView")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading reservations", e)
                infoTextView.text = "Error loading reservations"
                recyclerView.visibility = View.GONE
                noReservationsText.visibility = View.VISIBLE
                noReservationsText.text = "Error loading reservations: ${e.message}"
            }
    }

    private fun updateReservationStatus(
        reservationId: String,
        newStatus: String,
        recyclerView: RecyclerView,
        infoTextView: TextView,
        noReservationsText: TextView
    ) {
        db.collection("Reservations")
            .document(reservationId)
            .update("status", newStatus)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Reservation $newStatus", Toast.LENGTH_SHORT).show()
                // Refresh the reservations list
                loadReservationsForPharmacy(recyclerView, infoTextView, noReservationsText)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating reservation", e)
                Toast.makeText(requireContext(), "Error updating reservation", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupBottomSheetBehavior(view: View) {
        bottomSheet = view.findViewById(R.id.bottom_sheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        bottomSheetBehavior.peekHeight = 400
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.isFitToContents = false
        bottomSheetBehavior.skipCollapsed = false

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        Log.d(TAG, "Bottom Sheet: EXPANDED")
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        Log.d(TAG, "Bottom Sheet: COLLAPSED")
                    }
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        dismiss()
                    }
                    else -> {}
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }


    private fun toggleBottomSheet() {
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        } else {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun openInternalDirections() {
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
            // Request permission if not granted
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1001
            )
            Toast.makeText(requireContext(), "Please grant location permission", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Location permission granted, getting last location")

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: android.location.Location? ->
                if (location != null) {
                    Log.d(TAG, "Location obtained: ${location.latitude}, ${location.longitude}")
                    // Start navigation with obtained location
                    startNavigationActivity(location)
                } else {
                    Log.d(TAG, "No last known location, requesting location updates")
                    requestLocationUpdates()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting location", e)
                Toast.makeText(requireContext(), "Failed to get location", Toast.LENGTH_SHORT).show()
            }
    }
    private fun requestLocationUpdates() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
            priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 10000
            fastestInterval = 5000
        }

        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.app.ActivityCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        Log.d(TAG, "Location update received: ${location.latitude}, ${location.longitude}")
                        fusedLocationClient.removeLocationUpdates(this)
                        startNavigationActivity(location)
                    }
                }
            },
            null
        )
    }

    private fun startNavigationActivity(userLocation: android.location.Location) {
        Log.d(TAG, "Starting navigation activity")
        Log.d(TAG, "Pharmacy: ${pharmacyData.name}")
        Log.d(TAG, "Pharmacy location GeoPoint: ${pharmacyData.location}")
        Log.d(TAG, "User location: ${userLocation.latitude}, ${userLocation.longitude}")

        // Get latitude and longitude from the GeoPoint
        val destinationLat = pharmacyData.location.latitude
        val destinationLng = pharmacyData.location.longitude

        Log.d(TAG, "Destination coordinates: $destinationLat, $destinationLng")

        // Check if we have valid coordinates
        if (destinationLat == 0.0 || destinationLng == 0.0) {
            Toast.makeText(requireContext(), "Invalid pharmacy location", Toast.LENGTH_SHORT).show()
            return
        }

        // Start NavigationActivity
        NavigationActivity.startNavigation(
            requireContext(),
            pharmacyData.id,
            pharmacyData.name,
            destinationLat,
            destinationLng,
            userLocation
        )

        // Close the bottom sheet
        dismiss()
    }
}