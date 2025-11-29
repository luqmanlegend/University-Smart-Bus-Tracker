package com.example.unimapsmartbustracker

import android.Manifest
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat // Needed for ContextCompat.getDrawable
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.location.Priority
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.*
import com.google.maps.android.SphericalUtil
import java.text.SimpleDateFormat
import java.util.*

class BusRouteTrackingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var database: DatabaseReference // Firebase DB reference for bus assignments
    private lateinit var busLocationDatabaseRef: DatabaseReference // Firebase DB reference for bus live location updates

    private var assignedRoute: BusDriverStartRouteActivity.AssignedRoute? = null
    private lateinit var routeInfoTextView: TextView
    private val TAG = "BusRouteTrackingAct"

    // Location Services
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var requestingLocationUpdates = false // Flag to track if updates are active

    // To store the full list of stops for the current route
    private val currentRouteStops = mutableListOf<RouteStop>()
    private var busMarker: Marker? = null
    // Changed fullRoutePolyline to represent the *remaining* path
    private var remainingRoutePolyline: Polyline? = null
    private var traversedPolyline: Polyline? = null // The polyline for the traversed part of the predefined route

    // Polyline for initial guidance from current location to the first stop
    private var initialGuidancePolyline: Polyline? = null
    private var firstStopMarker: Marker? = null // Marker for the first stop in guidance phase

    // Track the current target stop index for navigation guidance along the *predefined route*
    private var currentStopIndex: Int = 0

    // Flag to indicate if the driver has reached the first official stop
    private var isRouteStarted: Boolean = false

    // Constants for location permissions and settings requests
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val REQUEST_CHECK_SETTINGS = 1002

    // Data class to hold a waypoint's location and its name
    data class RouteStop(val location: LatLng, val name: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bus_route_tracking)

        database = FirebaseDatabase.getInstance().reference // For assignments
        // busLocationDatabaseRef will be set dynamically once busNumber is known

        assignedRoute = intent.getSerializableExtra("ROUTE_DETAILS") as? BusDriverStartRouteActivity.AssignedRoute

        if (assignedRoute == null) {
            Toast.makeText(this, "No route details found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        routeInfoTextView = findViewById(R.id.routeInfoTextView)
        routeInfoTextView.visibility = View.VISIBLE

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationRequest()
        createLocationCallback()

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val stopRouteFab: FloatingActionButton = findViewById(R.id.stopRouteFab)
        stopRouteFab.setOnClickListener {
            Toast.makeText(this, "Route tracking stopped for Bus No. ${assignedRoute?.busNumber}", Toast.LENGTH_SHORT).show()
            assignedRoute?.let { route ->
                updateRouteStatusInFirebase(route, "completed")
            }
            stopLocationUpdates() // Stop location updates when route is stopped
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (requestingLocationUpdates) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isCompassEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = true // Enable built-in My Location button

        assignedRoute?.let { route ->
            currentRouteStops.addAll(getRouteStops(route.route))
            updateRouteInfoText() // Initial UI update for "Waiting for device location"

            // DO NOT call setupPredefinedRouteDisplay here initially.
            // It will be called inside onNewLocation once the route officially starts.

            // Start checking location settings and begin updates
            checkLocationSettingsAndStartUpdates()

            // Initialize busLocationDatabaseRef once assignedRoute is known
            assignedRoute?.busNumber?.let { busNum ->
                busLocationDatabaseRef = FirebaseDatabase.getInstance().getReference("busLocations/bus$busNum")
                Log.d(TAG, "Bus location Firebase reference set to: ${busLocationDatabaseRef.path}")
            } ?: run {
                Log.e(TAG, "Assigned bus number is null, cannot set busLocationDatabaseRef.")
                Toast.makeText(this, "Error: Bus number not assigned.", Toast.LENGTH_LONG).show()
                finish() // Close activity if essential data is missing
            }
            Toast.makeText(this, "Map Ready for Route: ${route.route}, Bus: ${route.busNumber}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Creates a LocationRequest for desired location update parameters.
     */
    private fun createLocationRequest() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000) // Every 5 seconds
            .setMinUpdateIntervalMillis(2000) // Smallest displacement between updates
            .build()
    }

    /**
     * Defines the callback for when location updates are received.
     */
    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    onNewLocation(location)
                } ?: run {
                    Log.w(TAG, "LocationResult was null or contained no locations.")
                    updateRouteInfoText(null) // Indicate no location
                }
            }
        }
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
     * Handles a new location update from FusedLocationProvider.
     * This is where the bus marker, polylines, and Firebase updates happen.
     */
    private fun onNewLocation(location: Location) {
        val busLatLng = LatLng(location.latitude, location.longitude)
        val speed = location.speed * 3.6 // Convert m/s to km/h

        Log.d(TAG, "New device location: Lat=${location.latitude}, Lng=${location.longitude}, Speed=${speed} km/h")

        // Prepare the custom bus icon
        val busIconBitmap = getResizedBitmapFromDrawable(R.drawable.ic_bus_24, 80, 80) // Set desired size here (e.g., 80x80 pixels)
        val busIconDescriptor = if (busIconBitmap != null) {
            BitmapDescriptorFactory.fromBitmap(busIconBitmap)
        } else {
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE) // Fallback to default
        }

        // Update or add the bus marker with a custom icon
        if (busMarker == null) {
            busMarker = googleMap.addMarker(
                MarkerOptions()
                    .position(busLatLng)
                    .title("Bus ${assignedRoute?.busNumber ?: "You"}")
                    .snippet("Speed: ${"%.2f".format(speed)} km/h")
                    .icon(busIconDescriptor) // <--- MODIFIED TO USE SCALED BITMAP
            )
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(busLatLng, 15f))
        } else {
            busMarker?.position = busLatLng
            busMarker?.snippet = "Speed: ${"%.2f".format(speed)} km/h"
            // Optionally, animate camera to follow bus if not already centered
            if (!isCameraCenteredOnBus(busLatLng)) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLng(busLatLng))
            }
        }
        busMarker?.showInfoWindow()

        // --- Logic for Initial Guidance vs. On-Route Tracking ---
        if (!isRouteStarted) {
            // Phase 1: Guiding driver to the first stop
            if (currentRouteStops.isNotEmpty()) {
                val firstStopLocation = currentRouteStops[0].location
                val distanceToFirstStop = SphericalUtil.computeDistanceBetween(busLatLng, firstStopLocation)
                val startProximityThresholdMeters = 70.0 // Threshold to consider driver "at" the first stop

                // Draw a polyline from current bus location to the first stop (initial guidance)
                initialGuidancePolyline?.remove() // Clear previous one
                initialGuidancePolyline = googleMap.addPolyline(
                    PolylineOptions()
                        .add(busLatLng, firstStopLocation)
                        .color(Color.BLUE) // Changed from MAGENTA to BLUE
                        .width(12f) // Increased width
                        .geodesic(true)
                )

                // Add or update marker for the first stop
                if (firstStopMarker == null) {
                    firstStopMarker = googleMap.addMarker(
                        MarkerOptions()
                            .position(firstStopLocation)
                            .title("Start Point: ${currentRouteStops[0].name}")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)) // Changed to ORANGE
                    )
                } else {
                    firstStopMarker?.position = firstStopLocation
                }
                Log.d(TAG, "Guiding to first stop. Distance: %.2f m".format(distanceToFirstStop))

                if (distanceToFirstStop < startProximityThresholdMeters) {
                    isRouteStarted = true // Driver has reached the first official stop
                    currentStopIndex = 0 // Reset currentStopIndex to 0, indicating being at the first stop
                    Toast.makeText(this@BusRouteTrackingActivity, "Reached Start Point: ${currentRouteStops[0].name}. Route Officially Started!", Toast.LENGTH_LONG).show()

                    initialGuidancePolyline?.remove() // Clear initial guidance polyline
                    initialGuidancePolyline = null
                    firstStopMarker?.remove() // Remove the first stop marker from guidance phase
                    firstStopMarker = null

                    // Now draw the full predefined route (blue) and all stop markers,
                    // and initialize traversedPolyline
                    setupPredefinedRouteDisplay()

                    // Update route status in Firebase
                    assignedRoute?.let { route ->
                        updateRouteStatusInFirebase(route, "in_progress")
                    }
                }
            } else {
                Log.e(TAG, "No route stops defined for initial guidance.")
            }
        } else {
            // Phase 2: On-route tracking (bus is following the predefined route)
            val newTraversedPoints = mutableListOf<LatLng>()
            traversedPolyline?.points?.let { existingPoints ->
                newTraversedPoints.addAll(existingPoints)
            }
            newTraversedPoints.add(busLatLng)
            traversedPolyline?.points = newTraversedPoints

            // Proximity check to advance current stop along the *predefined route*
            if (currentStopIndex < currentRouteStops.size) {
                val targetStopLocation = currentRouteStops[currentStopIndex].location
                val proximityThresholdMeters = 50.0 // meters for regular stops
                val distance = SphericalUtil.computeDistanceBetween(busLatLng, targetStopLocation)

                if (distance < proximityThresholdMeters) {
                    Log.d(TAG, "Reached stop: ${currentRouteStops[currentStopIndex].name}")
                    Toast.makeText(this@BusRouteTrackingActivity, "Reached: ${currentRouteStops[currentStopIndex].name}", Toast.LENGTH_SHORT).show()
                    currentStopIndex++ // Move to the next stop

                    // *** IMPORTANT: Redraw remaining route from the new currentStopIndex ***
                    remainingRoutePolyline?.remove() // Remove old remaining route polyline
                    val remainingPathPoints = mutableListOf<LatLng>()
                    // Add current bus location to remaining path to connect it
                    remainingPathPoints.add(busLatLng) // Connects from current bus location
                    for (i in currentStopIndex until currentRouteStops.size) {
                        remainingPathPoints.add(currentRouteStops[i].location)
                    }
                    if (remainingPathPoints.isNotEmpty()) {
                        remainingRoutePolyline = googleMap.addPolyline(PolylineOptions().addAll(remainingPathPoints).color(Color.BLUE).width(10f))
                    }
                }
            } else {
                // Route completed, if all stops passed and not already handled
                if (assignedRoute?.status != "completed") {
                    updateRouteStatusInFirebase(assignedRoute!!, "completed")
                    // No need for a Toast here, as updateRouteStatusInFirebase handles it for "completed" status
                }
            }
        }

        // Always update UI and push location to Firebase
        updateRouteInfoText(busLatLng)
        pushBusLocationToFirebase(location)
    }

    /**
     * Checks if location settings are satisfied and starts location updates.
     * Requests user to enable GPS if needed.
     */
    private fun checkLocationSettingsAndStartUpdates() {
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener { locationSettingsResponse ->
            Log.d(TAG, "Location settings are satisfied. Starting location updates.")
            startLocationUpdates()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    exception.startResolutionForResult(this@BusRouteTrackingActivity, REQUEST_CHECK_SETTINGS)
                    Log.w(TAG, "Location settings not satisfied. Resolution dialog shown.")
                } catch (sendEx: SendIntentException) {
                    Log.e(TAG, "Error showing location settings dialog.", sendEx)
                }
            } else if (exception is ApiException && exception.statusCode == LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE) {
                // Location settings are not satisfied and no dialog will be displayed.
                Log.e(TAG, "Location settings are inadequate, and cannot be fixed here. Dialog not displayed.")
                Toast.makeText(this, "Location services are required. Please enable GPS manually.", Toast.LENGTH_LONG).show()
                routeInfoTextView.text = "Error: Location services required. Please enable GPS."
            }
        }
    }

    /**
     * Starts receiving location updates from FusedLocationProviderClient.
     * Ensures permissions are granted.
     */
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request permissions if not granted
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        requestingLocationUpdates = true
        Log.d(TAG, "Started location updates.")
    }

    /**
     * Stops receiving location updates.
     */
    private fun stopLocationUpdates() {
        if (!requestingLocationUpdates) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        requestingLocationUpdates = false
        Log.d(TAG, "Stopped location updates.")
    }

    /**
     * Handles permission request results.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Location permission granted. Starting updates.")
                startLocationUpdates()
            } else {
                Log.w(TAG, "Location permission denied. Cannot track bus.")
                Toast.makeText(this, "Location permission denied. Cannot track bus.", Toast.LENGTH_LONG).show()
                routeInfoTextView.text = "Error: Location permission denied."
            }
        }
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
                stops.add(RouteStop(LatLng(6.461504, 100.358658), "Dataran Bus UniMAP (Start)"))
                stops.add(RouteStop(LatLng(6.462380, 100.353602), "FKTE"))
                stops.add(RouteStop(LatLng(6.462678, 100.352846), "FKTM"))
                stops.add(RouteStop(LatLng(6.461441, 100.349670), "Library UniMAP"))
                stops.add(RouteStop(LatLng(6.459714, 100.346719), "Dewan Ilmu"))
                stops.add(RouteStop(LatLng(6.458780, 100.350903), "FKTEN"))
                stops.add(RouteStop(LatLng(6.458632, 100.356071), "Dewan Kuliah"))
                stops.add(RouteStop(LatLng( 6.460660, 100.360458), "Dataran Bus UniMAP (End)"))
            }
            // Add other routes here if needed
        }
        return stops
    }

    /**
     * Draws the predefined polyline path and adds markers for each stop.
     * This is called AFTER the driver has reached the first official stop.
     */
    private fun setupPredefinedRouteDisplay() {
        googleMap.clear() // Clear all markers and polylines from the initial guidance phase

        if (currentRouteStops.isNotEmpty()) {
            val polylinePoints = mutableListOf<LatLng>()
            val boundsBuilder = LatLngBounds.Builder()

            // Add markers for all stops
            for (i in currentRouteStops.indices) {
                val stop = currentRouteStops[i]
                googleMap.addMarker(MarkerOptions().position(stop.location).title("${i + 1}. ${stop.name}").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))) // Changed to ORANGE
                boundsBuilder.include(stop.location)
            }

            // Initially, remainingRoutePolyline contains the entire route (from current stop index)
            // It will be redrawn from the bus's current location when onNewLocation is called.
            // Here, we just draw the full blue route as it exists from the start point.
            for (i in currentStopIndex until currentRouteStops.size) {
                polylinePoints.add(currentRouteStops[i].location)
            }
            if (polylinePoints.isNotEmpty()) {
                remainingRoutePolyline = googleMap.addPolyline(PolylineOptions().addAll(polylinePoints).color(Color.BLUE).width(10f))
            }

            // Initialize traversedPolyline. It will start empty and grow.
            traversedPolyline = googleMap.addPolyline(PolylineOptions().color(Color.GRAY).width(10f))

            if (currentRouteStops.size > 0) {
                val padding = 100
                val cameraUpdate = CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), padding)
                googleMap.animateCamera(cameraUpdate)
            }
            Log.d(TAG, "Predefined Route ${assignedRoute?.route} drawn for on-route tracking (remaining path in blue).")
        } else {
            Log.w(TAG, "No waypoints found for route '${assignedRoute?.route}'.")
            Toast.makeText(this, "Route details not available on map.", Toast.LENGTH_SHORT).show()
        }
    }


    /**
     * Updates the route information TextView to show the current status and guidance.
     * @param currentBusLocation The current LatLng of the bus, or null if not available.
     */
    private fun updateRouteInfoText(currentBusLocation: LatLng? = null) {
        val routeName = assignedRoute?.route ?: "Unknown Route"
        val busNumber = assignedRoute?.busNumber ?: "N/A"
        val time = assignedRoute?.time ?: "N/A"

        val stringBuilder = StringBuilder()
        stringBuilder.append("Route: $routeName, Bus: $busNumber, Time: $time\n\n")

        if (currentRouteStops.isEmpty()) {
            stringBuilder.append("Status: Initializing route details...")
        } else if (currentBusLocation == null) {
            stringBuilder.append("Status: Waiting for device location data...")
        } else {
            if (!isRouteStarted) {
                // Phase 1: Guide to the first stop
                val firstStopName = currentRouteStops[0].name
                val distanceToFirstStop = SphericalUtil.computeDistanceBetween(currentBusLocation, currentRouteStops[0].location)
                stringBuilder.append("Status: Head to Start Point\n")
                stringBuilder.append("Destination: $firstStopName (Approx. ${"%.0f".format(distanceToFirstStop)} m)")
            } else {
                // Phase 2: On-route tracking
                when {
                    currentStopIndex >= currentRouteStops.size -> {
                        // Route is completed
                        stringBuilder.append("Status: Route Completed! All stops visited.")
                        // Toast for route completion is already handled in onNewLocation
                    }
                    currentStopIndex == 0 -> {
                        // Just started the official route at the first stop (already passed threshold)
                        stringBuilder.append("Status: Departing from: ${currentRouteStops[0].name}\n")
                        if (currentRouteStops.size > 1) {
                            stringBuilder.append("Next Destination: ${currentRouteStops[1].name}")
                        } else {
                            stringBuilder.append("This is the only stop on the route.")
                        }
                    }
                    else -> {
                        // En route between stops
                        stringBuilder.append("Status: Just arrived at: ${currentRouteStops[currentStopIndex - 1].name}\n")
                        stringBuilder.append("Proceeding to: ${currentRouteStops[currentStopIndex].name}")
                    }
                }
            }
        }
        routeInfoTextView.text = stringBuilder.toString()
    }

    /**
     * Pushes the current bus location (from the driver's device) to Firebase.
     * This is what student apps will consume.
     */
    private fun pushBusLocationToFirebase(location: Location) {
        assignedRoute?.busNumber?.let { busNum ->
            val locationData = hashMapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "speed" to location.speed.toDouble() // Speed in m/s, can be converted to km/h on display
            )
            busLocationDatabaseRef.setValue(locationData)
                .addOnSuccessListener {
                    // Log.d(TAG, "Bus $busNum location pushed to Firebase successfully.")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to push bus $busNum location to Firebase: ${e.message}", e)
                    // Consider showing a toast if this is a persistent issue
                }
        } ?: run {
            Log.e(TAG, "Cannot push location to Firebase: Assigned bus number is null.")
        }
    }

    /**
     * Updates the status of the assigned route in Firebase.
     * @param route The AssignedRoute object to update.
     * @param status The new status to set (e.g., "completed", "in_progress", "cancelled").
     */
    private fun updateRouteStatusInFirebase(route: BusDriverStartRouteActivity.AssignedRoute, status: String) {
        val date = route.date
        val routeKey = route.firebaseKey

        if (date.isEmpty() || routeKey == null) {
            Log.e(TAG, "Cannot update route status: missing date or firebaseKey.")
            Toast.makeText(this, "Error: Unable to update route status in Firebase.", Toast.LENGTH_SHORT).show()
            return
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateObj: Date? = dateFormat.parse(date)

        if (dateObj == null) {
            Log.e(TAG, "Cannot parse date for Firebase path: $date")
            Toast.makeText(this, "Error: Invalid date format for route status update.", Toast.LENGTH_SHORT).show()
            return
        }

        val calendar = Calendar.getInstance().apply { time = dateObj }
        val year = calendar.get(Calendar.YEAR).toString()
        val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
        val day = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))

        val assignmentRef = database.child("assignments").child(year).child(month).child(day).child(routeKey)

        assignmentRef.child("status").setValue(status)
            .addOnSuccessListener {
                Log.d(TAG, "Route status updated to '$status' for route ${route.route} on $date.")
                // Only show a toast here if it's the completion status, otherwise it's too chatty
                if (status == "completed") {
                    Toast.makeText(this, "Route status updated to '$status'.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update route status for route ${route.route}: ${e.message}", e)
                Toast.makeText(this, "Failed to update route status: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
