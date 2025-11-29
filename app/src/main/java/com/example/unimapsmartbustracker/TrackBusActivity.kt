package com.example.unimapsmartbustracker

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat // Import ContextCompat for getDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.DatabaseReference // Added import
import com.google.firebase.database.Exclude // Added import
import com.google.maps.android.SphericalUtil
import java.io.Serializable // Added import
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for tracking a single UniMAP bus location on a Google Map based on student's selection.
 * It fetches bus location data from Firebase Realtime Database for the specific route selected
 * and updates the map in real-time. It always shows live location data, and displays the bus number
 * if an active assignment for that route is found. It now also displays the current passenger count.
 */
class TrackBusActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var database: DatabaseReference // Using DatabaseReference directly
    private lateinit var auth: FirebaseAuth
    private val TAG = "TrackBusActivity"

    // This database reference points to `routeLiveLocations/[selectedRouteName]`
    private var liveLocationRef: DatabaseReference? = null
    // assignmentsRef for dynamic bus number lookup from assignments
    private lateinit var assignmentsRef: DatabaseReference

    // Listeners to be managed for proper lifecycle handling
    private var liveLocationValueListener: ValueEventListener? = null
    private var assignmentsValueListener: ValueEventListener? = null

    private var busMarker: Marker? = null
    private var remainingRoutePolyline: Polyline? = null
    private var traversedPolyline: Polyline? = null

    private val currentRouteStops = mutableListOf<RouteStop>()
    private var currentStopIndexForDisplay: Int = 0

    private lateinit var routeStatusTextView: TextView
    private var selectedRouteName: String? = null
    private var assignedBusNumberForRoute: Int? = null // Store the bus number from assignments

    // To hold the last received live location data for display purposes
    private var lastKnownLiveLocation: LiveBusLocation? = null

    // Data class to hold a waypoint's location and its name
    data class RouteStop(val location: LatLng, val name: String)

    // Data class to match Firebase live location structure from Arduino
    data class LiveBusLocation(
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val speed: Double = 0.0, // Speed in km/h
        val passengerCount: Int = 0 // ADDED: passengerCount to match Arduino payload
    )

    // Data class for AssignedRoute (copied from BusDriverStartRouteActivity)
    data class AssignedRoute(
        val date: String = "",
        val route: String = "",
        val time: String = "",
        val driverId: String = "",
        val driverName: String = "",
        val busNumber: Int = 0,
        var status: String = "pending", // Status of the route (pending, in-progress, completed, cancelled, expired)
        @get:Exclude var firebaseKey: String? = null // Firebase key for this assignment
    ) : Serializable


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_track_bus)

        Log.d(TAG, "onCreate started.")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.track_bus_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = Firebase.auth
        database = Firebase.database.reference // Initialize broad Firebase reference

        // Initialize TextView
        routeStatusTextView = findViewById(R.id.routeStatusTextView)
        routeStatusTextView.visibility = View.VISIBLE

        selectedRouteName = intent.getStringExtra("SELECTED_ROUTE_NAME")
        Log.d(TAG, "Selected Route Name from Intent: $selectedRouteName")

        if (selectedRouteName.isNullOrEmpty()) {
            Toast.makeText(this, "No route selected. Please go back and choose a route.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "No route name passed to TrackBusActivity. Finishing activity.")
            finish()
            return
        }

        // Initialize liveLocationRef to point to the specific route chosen by the student
        // The Arduino code sends data to /routeLiveLocations/RouteA or /routeLiveLocations/RouteB
        liveLocationRef = database.child("routeLiveLocations").child(selectedRouteName!!.replace(" ", ""))

        // Get assignments for the current date to correlate with live data
        val today = Calendar.getInstance()
        val yearString = SimpleDateFormat("yyyy", Locale.getDefault()).format(today.time)
        val monthString = String.format("%02d", today.get(Calendar.MONTH) + 1)
        val dayString = String.format("%02d", today.get(Calendar.DAY_OF_MONTH))
        assignmentsRef = database.child("assignments").child(yearString).child(monthString).child(dayString)


        if (auth.currentUser == null) {
            Log.e(TAG, "User is null in onCreate. Redirecting to StudentLogin.")
            val intent = Intent(this, StudentLogin::class.java)
            startActivity(intent)
            finish()
            return
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val backButton: Button = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            Log.d(TAG, "Back button clicked. Navigating to MainActivity.")
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        Log.d(TAG, "Google Map is ready (onMapReady callback).")

        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isCompassEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = false // Students don't need their location button

        selectedRouteName?.let { route ->
            currentRouteStops.addAll(getRouteStops(route)) // Populate route stops for display and proximity check

            // Setup the static route display (markers and polylines) first
            setupRouteDisplay()

            // Start listening for live location data immediately, regardless of assignment status
            startLiveLocationTracking()

            // Start listening for assignments to get the bus number
            findAssignedBusNumberForRoute(route)

            // Initial UI update (bus number will be "N/A" until assignments lookup, location "unavailable" until Arduino sends data)
            updateRouteStatusText(null, null, null) // Pass null for initial state, including passengerCount

            Toast.makeText(this, "Tracking Route: $route", Toast.LENGTH_LONG).show()
        } ?: run {
            Toast.makeText(this, "Error: No route selected for map.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "onMapReady: selectedRouteName is null despite onCreate check.")
        }
    }

    /**
     * Starts listening to live location updates from Firebase for the SPECIFIC selected route.
     * This runs independently of assignment status.
     */
    private fun startLiveLocationTracking() {
        if (liveLocationRef == null) {
            Log.e(TAG, "liveLocationRef is null, cannot start live location tracking.")
            Toast.makeText(this, "Error: Live location reference not set up.", Toast.LENGTH_SHORT).show()
            return
        }

        // Remove old listener if it was previously active on this ref
        liveLocationValueListener?.let { liveLocationRef!!.removeEventListener(it) }

        liveLocationValueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val liveLocation = snapshot.getValue(LiveBusLocation::class.java)
                if (liveLocation != null) {
                    lastKnownLiveLocation = liveLocation // Store the latest location
                    Log.d(TAG, "Live data received for ${selectedRouteName}: Lat=${liveLocation.latitude}, Lng=${liveLocation.longitude}, Speed=${liveLocation.speed}, Passengers=${liveLocation.passengerCount}")
                    updateMapAndUI(liveLocation) // Always update map with live data
                    // Update UI with all live data including passenger count
                    updateRouteStatusText(LatLng(liveLocation.latitude, liveLocation.longitude), liveLocation.speed, liveLocation.passengerCount)
                } else {
                    lastKnownLiveLocation = null // Clear last known location
                    Log.d(TAG, "No live data for ${selectedRouteName} found at Firebase path or data is empty.")
                    // Clear bus marker and polylines if no data is coming
                    busMarker?.remove()
                    busMarker = null
                    remainingRoutePolyline?.remove()
                    remainingRoutePolyline = null
                    traversedPolyline?.remove()
                    traversedPolyline = null
                    updateRouteStatusText(null, null, null) // Indicate no live data
                    Toast.makeText(this@TrackBusActivity, "Bus for Route ${selectedRouteName} is offline or not sending data.", Toast.LENGTH_LONG).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                lastKnownLiveLocation = null // Clear last known location on error
                Log.e(TAG, "Firebase live location listener cancelled for ${selectedRouteName}: ${error.message}", error.toException())
                Toast.makeText(this@TrackBusActivity, "Error tracking bus location.", Toast.LENGTH_SHORT).show()
                googleMap.clear()
                busMarker = null
                remainingRoutePolyline = null
                traversedPolyline = null
                updateRouteStatusText(null, null, null)
            }
        }
        liveLocationRef!!.addValueEventListener(liveLocationValueListener!!)
        Log.d(TAG, "Attached live location listener to ${liveLocationRef!!.path}.")
    }

    /**
     * Finds the bus number assigned to the selected route for the current day from Firebase assignments.
     * This only updates the assignedBusNumberForRoute variable and triggers a UI update.
     * It does NOT control the live location listener.
     * @param routeName The user-friendly name of the selected route (e.g., "Route A").
     */
    private fun findAssignedBusNumberForRoute(routeName: String) {
        // Remove existing assignments listener to prevent multiple active listeners
        assignmentsValueListener?.let { assignmentsRef.removeEventListener(it) }

        assignmentsValueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                assignedBusNumberForRoute = null // Reset before searching for an active assignment

                val activeAssignmentsForSelectedRoute = mutableListOf<AssignedRoute>()

                for (assignmentSnapshot in snapshot.children) {
                    val assignedRouteData = assignmentSnapshot.getValue(AssignedRoute::class.java)
                    assignedRouteData?.let {
                        // Only consider assignments for the selected route that are 'pending' or 'in_progress'
                        if (it.route == routeName && (it.status == "pending" || it.status == "in_progress")) {
                            activeAssignmentsForSelectedRoute.add(it)
                        }
                    }
                }

                // Sort active assignments by time to find the next chronological bus
                activeAssignmentsForSelectedRoute.sortBy { parseTime(it.time) }

                // Determine the bus to track:
                // Prioritize 'in_progress' bus if any, otherwise the earliest 'pending' bus
                var busToTrack: AssignedRoute? = null
                for (assignment in activeAssignmentsForSelectedRoute) {
                    if (assignment.status == "in_progress") {
                        busToTrack = assignment
                        break // Found an in-progress bus, prioritize it
                    } else if (busToTrack == null) {
                        // If no in-progress bus found yet, take the first pending one (which will be earliest due to sort)
                        busToTrack = assignment
                    }
                }

                if (busToTrack != null) {
                    // If the assigned bus number has changed, log it. The UI update will handle the display.
                    if (assignedBusNumberForRoute != busToTrack.busNumber) {
                        Log.d(TAG, "Assigned bus number for ${routeName} changed from ${assignedBusNumberForRoute} to ${busToTrack.busNumber}.")
                        Toast.makeText(this@TrackBusActivity, "Tracking bus ${busToTrack.busNumber} for Route: ${routeName}", Toast.LENGTH_LONG).show()
                    }
                    assignedBusNumberForRoute = busToTrack.busNumber // Update the bus number to display
                    Log.d(TAG, "Active Bus Number for Route $routeName: ${assignedBusNumberForRoute}. Status: ${busToTrack.status}")
                } else {
                    Log.d(TAG, "No active bus found for Route $routeName in assignments today with 'pending' or 'in_progress' status.")
                    assignedBusNumberForRoute = null // No active bus assigned
                    Toast.makeText(this@TrackBusActivity, "No active bus assigned for Route $routeName currently.", Toast.LENGTH_LONG).show()
                }
                // Always update UI after assignment check, using the last known live location if available
                // Ensure passenger count is also passed
                updateRouteStatusText(lastKnownLiveLocation?.let { LatLng(it.latitude, it.longitude) }, lastKnownLiveLocation?.speed, lastKnownLiveLocation?.passengerCount)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to query assignments for bus number: ${error.message}")
                Toast.makeText(this@TrackBusActivity, "Error fetching bus assignment status.", Toast.LENGTH_LONG).show()
                assignedBusNumberForRoute = null // Clear bus number on error
                // Ensure passenger count is also passed
                updateRouteStatusText(lastKnownLiveLocation?.let { LatLng(it.latitude, it.longitude) }, lastKnownLiveLocation?.speed, lastKnownLiveLocation?.passengerCount) // Update UI
            }
        }
        assignmentsRef.addValueEventListener(assignmentsValueListener!!)
    }

    /**
     * Helper function to parse time strings (e.g., "08:00 AM") into Date objects for sorting.
     */
    private fun parseTime(timeString: String): Date {
        val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return try {
            format.parse(timeString) ?: Date(0) // Return epoch if parsing fails
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing time string: $timeString", e)
            Date(0) // Return epoch on error
        }
    }


    /**
     * Updates the map marker and TextView for the selected route with its live location data.
     * This function is now called directly from liveLocationValueListener, ensuring real-time map updates.
     * @param liveLocation The current live location data for the selected route.
     */
    private fun updateMapAndUI(liveLocation: LiveBusLocation) {
        val latLng = LatLng(liveLocation.latitude, liveLocation.longitude)
        val speedText = String.format("Speed: %.2f km/h", liveLocation.speed)

        // Use assignedBusNumberForRoute for the title, which comes from the assignments listener
        val busTitle = if (assignedBusNumberForRoute != null) {
            "Bus ${assignedBusNumberForRoute} (${selectedRouteName})"
        } else {
            "Bus on ${selectedRouteName} (No Assignment)" // Fallback if no assignment is active
        }

        // Prepare the custom bus icon
        val busIconBitmap = getResizedBitmapFromDrawable(R.drawable.ic_bus_24, 80, 80)
        val busIconDescriptor = if (busIconBitmap != null) {
            BitmapDescriptorFactory.fromBitmap(busIconBitmap)
        } else {
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
        }

        // Update or create marker
        if (busMarker == null) {
            busMarker = googleMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(busTitle)
                    .snippet(speedText)
                    .icon(busIconDescriptor)
            )
            // Initial camera move to the bus when it first appears
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        } else {
            busMarker?.position = latLng
            busMarker?.title = busTitle // Update title if assigned bus number changes
            busMarker?.snippet = speedText
            // Only animate camera if not already centered to avoid jitter
            if (!isCameraCenteredOnBus(latLng)) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))
            }
        }
        busMarker?.showInfoWindow()

        // Update traversed polyline (grey)
        val newTraversedPoints = traversedPolyline?.points?.toMutableList() ?: mutableListOf()
        newTraversedPoints.add(latLng)
        if (traversedPolyline == null) {
            traversedPolyline = googleMap.addPolyline(
                PolylineOptions()
                    .addAll(newTraversedPoints)
                    .color(Color.GRAY)
                    .width(10f)
            )
        } else {
            traversedPolyline?.points = newTraversedPoints
        }

        // Update remaining route polyline (blue)
        if (currentRouteStops.isNotEmpty()) {
            var closestStopIndex = 0
            var minDistance = Double.MAX_VALUE
            for (i in currentRouteStops.indices) {
                val stop = currentRouteStops[i]
                val distance = SphericalUtil.computeDistanceBetween(latLng, stop.location)
                if (distance < minDistance) {
                    minDistance = distance
                    closestStopIndex = i
                }
            }

            remainingRoutePolyline?.remove()
            val remainingPathPoints = mutableListOf<LatLng>()
            remainingPathPoints.add(latLng) // Connects from current bus location
            for (i in closestStopIndex until currentRouteStops.size) {
                remainingPathPoints.add(currentRouteStops[i].location)
            }
            if (remainingPathPoints.isNotEmpty()) {
                remainingRoutePolyline = googleMap.addPolyline(
                    PolylineOptions()
                        .addAll(remainingPathPoints)
                        .color(Color.BLUE)
                        .width(10f)
                )
            }
        }

        Log.d(TAG, "Displayed bus location for ${selectedRouteName} (Bus No: ${assignedBusNumberForRoute ?: "N/A"}) at $latLng, Speed: ${liveLocation.speed} km/h, Passengers: ${liveLocation.passengerCount}")
    }


    /**
     * Updates the TextView at the top to show bus route, bus number, and current location/next stop.
     * @param currentBusLocation The current LatLng of the bus (from Arduino), or null if not available.
     * @param currentSpeed The current speed of the bus, or null if not available.
     * @param passengerCount The current number of passengers on the bus, or null if not available.
     */
    private fun updateRouteStatusText(currentBusLocation: LatLng? = null, currentSpeed: Double? = null, passengerCount: Int? = null) {
        val routeDisplay = selectedRouteName ?: "Unknown Route"
        val busNumDisplay = assignedBusNumberForRoute?.toString() ?: "N/A"
        val passengersDisplay = passengerCount?.toString() ?: "N/A" // Display passenger count
        val stringBuilder = StringBuilder()

        stringBuilder.append("Route: $routeDisplay | Bus No.: $busNumDisplay\n")

        // Re-added Lat, Lng, and Speed display as requested
        val latitudeText = currentBusLocation?.latitude?.let { "%.4f".format(it) } ?: "N/A"
        val longitudeText = currentBusLocation?.longitude?.let { "%.4f".format(it) } ?: "N/A"
        val speedText = currentSpeed?.let { "%.2f km/h".format(it) } ?: "N/A"
        stringBuilder.append("Lat: $latitudeText, Lng: $longitudeText, Speed: $speedText\n")

        stringBuilder.append("Current Passengers on the bus: $passengersDisplay\n") // Added passenger count to display

        if (currentRouteStops.isEmpty()) {
            stringBuilder.append("Status: Initializing route details...")
        } else if (currentBusLocation == null) {
            stringBuilder.append("Status: Waiting for live data from bus for ${routeDisplay}...")
        } else if (assignedBusNumberForRoute == null) {
            stringBuilder.append("Status: Bus location for ${routeDisplay} received.\nWaiting for active assignment.")
        }
        else {
            // Find the closest stop to the current bus location
            var closestStopName: String? = null
            var minDistance = Double.MAX_VALUE

            var determinedClosestStopIndex = -1
            for (i in currentRouteStops.indices) {
                val stop = currentRouteStops[i]
                val distance = SphericalUtil.computeDistanceBetween(currentBusLocation, stop.location)

                if (distance < minDistance) {
                    minDistance = distance
                    closestStopName = stop.name
                    determinedClosestStopIndex = i
                }
            }

            if (determinedClosestStopIndex != -1) {
                currentStopIndexForDisplay = determinedClosestStopIndex

                val lastReachedStopName = currentRouteStops[currentStopIndexForDisplay].name
                stringBuilder.append("Currently at: $lastReachedStopName\n")

                val nextStopIndex = currentStopIndexForDisplay + 1
                if (nextStopIndex < currentRouteStops.size) {
                    stringBuilder.append("Next Stop: ${currentRouteStops[nextStopIndex].name}")
                } else {
                    stringBuilder.append("Next Stop: End of Route")
                }
            } else {
                stringBuilder.append("Currently at: En route between stops.")
            }
        }
        routeStatusTextView.text = stringBuilder.toString()
    }


    /**
     * Converts a drawable resource to a scaled Bitmap.
     * @param drawableId The resource ID of the drawable (e.g., R.drawable.ic_bus_24).
     * @param width The desired width of the bitmap in pixels.
     * @param height The desired height of the bitmap in pixels.
     * @return A scaled Bitmap or null if the drawable cannot be loaded.
     */
    private fun getResizedBitmapFromDrawable(drawableId: Int, width: Int, height: Int): Bitmap? {
        val drawable: Drawable? = ContextCompat.getDrawable(this, drawableId)
        if (drawable == null) {
            Log.e(TAG, "Drawable not found for ID: $drawableId")
            return null
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Checks if the camera is currently centered on the bus location.
     * This helps prevent excessive camera movements.
     * @param busLocation The current location of the bus.
     * @return True if the camera is approximately centered on the bus, false otherwise.
     */
    private fun isCameraCenteredOnBus(busLocation: LatLng): Boolean {
        val cameraTarget = googleMap.cameraPosition.target
        val latDiff = Math.abs(cameraTarget.latitude - busLocation.latitude)
        val lngDiff = Math.abs(cameraTarget.longitude - busLocation.longitude)
        // Define a small threshold for "centered"
        return latDiff < 0.0001 && lngDiff < 0.0001
    }

    /**
     * Determines the predefined stops for a given route name.
     * This helper function can be expanded with more routes/stops.
     */
    private fun getRouteStops(routeName: String): List<RouteStop> {
        val stops = mutableListOf<RouteStop>()
        when (routeName) {
            "Route A" -> {
                stops.add(RouteStop(LatLng( 6.460660, 100.360458), "Dataran Bus UniMAP (Start)"))
                stops.add(RouteStop(LatLng(6.458632, 100.356071), "Dewan Kuliah"))
                stops.add(RouteStop(LatLng(6.458780, 100.350903), "FKTEN"))
                stops.add(RouteStop(LatLng(6.459714, 100.346719), "Dewan Ilmu"))
                stops.add(RouteStop(LatLng(6.461441, 100.349670), "Library UniMAP"))
                stops.add(RouteStop(LatLng(6.462678, 100.352846), "FKTM"))
                stops.add(RouteStop(LatLng(6.462380, 100.353602), "FKTE"))
                stops.add(RouteStop(LatLng( 6.460660, 100.360458), "Dataran Bus UniMAP (End)"))
            }
            "Route B" -> {
                stops.add(RouteStop(LatLng( 6.460660, 100.360458), "Dataran Bus UniMAP (Start)"))
                stops.add(RouteStop(LatLng(6.462380, 100.353602), "FKTE"))
                stops.add(RouteStop(LatLng(6.462678, 100.352846), "FKTM"))
                stops.add(RouteStop(LatLng(6.461441, 100.349670), "Library UniMAP"))
                stops.add(RouteStop(LatLng(6.459714, 100.346719), "Dewan Ilmu"))
                stops.add(RouteStop(LatLng(6.458780, 100.350903), "FKTEN"))
                stops.add(RouteStop(LatLng(6.458632, 100.356071), "Dewan Kuliah"))
                stops.add(RouteStop(LatLng( 6.460660, 100.360458), "Dataran Bus UniMAP (End)"))
            }
        }
        return stops
    }

    /**
     * Draws the predefined polyline path and adds markers for each stop for the selected route.
     * This method is called once when the map is ready.
     */
    private fun setupRouteDisplay() {
        googleMap.clear() // Clear any existing markers/polylines from previous state

        val stops = currentRouteStops // Use the already populated currentRouteStops
        if (stops.isNotEmpty()) {
            val polylinePoints = mutableListOf<LatLng>()
            val boundsBuilder = LatLngBounds.Builder()

            // Add markers for all stops
            for (i in stops.indices) {
                val stop = currentRouteStops[i]
                googleMap.addMarker(
                    MarkerOptions()
                        .position(stop.location)
                        .title("${i + 1}. ${stop.name}")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)) // Orange for stops
                )
                boundsBuilder.include(stop.location)
            }

            // Initially draw the *entire* route as the "remaining route" for the student view
            polylinePoints.addAll(stops.map { it.location })
            remainingRoutePolyline = googleMap.addPolyline(
                PolylineOptions()
                    .addAll(polylinePoints)
                    .color(Color.BLUE) // Blue for the overall route
                    .width(10f)
            )

            // Initialize traversed polyline (it will start empty)
            traversedPolyline = googleMap.addPolyline(
                PolylineOptions()
                    .color(Color.GRAY) // Grey for traversed path
                    .width(10f)
            )

            if (stops.size > 0) {
                val padding = 100
                val cameraUpdate = CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), padding)
                googleMap.animateCamera(cameraUpdate)
            }
            Log.d(TAG, "Route ${selectedRouteName} drawn with ${stops.size} stops and polylines for student tracking.")
        } else {
            Log.w(TAG, "No waypoints found for route '${selectedRouteName}'.")
            Toast.makeText(this, "Route details not available on map.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Safely remove listeners if they have been initialized
        liveLocationRef?.let { ref ->
            liveLocationValueListener?.let { listener ->
                ref.removeEventListener(listener)
                Log.d(TAG, "Removed liveLocationValueListener from ${ref.path}.")
            }
        }
        assignmentsRef.let { ref ->
            assignmentsValueListener?.let { listener ->
                ref.removeEventListener(listener)
                Log.d(TAG, "Removed assignmentsValueListener from ${ref.path}.")
            }
        }
    }
}
