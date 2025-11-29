package com.example.unimapsmartbustracker

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class AdminAssignBusDriverRouteActivity : AppCompatActivity() {

    private lateinit var chooseDateButton: Button
    private lateinit var chooseDriverButton: Button
    private lateinit var chooseTimeButton: Button
    private lateinit var chooseRouteButton: Button
    private lateinit var chooseBusNumberButton: Button // New: Bus Number Button
    private lateinit var saveButton: Button
    private lateinit var database: DatabaseReference
    private val TAG = "AdminAssignRouteActivity"
    private var selectedDate: Calendar = Calendar.getInstance()
    private var selectedDriverId: String? = null
    private var selectedDriverName: String? = null
    private var selectedTime: String? = null
    private var selectedRoute: String? = null
    private var selectedBusNumber: Int? = null // New: Variable to store selected bus number

    // Define time window constants consistently across activities
    // These define when a "pending" route should transition to "expired"
    private val PROXIMITY_MINUTES_BEFORE_START = 20 // Same as in BusDriverStartRouteActivity.kt
    private val FLEXIBILITY_MINUTES_AFTER_START = 5  // Same as in BusDriverStartRouteActivity.kt


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adminassignbusdriverroute)

        // Initialize Firebase
        database = Firebase.database.reference

        // Initialize UI elements by their correct IDs from the layout file
        chooseDateButton = findViewById(R.id.chooseDateButton)
        chooseDriverButton = findViewById(R.id.chooseDriverButton)
        chooseTimeButton = findViewById(R.id.chooseTimeButton) // Correct ID for "Choose Time"
        chooseRouteButton = findViewById(R.id.chooseRouteButton)
        chooseBusNumberButton = findViewById(R.id.chooseBusNumberButton) // Correct ID for "Choose Bus Number"
        saveButton = findViewById(R.id.saveButton)

        // Set click listener for the "Choose Date" Button
        chooseDateButton.setOnClickListener {
            showDatePickerDialog()
        }

        // Set click listener for the "Choose Bus Driver" Button
        chooseDriverButton.setOnClickListener {
            showDriverListDialog()
        }

        // Set click listener for the "Choose Time" Button
        chooseTimeButton.setOnClickListener {
            showTimeSelectionDialog()
        }

        // Set click listener for the "Choose Route" Button
        chooseRouteButton.setOnClickListener {
            showRouteSelectionDialog()
        }

        // Set click listener for the "Choose Bus Number" Button
        chooseBusNumberButton.setOnClickListener {
            showBusNumberSelectionDialog() // This method will now check for availability
        }

        // Set click listener for the "Save" Button
        saveButton.setOnClickListener {
            saveAssignment()
        }

        // Update button texts on creation
        updateDateButtonText()
        updateDriverButtonText()
        updateTimeButtonText()
        updateRouteButtonText()
        updateBusNumberButtonText()

        // Call the cleanup function when the activity is created to mark expired pending routes
        updateExpiredPendingAssignments()
    }

    private fun showDatePickerDialog() {
        val c = Calendar.getInstance()
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, monthOfYear, dayOfMonth ->
                selectedDate.set(year, monthOfYear, dayOfMonth)
                updateDateButtonText()
                // Reset bus number and time selections if date changes to avoid conflicts
                selectedBusNumber = null
                selectedTime = null
                updateBusNumberButtonText()
                updateTimeButtonText()
            },
            year,
            month,
            day
        )
        // Set minimum date to today so admins cannot assign routes in the past
        datePickerDialog.datePicker.minDate = c.timeInMillis
        datePickerDialog.show()
    }

    private fun updateDateButtonText() {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        chooseDateButton.text = "Date: ${dateFormat.format(selectedDate.time)}"
    }

    private fun showDriverListDialog() {
        val driversList = ArrayList<BusDriver>()
        val driverNames = ArrayList<String>()

        database.child("drivers")
            .orderByChild("role")
            .equalTo("Driver")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    driversList.clear()
                    driverNames.clear()
                    for (driverSnapshot in snapshot.children) {
                        val driver = driverSnapshot.getValue(BusDriver::class.java)
                        driver?.let {
                            driversList.add(it)
                            driverNames.add(it.name)
                        }
                    }

                    if (driverNames.isNotEmpty()) {
                        val builder = AlertDialog.Builder(this@AdminAssignBusDriverRouteActivity)
                        builder.setTitle("Choose Bus Driver")
                            .setItems(driverNames.toTypedArray()) { _, which ->
                                val selectedDriver = driversList[which]
                                selectedDriverId = selectedDriver.idNumber
                                selectedDriverName = selectedDriver.name
                                updateDriverButtonText()
                                Log.d(TAG, "Selected Driver ID: $selectedDriverId, Name: $selectedDriverName")
                            }
                            .create()
                            .show()
                    } else {
                        Toast.makeText(
                            this@AdminAssignBusDriverRouteActivity,
                            "No drivers available.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to fetch drivers: ${error.message}")
                    Toast.makeText(
                        this@AdminAssignBusDriverRouteActivity,
                        "Failed to load drivers.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun updateDriverButtonText() {
        chooseDriverButton.text = if (selectedDriverName.isNullOrEmpty()) {
            "Choose Bus Driver"
        } else {
            "Driver: $selectedDriverName"
        }
    }

    private fun showTimeSelectionDialog() {
        val timeSlots = ArrayList<String>()
        var hour = 7
        var minute = 30

        val currentTime = Calendar.getInstance()
        val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
        val currentMinute = currentTime.get(Calendar.MINUTE)

        // Check if the selected date is today
        val isToday = selectedDate.get(Calendar.YEAR) == currentTime.get(Calendar.YEAR) &&
                selectedDate.get(Calendar.MONTH) == currentTime.get(Calendar.MONTH) &&
                selectedDate.get(Calendar.DAY_OF_MONTH) == currentTime.get(Calendar.DAY_OF_MONTH)

        while (hour <= 19) {
            // Adjust minute to be 0 or 30
            val adjustedMinute = if (minute < 30) 0 else 30

            // Create a temporary calendar for this time slot
            val tempCalendar = Calendar.getInstance().apply {
                time = selectedDate.time // Use the selected date
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, adjustedMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Only add time slots that are in the future if the selected date is today
            if (!isToday || tempCalendar.timeInMillis > currentTime.timeInMillis) {
                val ampm = if (hour < 12) "AM" else "PM"
                val displayHour = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
                val timeString = String.format("%02d:%02d %s", displayHour, adjustedMinute, ampm)
                timeSlots.add(timeString)
            }

            // Increment for the next slot
            minute += 30
            if (minute == 60) {
                minute = 0
                hour++
            }
        }

        // If no future time slots are available for today
        if (isToday && timeSlots.isEmpty()) {
            Toast.makeText(this, "No future time slots available for today. Please select another date.", Toast.LENGTH_LONG).show()
            return
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Choose Time")
            .setItems(timeSlots.toTypedArray()) { _, which ->
                selectedTime = timeSlots[which]
                updateTimeButtonText()
                Log.d(TAG, "Selected Time: $selectedTime")
                // Reset bus number selection if time changes
                selectedBusNumber = null
                updateBusNumberButtonText()
            }
            .create()
            .show()
    }

    private fun updateTimeButtonText() {
        chooseTimeButton.text = if (selectedTime.isNullOrEmpty()) {
            "Choose Time"
        } else {
            "Time: $selectedTime"
        }
    }

    private fun showRouteSelectionDialog() {
        val routes = arrayOf("Route A", "Route B")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Choose Route")
            .setItems(routes) { _, which ->
                selectedRoute = routes[which]
                updateRouteButtonText()
                Log.d(TAG, "Selected Route: $selectedRoute")
            }
            .create()
            .show()
    }

    private fun updateRouteButtonText() {
        chooseRouteButton.text = if (selectedRoute.isNullOrEmpty()) {
            "Choose Route"
        } else {
            "Route: $selectedRoute"
        }
    }

    /**
     * Shows a dialog to select a bus number from 1 to 40.
     * This method now filters out bus numbers that are already assigned
     * for the currently selected date and time.
     */
    private fun showBusNumberSelectionDialog() {
        // Ensure date and time are selected before proceeding
        if (selectedDate == null || selectedTime == null) {
            Toast.makeText(this, "Please choose a Date and Time first.", Toast.LENGTH_SHORT).show()
            return
        }

        val yearString = SimpleDateFormat("yyyy", Locale.getDefault()).format(selectedDate.time)
        val monthString = SimpleDateFormat("MM", Locale.getDefault()).format(selectedDate.time)
        val dayString = SimpleDateFormat("dd", Locale.getDefault()).format(selectedDate.time)

        // Reference to the assignments for the specific date
        val assignmentsRef = database.child("assignments").child(yearString).child(monthString).child(dayString)

        assignmentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val assignedBusNumbersForDateTime = mutableSetOf<Int>()
                for (assignmentSnapshot in snapshot.children) {
                    val assignmentTime = assignmentSnapshot.child("time").getValue(String::class.java)
                    val busNumber = assignmentSnapshot.child("busNumber").getValue(Int::class.java)
                    val status = assignmentSnapshot.child("status").getValue(String::class.java) ?: "pending"

                    // Check if the assignment matches the selected time and has a bus number,
                    // AND if it's not completed or cancelled.
                    // This ensures bus numbers are only considered "unavailable" if the assignment is still active/pending.
                    if (assignmentTime == selectedTime && busNumber != null && (status == "pending" || status == "in_progress")) {
                        assignedBusNumbersForDateTime.add(busNumber)
                    }
                }

                // Generate a list of all possible bus numbers (1 to 40)
                val allBusNumbers = (1..40).toSet()

                // Filter out the assigned bus numbers to get available ones
                val availableBusNumbers = (allBusNumbers - assignedBusNumbersForDateTime).sorted()

                if (availableBusNumbers.isEmpty()) {
                    Toast.makeText(this@AdminAssignBusDriverRouteActivity, "No bus numbers available for this date and time. Please choose another date or time.", Toast.LENGTH_LONG).show()
                    return
                }

                val busNumberStrings = availableBusNumbers.map { it.toString() }.toTypedArray()

                val builder = AlertDialog.Builder(this@AdminAssignBusDriverRouteActivity)
                builder.setTitle("Choose Bus Number")
                    .setItems(busNumberStrings) { _, which ->
                        selectedBusNumber = availableBusNumbers[which]
                        updateBusNumberButtonText()
                        Log.d(TAG, "Selected Bus Number: $selectedBusNumber")
                    }
                    .create()
                    .show()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to fetch assigned bus numbers: ${error.message}")
                Toast.makeText(this@AdminAssignBusDriverRouteActivity, "Failed to load bus numbers.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Updates the text of the "Choose Bus Number" button.
     */
    private fun updateBusNumberButtonText() {
        chooseBusNumberButton.text = if (selectedBusNumber == null) {
            "Choose Bus Number"
        } else {
            "Bus: $selectedBusNumber"
        }
    }


    private fun saveAssignment() {
        // Updated validation to include selectedBusNumber
        if (selectedDate == null || selectedRoute == null || selectedDriverId == null || selectedTime == null || selectedBusNumber == null) {
            Toast.makeText(this, "Please select Date, Route, Driver, Time, and Bus Number.", Toast.LENGTH_SHORT).show()
            return
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        // NEW: Time validation to prevent assigning routes in the past
        val currentDateTime = Calendar.getInstance()
        val assignedDateTimeForValidation = Calendar.getInstance().apply {
            time = selectedDate.time // Set the selected date
            try {
                val parsedTime = timeFormat.parse(selectedTime!!) // Parse the selected time
                if (parsedTime != null) {
                    set(Calendar.HOUR_OF_DAY, parsedTime.hours)
                    set(Calendar.MINUTE, parsedTime.minutes)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing selected time for validation: ${e.message}", e)
                Toast.makeText(this@AdminAssignBusDriverRouteActivity, "Error validating time. Please try again.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (assignedDateTimeForValidation.timeInMillis < currentDateTime.timeInMillis) {
            Toast.makeText(this, "Cannot assign route for a past date or time. Please choose a future time.", Toast.LENGTH_LONG).show()
            Log.w(TAG, "Attempted to save past assignment: Date ${dateFormat.format(selectedDate.time)}, Time ${selectedTime}")
            return
        }
        // END NEW: Time validation

        val dateString = dateFormat.format(selectedDate.time)

        // Use the nested Firebase structure as per your driver-side activities
        val yearString = SimpleDateFormat("yyyy", Locale.getDefault()).format(selectedDate.time)
        val monthString = SimpleDateFormat("MM", Locale.getDefault()).format(selectedDate.time)
        val dayString = SimpleDateFormat("dd", Locale.getDefault()).format(selectedDate.time)

        // Create a unique key for the assignment within the specific day/month/year path
        // This key will ensure uniqueness for a given route, time, and bus number on a specific day
        val assignmentKey = "${selectedRoute?.replace(" ", "")}-${selectedTime?.replace(" ", "")?.replace(":", "")?.replace(" ", "")}-${selectedBusNumber}" // Using cleaned strings for key

        // Path to save the assignment: assignments/YYYY/MM/DD/ASSIGNMENT_KEY
        val assignmentPath = database.child("assignments").child(yearString).child(monthString).child(dayString).child(assignmentKey)


        // Check if the assignment key already exists at the specific nested path (Duplicate assignment for same route/time/bus)
        assignmentPath.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // The assignment already exists, show an error message
                    Toast.makeText(
                        this@AdminAssignBusDriverRouteActivity,
                        "Assignment already exists for this Date, Route, Time, and Bus Number. Please choose different values.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // Call a unified validation function for both driver and route availability
                    validateAndSaveAssignment(yearString, monthString, dayString, dateString, assignmentKey)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to check for existing assignment: ${error.message}")
                Toast.makeText(
                    this@AdminAssignBusDriverRouteActivity,
                    "Failed to save assignment: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    /**
     * Performs comprehensive validation for assignment conflicts (driver or route)
     * before attempting to save the new assignment.
     */
    private fun validateAndSaveAssignment(yearString: String, monthString: String, dayString: String, dateString: String, assignmentKey: String) {
        val assignmentsForDateRef = database.child("assignments").child(yearString).child(monthString).child(dayString)

        assignmentsForDateRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var driverAlreadyAssignedToOtherRoute = false
                var routeAlreadyTakenByAnotherDriver = false
                var conflictingRouteNameForDriver: String? = null // To store the route name for the driver conflict message

                for (assignmentSnapshot in snapshot.children) {
                    val assignedDriverId = assignmentSnapshot.child("driverId").getValue(String::class.java)
                    val assignedTime = assignmentSnapshot.child("time").getValue(String::class.java)
                    val assignedRouteName = assignmentSnapshot.child("route").getValue(String::class.java)
                    val assignedStatus = assignmentSnapshot.child("status").getValue(String::class.java) ?: "pending"

                    // Check 1: Is the selected driver already busy at this exact time?
                    // This covers cases where driver A is assigned Route A at 6 PM, and admin tries to assign driver A to Route B at 6 PM.
                    if (assignedDriverId == selectedDriverId && assignedTime == selectedTime &&
                        (assignedStatus == "pending" || assignedStatus == "in_progress")) {
                        driverAlreadyAssignedToOtherRoute = true
                        conflictingRouteNameForDriver = assignedRouteName
                        break // Found a driver conflict, no need to check further
                    }

                    // Check 2: Is the selected route at this exact time already taken by *any* driver?
                    // This covers your specific scenario: Driver A on Route A at 6:30 PM,
                    // and admin tries to assign Driver C to Route A at 6:30 PM.
                    if (assignedRouteName == selectedRoute && assignedTime == selectedTime &&
                        (assignedStatus == "pending" || assignedStatus == "in_progress")) {
                        routeAlreadyTakenByAnotherDriver = true
                        break // Found a route conflict, no need to check further
                    }
                }

                if (driverAlreadyAssignedToOtherRoute) {
                    Toast.makeText(
                        this@AdminAssignBusDriverRouteActivity,
                        "Driver ${selectedDriverName} is already assigned to ${conflictingRouteNameForDriver} at ${selectedTime} on ${selectedDate.time.formatDate()}. Please choose a different time or driver.",
                        Toast.LENGTH_LONG
                    ).show()
                } else if (routeAlreadyTakenByAnotherDriver) {
                    Toast.makeText(
                        this@AdminAssignBusDriverRouteActivity,
                        "Route ${selectedRoute} at ${selectedTime} on ${selectedDate.time.formatDate()} is already assigned to another driver. Please choose a different time or route.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // No conflicts found, proceed to save the assignment
                    saveNewAssignment(yearString, monthString, dayString, assignmentKey, dateString)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to perform assignment validation: ${error.message}")
                Toast.makeText(this@AdminAssignBusDriverRouteActivity, "Failed to validate assignment: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    /**
     * Saves the new assignment to Firebase after all validations pass.
     */
    private fun saveNewAssignment(yearString: String, monthString: String, dayString: String, assignmentKey: String, dateString: String) {
        val assignmentData = mapOf(
            "date" to dateString,
            "route" to selectedRoute,
            "driverId" to selectedDriverId,
            "driverName" to selectedDriverName,
            "time" to selectedTime,
            "busNumber" to selectedBusNumber,
            "status" to "pending"
        )

        val assignmentPath = database.child("assignments").child(yearString).child(monthString).child(dayString).child(assignmentKey)

        assignmentPath.setValue(assignmentData)
            .addOnSuccessListener {
                Toast.makeText(
                    this@AdminAssignBusDriverRouteActivity,
                    "Assignment saved successfully.",
                    Toast.LENGTH_SHORT
                ).show()
                // Clear selected values after successful save
                selectedDate = Calendar.getInstance()
                selectedRoute = null
                selectedDriverId = null
                selectedDriverName = null
                selectedTime = null
                selectedBusNumber = null
                updateDateButtonText()
                updateRouteButtonText()
                updateDriverButtonText()
                updateTimeButtonText()
                updateBusNumberButtonText()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this@AdminAssignBusDriverRouteActivity,
                    "Failed to save assignment: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    /**
     * Iterates through all 'pending' assignments in Firebase.
     * If the assigned time has passed (beyond the flexibility window), it updates the status of that assignment to 'expired'.
     * This function runs on app startup to maintain data consistency.
     */
    private fun updateExpiredPendingAssignments() {
        val now = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        database.child("assignments").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var updatedCount = 0
                for (yearSnapshot in snapshot.children) {
                    for (monthSnapshot in yearSnapshot.children) {
                        for (daySnapshot in monthSnapshot.children) {
                            for (assignmentSnapshot in daySnapshot.children) {
                                // Data class representing an assigned route (copied here for consistency)
                                data class AssignedRoute(
                                    val date: String = "",
                                    val route: String = "",
                                    val time: String = "",
                                    val driverId: String = "",
                                    val driverName: String = "",
                                    val busNumber: Int = 0,
                                    var status: String = "pending"
                                )
                                val assignedRoute = assignmentSnapshot.getValue(AssignedRoute::class.java)
                                assignedRoute?.let {
                                    // Only consider 'pending' routes for update
                                    if (it.status == "pending") {
                                        val assignedDateTimeString = "${it.date} ${it.time}"
                                        try {
                                            val assignedDateTime = dateFormat.parse(assignedDateTimeString)

                                            // Calculate the time after which a "pending" route should be considered "expired"
                                            val expiryTime = Calendar.getInstance().apply {
                                                time = assignedDateTime ?: Date(0L) // Use assigned time or epoch if null
                                                add(Calendar.MINUTE, FLEXIBILITY_MINUTES_AFTER_START)
                                            }.time

                                            if (assignedDateTime != null && now.time.after(expiryTime)) {
                                                // Current time is past the assigned time + flexibility, update status to 'expired'
                                                assignmentSnapshot.ref.child("status").setValue("expired")
                                                    .addOnSuccessListener {
                                                        Log.d(TAG, "Admin: Updated assignment to 'expired': ${assignedRoute.route} on ${assignedRoute.date} at ${assignedRoute.time}")
                                                        updatedCount++
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Log.e(TAG, "Admin: Failed to update status to 'expired' for ${assignedRoute.route}: ${e.message}", e)
                                                    }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Admin: Error parsing date/time for expiry check: ${e.message} for route ${it.route}", e)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (updatedCount > 0) {
                    Toast.makeText(this@AdminAssignBusDriverRouteActivity, "$updatedCount pending routes marked as expired.", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d(TAG, "Admin: No pending assignments found to mark as expired.")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Admin: Firebase cleanup cancelled: ${error.message}", error.toException())
            }
        })
    }


    // Extension function for Date formatting (optional, but clean)
    private fun Date.formatDate(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(this)
    }

    // Data class for BusDriver (same as in BusDriversDatabaseActivity)
    data class BusDriver(
        val name: String = "",
        val idNumber: String = "",
        val phoneNumber: String = "",
        val email: String = "",
        val role: String = "Driver",
        val route: String = ""
    )
}