package com.example.unimapsmartbustracker

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Button
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import android.util.Log
import android.widget.TextView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import androidx.appcompat.app.AlertDialog // Import AlertDialog
import android.widget.Toast // Import Toast

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: com.google.firebase.database.DatabaseReference

    // Define the TAG for logging in MainActivity
    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase Auth and Database
        auth = Firebase.auth
        database = Firebase.database.reference
        Log.d(TAG, "Firebase Auth initialized. Current user on onCreate: ${auth.currentUser?.uid ?: "No user"}")


        // Get current user
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "User is logged in on onCreate: ${currentUser.uid}")
            // Get user data from Realtime Database
            val userId = currentUser.uid
            database.child("students").child(userId).addListenerForSingleValueEvent(object :
                ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Get the name, matric number, and phone number from the database
                        val name = snapshot.child("name").value as? String ?: ""
                        val matricNumber = snapshot.child("matricNumber").value as? String ?: ""
                        val phoneNumber = snapshot.child("phoneNumber").value as? String ?: ""

                        // Update UI with user data
                        val welcomeTextView: TextView = findViewById(R.id.welcomeTextView)
                        val nameTextView: TextView = findViewById(R.id.nameTextView)
                        val matricTextView: TextView = findViewById(R.id.matricTextView)
                        val phoneTextView: TextView = findViewById(R.id.phoneTextView) //find view for phone number

                        welcomeTextView.text = "Welcome to UniMAP Smart Bus Tracker System"
                        nameTextView.text = "Welcome $name"  // Display the name
                        matricTextView.text = "Your matric number is $matricNumber" // Display matric number
                        phoneTextView.text = "Your phone number is $phoneNumber" // Display phone number
                        Log.d(TAG, "User data loaded: Name=$name, Matric=$matricNumber")
                    } else {
                        //if the snapshot does not exist
                        val welcomeTextView: TextView = findViewById(R.id.welcomeTextView)
                        welcomeTextView.text = "Welcome to UniMAP Smart Bus Tracker System"
                        Log.d(TAG, "No user data snapshot found for UID: $userId")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                    Log.e(TAG, "Error getting user data: ${error.message}")
                    val welcomeTextView: TextView = findViewById(R.id.welcomeTextView)
                    welcomeTextView.text = "Welcome to UniMAP Smart Bus Tracker System"
                }
            })
        } else {
            val welcomeTextView: TextView = findViewById(R.id.welcomeTextView)
            welcomeTextView.text = "Welcome to UniMAP Smart Bus Tracker System"
            Log.d(TAG, "No user logged in on onCreate.")
        }

        // Schedule Button
        val scheduleButton: Button = findViewById(R.id.scheduleButton)
        scheduleButton.setOnClickListener {
            val intent = Intent(this, UniMAPBusSchedulePage::class.java)
            startActivity(intent)
            Log.d(TAG, "Navigated to UniMAPBusSchedulePage.")
        }

        // Logout Button
        val logoutButton: Button = findViewById(R.id.logoutButton)
        logoutButton.setOnClickListener {
            // Sign out the user
            auth.signOut()
            Log.d(TAG, "User logged out.")
            // Navigate to the login activity
            val intent = Intent(this, StudentLogin::class.java)
            startActivity(intent)
            finish() // Close the current activity to prevent going back
        }

        // Change Password Button
        val changePasswordButton: Button = findViewById(R.id.changePasswordButton)
        changePasswordButton.setOnClickListener {
            val intent = Intent(this, ChangePassword::class.java)
            startActivity(intent)
            Log.d(TAG, "Navigated to ChangePassword.")
        }

        // Track Bus Button with Auth Check
        val trackBusButton: Button = findViewById(R.id.trackBusButton)
        trackBusButton.setOnClickListener {
            val userBeforeTrack = auth.currentUser
            Log.d(TAG, "Track Bus button clicked. Current user detected: ${userBeforeTrack?.uid ?: "No user"}")

            if (userBeforeTrack != null) {
                // User is logged in, now show route selection dialog
                showRouteSelectionDialog()
            } else {
                // User not logged in, redirect to login
                Log.e(TAG, "ERROR: User is unexpectedly null. Redirecting to StudentLogin. Current auth.currentUser: ${auth.currentUser?.uid ?: "STILL null"}")
                val intent = Intent(this, StudentLogin::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    /**
     * Displays a dialog for the student to choose between Route A and Route B.
     */
    private fun showRouteSelectionDialog() {
        val routes = arrayOf("Route A", "Route B")
        AlertDialog.Builder(this)
            .setTitle("Choose Bus Route to Track")
            .setItems(routes) { dialog, which ->
                val selectedRoute = routes[which]
                Log.d(TAG, "Student selected route: $selectedRoute")

                // Start TrackBusActivity and pass the selected route
                val intent = Intent(this, TrackBusActivity::class.java)
                intent.putExtra("SELECTED_ROUTE_NAME", selectedRoute)
                startActivity(intent)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Route tracking cancelled.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
