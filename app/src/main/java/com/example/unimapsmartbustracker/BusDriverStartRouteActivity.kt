package com.example.unimapsmartbustracker

import android.content.Context
import android.content.Intent
import android.location.LocationManager // Import LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog // Import AlertDialog
import androidx.core.content.ContextCompat // Import ContextCompat for color resources
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.database.Exclude // Import Exclude for AssignedRoute

class BusDriverStartRouteActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var assignedRoutesRecyclerView: RecyclerView
    private lateinit var assignedRoutesList: ArrayList<AssignedRoute>
    private lateinit var adapter: AssignedRouteStartAdapter
    private val TAG = "BusDriverStartRouteAct"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bus_driver_start_route)

        // Initialize Firebase Database reference
        database = Firebase.database.reference

        // Initialize RecyclerView and its adapter
        assignedRoutesRecyclerView = findViewById(R.id.assignedRoutesStartRecyclerView)
        assignedRoutesRecyclerView.layoutManager = LinearLayoutManager(this)
        assignedRoutesList = ArrayList()
        // Pass a lambda function for the start button click listener
        adapter = AssignedRouteStartAdapter(assignedRoutesList) { assignedRoute ->
            // Show information dialog before starting the route
            showStartRouteInfoDialog(assignedRoute)
        }
        assignedRoutesRecyclerView.adapter = adapter

        // Load assigned routes for the current driver
        loadDriverIdAndAssignedRoutes()
    }

    override fun onResume() {
        super.onResume()
        // Removed: The call to updateExpiredPendingAssignments() is removed from here
        // as AdminAssignBusDriverRouteActivity is now responsible for updating Firebase status.
        // This activity will just reflect the current status from Firebase.
        adapter.notifyDataSetChanged() // Still useful to refresh button states based on local time
        Log.d(TAG, "onResume: Refreshed adapter for time-sensitive button states.")
    }

    /**
     * Checks if location services (GPS or network location) are enabled on the device.
     * @return True if location services are enabled, false otherwise.
     */
    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Displays an AlertDialog with important information before the driver starts the route.
     * @param assignedRoute The route about to be started.
     */
    private fun showStartRouteInfoDialog(assignedRoute: AssignedRoute) {
        val message = """
            i) Please drive carefully, recommended bus speed inside UniMAP is 30km/h - 50km/h.
            ii) Please put the Tracker Device inside the bus and pass it on for the next driver to use.
            iii) Recite Doa before starting the route.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Important Information Before Starting Route")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                // Dismiss the dialog
                dialog.dismiss()

                // NEW: Check if location is enabled after dismissing the info dialog
                if (!isLocationEnabled()) {
                    Toast.makeText(this, "Please enable your device's location (GPS) to start tracking.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton // Stop here if location is not enabled
                }

                // IMPORTANT: Update status to "in_progress" in Firebase BEFORE starting tracking activity
                updateRouteStatusToInProgress(assignedRoute)
            }
            .setCancelable(false) // Prevents dialog from being dismissed by back button
            .show()
    }

    /**
     * Updates the status of the given assigned route to "in_progress" in Firebase.
     * After successful update, it starts the BusRouteTrackingActivity.
     */
    private fun updateRouteStatusToInProgress(assignedRoute: AssignedRoute) {
        if (assignedRoute.firebaseKey == null) {
            Log.e(TAG, "Cannot update status: firebaseKey is null for route ${assignedRoute.route}")
            Toast.makeText(this, "Error: Route data incomplete. Cannot start tracking.", Toast.LENGTH_LONG).show()
            return
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateForPath = dateFormat.format(dateFormat.parse(assignedRoute.date) ?: Date()) // Ensure date is correctly parsed for path
        val parts = dateForPath.split("/") // DD/MM/YYYY
        if (parts.size != 3) {
            Log.e(TAG, "Invalid date format for path: ${assignedRoute.date}")
            Toast.makeText(this, "Error: Invalid date format. Cannot start tracking.", Toast.LENGTH_LONG).show()
            return
        }
        val dayString = parts[0]
        val monthString = parts[1]
        val yearString = parts[2]

        val assignmentPath = database.child("assignments")
            .child(yearString).child(monthString).child(dayString).child(assignedRoute.firebaseKey!!)

        assignmentPath.child("status").setValue("in_progress")
            .addOnSuccessListener {
                Log.d(TAG, "Successfully updated route ${assignedRoute.route} status to 'in_progress' in Firebase.")
                Toast.makeText(this, "Route status updated to In Progress.", Toast.LENGTH_SHORT).show()

                // Now proceed to start the BusRouteTrackingActivity
                val intent = Intent(this, BusRouteTrackingActivity::class.java)
                // Update the status of the assignedRoute object locally before passing
                assignedRoute.status = "in_progress"
                intent.putExtra("ROUTE_DETAILS", assignedRoute as Serializable)
                startActivity(intent)
                Toast.makeText(this, "Loading map for Route: ${assignedRoute.route}, Bus: ${assignedRoute.busNumber}", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update route ${assignedRoute.route} status to 'in_progress': ${e.message}", e)
                Toast.makeText(this, "Failed to update route status. Please try again.", Toast.LENGTH_LONG).show()
            }
    }


    /**
     * Loads the current authenticated driver's ID number from Firebase,
     * and then proceeds to load their assigned routes.
     */
    private fun loadDriverIdAndAssignedRoutes() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.e(TAG, "No authenticated bus driver found. User needs to log in.")
            Toast.makeText(this, "Please log in to view assigned routes.", Toast.LENGTH_LONG).show() // User feedback
            return
        }

        val driverUid = currentUser.uid

        // First, get the driver's idNumber from the "drivers" node
        database.child("drivers").child(driverUid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val driverId = snapshot.child("idNumber").value as? String
                    if (driverId != null) {
                        Log.d(TAG, "Found driver idNumber: $driverId for UID: $driverUid")
                        loadAssignedRoutes(driverId)
                    } else {
                        Log.e(TAG, "Driver idNumber not found for UID: $driverUid. Check 'drivers' node structure.")
                        Toast.makeText(this@BusDriverStartRouteActivity, "Driver information not found. Please contact support.", Toast.LENGTH_LONG).show() // User feedback
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to get driver idNumber: ${error.message}")
                    Toast.makeText(this@BusDriverStartRouteActivity, "Failed to load driver data: ${error.message}", Toast.LENGTH_LONG).show() // User feedback
                }
            })
    }

    /**
     * Loads assigned routes from Firebase Realtime Database for a given driverId.
     * It listens for changes in the "assignments" node and filters routes
     * that match the provided driverId and are not "completed" or "cancelled".
     */
    private fun loadAssignedRoutes(driverId: String) {
        database.child("assignments").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                assignedRoutesList.clear() // Clear existing list to prevent duplicates

                for (yearSnapshot in snapshot.children) {
                    for (monthSnapshot in yearSnapshot.children) {
                        for (daySnapshot in monthSnapshot.children) { // Iterate through day nodes
                            for (assignmentSnapshot in daySnapshot.children) { // Iterate through assignment keys
                                val assignmentData = assignmentSnapshot.getValue(AssignedRoute::class.java)
                                assignmentData?.let {
                                    it.firebaseKey = assignmentSnapshot.key

                                    // Only show assignments for the current driver, and NOT 'completed' or 'cancelled'
                                    // 'Expired' status will now be explicitly set in Firebase by AdminAssignBusDriverRouteActivity.kt
                                    // and then reflected directly from the fetched 'it.status'.
                                    if (it.driverId == driverId && it.status != "completed" && it.status != "cancelled") {
                                        assignedRoutesList.add(it)
                                        Log.d(TAG, "Added assigned route: ${it.route} on ${it.date} at ${it.time}, Status: ${it.status}")
                                    } else {
                                        Log.d(TAG, "Filtered out assignment: ${it.route} on ${it.date} at ${it.time} (DriverID: ${it.driverId == driverId}, Status: ${it.status})")
                                    }
                                }
                            }
                        }
                    }
                }

                // Sort the list by date and then by time for chronological order
                assignedRoutesList.sortWith(compareBy<AssignedRoute> {
                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(it.date) ?: Date(0L)
                }.thenBy {
                    // Custom comparator for time strings (e.g., "07:30 AM" vs "01:00 PM")
                    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                    timeFormat.parse(it.time) ?: Date(0L)
                })

                adapter.notifyDataSetChanged()

                if (assignedRoutesList.isEmpty()) {
                    Toast.makeText(this@BusDriverStartRouteActivity, "No active assigned routes found for you today or upcoming.", Toast.LENGTH_LONG).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to read assigned routes: ${error.message}")
                Toast.makeText(this@BusDriverStartRouteActivity, "Failed to load assigned routes.", Toast.LENGTH_LONG).show()
            }
        })
    }

    /**
     * Data class representing an assigned route.
     * This matches the structure of your "assignments" node in Firebase Realtime Database.
     * Implements Serializable to allow passing via Intent.
     */
    data class AssignedRoute(
        val date: String = "",
        val route: String = "",
        val time: String = "",
        val driverId: String = "",
        val driverName: String = "",
        val busNumber: Int = 0,
        var status: String = "pending", // Status of the route (pending, in-progress, completed, cancelled, expired)
        @get:Exclude var firebaseKey: String? = null // Firebase key for this assignment
    ) : Serializable {
        // You might want a unique ID for each assignment if you plan to update/delete them
        fun getUniqueId(): String {
            return "${route.replace(" ", "")}-${time.replace(" ", "")}-${busNumber}"
        }
    }

    /**
     * RecyclerView Adapter for displaying assigned routes in `BusDriverStartRouteActivity`.
     * Each item includes a "Start Route" button.
     */
    class AssignedRouteStartAdapter(
        private var routesList: List<AssignedRoute>,
        private val onStartClickListener: (AssignedRoute) -> Unit
    ) : RecyclerView.Adapter<AssignedRouteStartAdapter.RouteStartViewHolder>() {

        // Define a window around the assigned start time for the button to be active.
        // These constants define the client-side display logic for "pending" routes.
        companion object {
            const val PROXIMITY_MINUTES_BEFORE_START = 20 // e.g., for 6:00 PM route, 'Upcoming' until 5:40 PM
            const val FLEXIBILITY_MINUTES_AFTER_START = 5  // e.g., for 6:00 PM route, 'Start Route' until 6:05 PM (exclusive)
        }

        class RouteStartViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val dateTextView: TextView = itemView.findViewById(R.id.cardDateTextView)
            val routeTextView: TextView = itemView.findViewById(R.id.cardRouteTextView)
            val timeTextView: TextView = itemView.findViewById(R.id.cardTimeTextView)
            val busNumberTextView: TextView = itemView.findViewById(R.id.cardBusNumberTextView)
            val startButton: Button = itemView.findViewById(R.id.startButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteStartViewHolder {
            val itemView = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_bus_route_card, parent, false)
            return RouteStartViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: RouteStartViewHolder, position: Int) {
            val currentRoute = routesList[position]
            holder.dateTextView.text = "Date: ${currentRoute.date}"
            holder.routeTextView.text = "Route: ${currentRoute.route}"
            holder.timeTextView.text = "Time: ${currentRoute.time}"
            holder.busNumberTextView.text = "Bus No.: ${currentRoute.busNumber}"

            // Determine button state based on route status and current time
            val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
            val currentDateTime = Calendar.getInstance().time // Current device time

            try {
                // Combine date and time strings for easier comparison
                val assignedDateTimeString = "${currentRoute.date} ${currentRoute.time}"
                val assignedDateTime = dateFormat.parse(assignedDateTimeString)

                if (assignedDateTime == null) {
                    Log.e("AssignedRouteAdapter", "Failed to parse date or time for route: ${currentRoute.route}")
                    holder.startButton.text = "Error"
                    holder.startButton.isEnabled = false
                    holder.startButton.backgroundTintList = ContextCompat.getColorStateList(holder.itemView.context, android.R.color.darker_gray)
                    holder.startButton.setOnClickListener(null) // Remove listener for invalid state
                    return
                }

                when (currentRoute.status) {
                    "in_progress" -> {
                        holder.startButton.text = "Route In Progress"
                        holder.startButton.isEnabled = false
                        holder.startButton.backgroundTintList = ContextCompat.getColorStateList(holder.itemView.context, android.R.color.holo_blue_dark)
                        holder.startButton.setOnClickListener(null)
                    }
                    "pending" -> {
                        // Calculate the window where the "Start Route" button should be active
                        val activeWindowStart = Calendar.getInstance().apply {
                            time = assignedDateTime
                            add(Calendar.MINUTE, -PROXIMITY_MINUTES_BEFORE_START)
                        }.time // e.g., for 6:00 PM route, window starts at 5:40 PM

                        val activeWindowEnd = Calendar.getInstance().apply {
                            time = assignedDateTime
                            add(Calendar.MINUTE, FLEXIBILITY_MINUTES_AFTER_START)
                        }.time // e.g., for 6:00 PM route, window ends at 6:05 PM (exclusive)

                        // Check if current time is within the active window (inclusive start, exclusive end)
                        // This determines if 'Start Route' should be shown
                        val isCurrentlyInActiveWindow = !currentDateTime.before(activeWindowStart) && currentDateTime.before(activeWindowEnd)

                        if (isCurrentlyInActiveWindow) {
                            // Current time is within the valid window to start the route
                            holder.startButton.text = "Start Route"
                            holder.startButton.isEnabled = true
                            holder.startButton.backgroundTintList = ContextCompat.getColorStateList(holder.itemView.context, android.R.color.holo_green_light)
                            holder.startButton.setOnClickListener { onStartClickListener(currentRoute) }
                        } else if (currentDateTime.before(activeWindowStart)) {
                            // Assigned time is in the future, before the active window
                            holder.startButton.text = "Upcoming"
                            holder.startButton.isEnabled = false
                            holder.startButton.backgroundTintList = ContextCompat.getColorStateList(holder.itemView.context, android.R.color.darker_gray)
                            holder.startButton.setOnClickListener(null) // Remove listener
                        } else {
                            // Assigned time (plus flexibility) has passed and status is still pending (meaning it was missed)
                            // This indicates it should be expired, handled by AdminAssignBusDriverRouteActivity.kt
                            holder.startButton.text = "Expired / Missed"
                            holder.startButton.isEnabled = false
                            holder.startButton.backgroundTintList = ContextCompat.getColorStateList(holder.itemView.context, android.R.color.holo_red_dark)
                            holder.startButton.setOnClickListener(null) // Remove listener
                        }
                    }
                    // If the status is 'expired' (updated in Firebase by AdminAssignBusDriverRouteActivity.kt)
                    "expired" -> {
                        holder.startButton.text = "Expired / Missed"
                        holder.startButton.isEnabled = false
                        holder.startButton.backgroundTintList = ContextCompat.getColorStateList(holder.itemView.context, android.R.color.holo_red_dark)
                        holder.startButton.setOnClickListener(null) // Remove listener
                    }
                    // "completed" and "cancelled" statuses are filtered out in loadAssignedRoutes,
                    // so they shouldn't typically appear here. If they somehow do, they'll be disabled.
                    else -> {
                        holder.startButton.text = "Status: ${currentRoute.status.replace("_", " ").capitalize()}"
                        holder.startButton.isEnabled = false
                        holder.startButton.backgroundTintList = ContextCompat.getColorStateList(holder.itemView.context, android.R.color.darker_gray)
                        holder.startButton.setOnClickListener(null)
                    }
                }
            } catch (e: Exception) {
                Log.e("AssignedRouteAdapter", "Error parsing date/time for button logic: ${e.message}", e)
                holder.startButton.text = "Error"
                holder.startButton.isEnabled = false
                holder.startButton.backgroundTintList = ContextCompat.getColorStateList(holder.itemView.context, android.R.color.darker_gray)
                holder.startButton.setOnClickListener(null)
            }
        }

        override fun getItemCount() = routesList.size
    }
}
