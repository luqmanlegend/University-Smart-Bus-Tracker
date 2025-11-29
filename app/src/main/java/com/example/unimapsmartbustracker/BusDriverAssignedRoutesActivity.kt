package com.example.unimapsmartbustracker

import android.os.Bundle
import android.util.Log
import android.widget.Toast // Import Toast for messages
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*
import java.io.Serializable // Needed for AssignedRoute if it's reused elsewhere requiring serialization
import com.google.firebase.database.Exclude // Import Exclude
import androidx.core.content.ContextCompat // Import ContextCompat for color resources

class BusDriverAssignedRoutesActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var assignedRoutesRecyclerView: RecyclerView
    private lateinit var assignedRoutesList: ArrayList<AssignedRoute>
    private lateinit var adapter: AssignedRouteAdapter
    private val TAG = "BusDriverAssignedRoutes"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_busdriver_assigned_routes)

        database = Firebase.database.reference
        assignedRoutesRecyclerView = findViewById(R.id.assignedRoutesRecyclerView)
        assignedRoutesRecyclerView.layoutManager = LinearLayoutManager(this)
        assignedRoutesList = ArrayList()
        adapter = AssignedRouteAdapter(assignedRoutesList)
        assignedRoutesRecyclerView.adapter = adapter

        loadDriverIdAndAssignedRoutes()
    }

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
                        Toast.makeText(this@BusDriverAssignedRoutesActivity, "Driver information not found. Please contact support.", Toast.LENGTH_LONG).show() // User feedback
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to get driver idNumber: ${error.message}")
                    Toast.makeText(this@BusDriverAssignedRoutesActivity, "Failed to load driver data: ${error.message}", Toast.LENGTH_LONG).show() // User feedback
                }
            })
    }

    private fun loadAssignedRoutes(driverId: String) {
        // Read all assignments and filter by driverId
        // Assumes nested Firebase structure: assignments/YYYY/MM/DD/ASSIGNMENT_KEY
        database.child("assignments").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                assignedRoutesList.clear()
                for (yearSnapshot in snapshot.children) {
                    for (monthSnapshot in yearSnapshot.children) {
                        for (daySnapshot in monthSnapshot.children) { // Iterate through day nodes
                            for (assignmentSnapshot in daySnapshot.children) { // Iterate through assignment keys
                                val assignment = assignmentSnapshot.getValue(AssignedRoute::class.java)
                                assignment?.let {
                                    it.firebaseKey = assignmentSnapshot.key // Assign the Firebase key

                                    // Filter by driverId. No longer filtering by status == "completed" or "cancelled" here
                                    // as the goal is to show all historical assignments.
                                    if (it.driverId == driverId) {
                                        assignedRoutesList.add(it)
                                        Log.d(TAG, "Added assigned route for driver $driverId: Date=${it.date}, Route=${it.route}, Time=${it.time}, Bus=${it.busNumber}, Status=${it.status}")
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
                    Toast.makeText(this@BusDriverAssignedRoutesActivity, "No assigned routes found for you.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to read assigned routes: ${error.message}")
                Toast.makeText(this@BusDriverAssignedRoutesActivity, "Failed to load assigned routes: ${error.message}", Toast.LENGTH_LONG).show() // User feedback
            }
        })
    }

    /**
     * Data class representing an assigned route from Firebase.
     * Must match the fields saved by AdminAssignBusDriverRouteActivity.kt.
     * Implements Serializable to allow passing via Intent.
     * Ensure this matches the AssignedRoute class in BusDriverStartRouteActivity.kt for consistency.
     */
    data class AssignedRoute(
        val date: String = "",
        val route: String = "",
        val time: String = "",
        val driverId: String = "",
        val driverName: String = "",
        val busNumber: Int = 0,
        var status: String = "pending", // Status of the route (pending, in-progress, completed)
        @get:Exclude var firebaseKey: String? = null // Firebase key for this assignment
    ) : Serializable {
        // You might want a unique ID for each assignment if you plan to update/delete them
        fun getUniqueId(): String {
            return "${route.replace(" ", "")}-${time.replace(" ", "")}-${busNumber}"
        }
    }

    /**
     * RecyclerView Adapter for displaying assigned routes in a read-only format.
     */
    class AssignedRouteAdapter(private var routesList: List<AssignedRoute>) :
        RecyclerView.Adapter<AssignedRouteAdapter.RouteViewHolder>() {

        private val TAG = "AssignedRouteAdapter" // Tag for logging within the adapter

        /**
         * ViewHolder for a single assigned route item.
         */
        class RouteViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val dateTextView: android.widget.TextView = itemView.findViewById(R.id.dateTextView)
            val routeTextView: android.widget.TextView = itemView.findViewById(R.id.routeTextView)
            val timeTextView: android.widget.TextView = itemView.findViewById(R.id.timeTextView)
            val busNumberTextView: android.widget.TextView = itemView.findViewById(R.id.busNumberTextView) // New: Bus Number TextView
            val driverNameTextView: android.widget.TextView = itemView.findViewById(R.id.driverNameTextView) // New: Driver Name TextView
            val statusTextView: android.widget.TextView = itemView.findViewById(R.id.statusTextView) // Add status TextView
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RouteViewHolder {
            val itemView = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_assigned_route_readonly, parent, false) // Using the new read-only item layout
            return RouteViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
            val currentRoute = routesList[position]
            holder.dateTextView.text = "Date: ${currentRoute.date}"
            holder.routeTextView.text = "Route: ${currentRoute.route}"
            holder.timeTextView.text = "Time: ${currentRoute.time}"
            holder.busNumberTextView.text = "Bus No.: ${currentRoute.busNumber}" // Display bus number
            holder.driverNameTextView.text = "Driver: ${currentRoute.driverName}" // Display driver name

            // Determine and display status
            val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
            val currentDateTime = Calendar.getInstance().time

            try {
                val assignedDateTimeString = "${currentRoute.date} ${currentRoute.time}"
                val assignedDateTime = dateFormat.parse(assignedDateTimeString)

                if (assignedDateTime == null) {
                    holder.statusTextView.text = "Status: Error"
                    holder.statusTextView.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray))
                    Log.e(TAG, "Failed to parse date or time for route: ${currentRoute.route}")
                    return
                }

                when (currentRoute.status) {
                    "pending" -> {
                        if (currentDateTime.after(assignedDateTime)) {
                            holder.statusTextView.text = "Status: Expired"
                            holder.statusTextView.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark))
                        } else {
                            holder.statusTextView.text = "Status: Pending"
                            holder.statusTextView.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray))
                        }
                    }
                    "in_progress" -> {
                        holder.statusTextView.text = "Status: In Progress"
                        holder.statusTextView.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_blue_dark))
                    }
                    "completed" -> {
                        holder.statusTextView.text = "Status: Completed"
                        holder.statusTextView.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark))
                    }
                    "cancelled" -> {
                        holder.statusTextView.text = "Status: Cancelled"
                        holder.statusTextView.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.black))
                    }
                    else -> {
                        holder.statusTextView.text = "Status: Unknown"
                        holder.statusTextView.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.black))
                    }
                }
            } catch (e: Exception) {
                holder.statusTextView.text = "Status: Error"
                holder.statusTextView.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray))
                Log.e(TAG, "Error parsing date/time for status display: ${e.message}", e)
            }
        }

        override fun getItemCount() = routesList.size
    }
}
