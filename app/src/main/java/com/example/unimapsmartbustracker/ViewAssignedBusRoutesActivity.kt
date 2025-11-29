package com.example.unimapsmartbustracker

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.ContextCompat // Import ContextCompat for color resources

@IgnoreExtraProperties
data class AssignedBusRoute(
    val date: String = "",
    val driverName: String = "",
    val driverId: String = "",
    val route: String = "",
    val time: String = "",
    val busNumber: Int = 0, // Added busNumber field
    var status: String = "pending", // Ensure status field is present
    @get:Exclude
    var firebaseKey: String? = null
)

class ViewAssignedBusRoutesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AssignedBusRoutesAdapter
    private val assignedRoutesList = mutableListOf<AssignedBusRoute>()
    private lateinit var databaseRef: DatabaseReference
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyTextView: TextView

    companion object {
        private const val TAG = "ViewAssignedBusRoutes"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_assigned_bus_routes)

        recyclerView = findViewById(R.id.assignedRoutesRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyTextView = findViewById(R.id.emptyTextView)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AssignedBusRoutesAdapter(assignedRoutesList) { position ->
            showDeleteConfirmationDialog(position)
        }
        recyclerView.adapter = adapter

        databaseRef = FirebaseDatabase.getInstance().getReference("assignments")

        fetchAssignedRoutes()
    }

    private fun fetchAssignedRoutes() {
        progressBar.visibility = View.VISIBLE
        emptyTextView.visibility = View.GONE

        // Listen for data changes in the 'assignments' node
        databaseRef.addValueEventListener(object : ValueEventListener { // Changed to addValueEventListener for real-time updates
            override fun onDataChange(snapshot: DataSnapshot) {
                assignedRoutesList.clear()
                // Iterate through the nested structure: YEAR -> MM -> DD -> ASSIGNMENT_KEY
                for (yearSnapshot in snapshot.children) {
                    for (monthSnapshot in yearSnapshot.children) {
                        for (daySnapshot in monthSnapshot.children) { // Added iteration for the day node
                            for (assignmentSnapshot in daySnapshot.children) { // Now iterating over actual assignment keys
                                val assignedRoute = assignmentSnapshot.getValue(AssignedBusRoute::class.java)
                                if (assignedRoute != null) {
                                    assignedRoute.firebaseKey = assignmentSnapshot.key // Store the unique key for deletion
                                    assignedRoutesList.add(assignedRoute)
                                }
                            }
                        }
                    }
                }
                // Sort the list by date and time
                assignedRoutesList.sortWith(compareBy<AssignedBusRoute> {
                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(it.date) ?: Date(0L) // Handle potential null parse results
                }.thenBy {
                    // Custom comparator for time strings (e.g., "07:30 AM" vs "01:00 PM")
                    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                    timeFormat.parse(it.time) ?: Date(0L) // Handle potential null parse results
                })

                adapter.notifyDataSetChanged()
                if (assignedRoutesList.isEmpty()) {
                    emptyTextView.visibility = View.VISIBLE
                    emptyTextView.text = "No assigned routes found."
                } else {
                    emptyTextView.visibility = View.GONE
                }
                progressBar.visibility = View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@ViewAssignedBusRoutesActivity, "Failed to load data: ${error.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Database error", error.toException())
            }
        })
    }

    private fun showDeleteConfirmationDialog(position: Int) {
        val assignedRoute = assignedRoutesList[position]
        AlertDialog.Builder(this)
            .setTitle("Delete Assignment")
            .setMessage("Are you sure you want to delete the assignment for ${assignedRoute.driverName} (Bus ${assignedRoute.busNumber}) on ${assignedRoute.date} at ${assignedRoute.time}?")
            .setPositiveButton("Delete") { dialog, _ ->
                deleteAssignment(position)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun deleteAssignment(position: Int) {
        val assignedRoute = assignedRoutesList[position]

        val key = assignedRoute.firebaseKey
        if (key == null) {
            Toast.makeText(this, "Unable to find assignment key for deletion.", Toast.LENGTH_SHORT).show()
            return
        }

        // Parse the date string (dd/MM/yyyy) to get year, month, and day for the Firebase path
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateObj: Date? = dateFormat.parse(assignedRoute.date)

        if (dateObj == null) {
            Toast.makeText(this, "Invalid date format for deletion.", Toast.LENGTH_SHORT).show()
            return
        }

        val calendar = Calendar.getInstance().apply { time = dateObj }
        val year = calendar.get(Calendar.YEAR).toString()
        // Month needs to be 1-indexed and padded with zero if single digit (e.g., 01 for January)
        val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
        val day = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))

        // Construct the full path to the assignment in Firebase: assignments/YYYY/MM/DD/ASSIGNMENT_KEY
        val assignmentRef = databaseRef.child(year).child(month).child(day).child(key)

        assignmentRef.removeValue()
            .addOnSuccessListener {
                Log.d(TAG, "Assignment successfully deleted from Firebase: $key")
                Toast.makeText(this, "Assignment deleted", Toast.LENGTH_SHORT).show()
                // No need to manually remove from list and notify adapter if using addValueEventListener,
                // as the listener will trigger and re-populate the list.
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error deleting assignment from Firebase: $key", e)
                Toast.makeText(this, "Failed to delete assignment: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    class AssignedBusRoutesAdapter(
        private val routes: MutableList<AssignedBusRoute>,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<AssignedBusRoutesAdapter.RouteViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_assigned_route, parent, false) // Ensure this points to the correct XML
            return RouteViewHolder(view, onItemClick)
        }

        override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
            holder.bind(routes[position])
        }

        override fun getItemCount(): Int = routes.size

        class RouteViewHolder(itemView: View, onItemClick: (Int) -> Unit) : RecyclerView.ViewHolder(itemView) {
            private val dateTextView: TextView = itemView.findViewById(R.id.cardDateTextView) // Updated ID
            private val driverNameTextView: TextView = itemView.findViewById(R.id.cardDriverNameTextView) // Updated ID
            private val driverIdTextView: TextView = itemView.findViewById(R.id.cardDriverIdTextView) // Updated ID
            private val routeTextView: TextView = itemView.findViewById(R.id.cardRouteTextView) // Updated ID
            private val timeTextView: TextView = itemView.findViewById(R.id.cardTimeTextView) // Updated ID
            private val busNumberTextView: TextView = itemView.findViewById(R.id.cardBusNumberTextView) // Updated ID
            private val statusTextView: TextView = itemView.findViewById(R.id.cardStatusTextView) // Added status TextView

            init {
                itemView.setOnClickListener {
                    onItemClick(adapterPosition)
                }
            }

            fun bind(route: AssignedBusRoute) {
                dateTextView.text = "Date: ${route.date}"
                routeTextView.text = "Route: ${route.route}"
                timeTextView.text = "Time: ${route.time}"
                busNumberTextView.text = "Bus No.: ${route.busNumber}"
                driverNameTextView.text = "Driver Name: ${route.driverName}"
                driverIdTextView.text = "Driver ID: ${route.driverId}"

                // Determine and display status
                val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
                val currentDateTime = Calendar.getInstance().time

                try {
                    val assignedDateTimeString = "${route.date} ${route.time}"
                    val assignedDateTime = dateFormat.parse(assignedDateTimeString)

                    if (assignedDateTime == null) {
                        statusTextView.text = "Status: Error"
                        statusTextView.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.darker_gray))
                        Log.e(TAG, "Failed to parse date or time for route: ${route.route}")
                        return
                    }

                    when (route.status) {
                        "pending" -> {
                            if (currentDateTime.after(assignedDateTime)) {
                                statusTextView.text = "Status: Expired"
                                statusTextView.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_red_dark))
                            } else {
                                statusTextView.text = "Status: Pending"
                                statusTextView.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.darker_gray))
                            }
                        }
                        "in_progress" -> {
                            statusTextView.text = "Status: In Progress"
                            statusTextView.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_blue_dark))
                        }
                        "completed" -> {
                            statusTextView.text = "Status: Completed"
                            statusTextView.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark))
                        }
                        "cancelled" -> {
                            statusTextView.text = "Status: Cancelled"
                            statusTextView.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.black)) // Or another distinct color
                        }
                        else -> {
                            statusTextView.text = "Status: Unknown"
                            statusTextView.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.black))
                        }
                    }
                } catch (e: Exception) {
                    statusTextView.text = "Status: Error"
                    statusTextView.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.darker_gray))
                    Log.e(TAG, "Error parsing date/time for status display: ${e.message}", e)
                }
            }
        }
    }
}
