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

class BusDriverMainPage : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: com.google.firebase.database.DatabaseReference
    private val TAG = "BusDriverMainPage"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_busdrivermainpage)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.busdriver_main_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase Auth and Database
        auth = Firebase.auth
        database = Firebase.database.reference

        // Get UI elements
        val welcomeTextView: TextView = findViewById(R.id.welcomeTextView)
        val nameTextView: TextView = findViewById(R.id.nameTextView)
        val idTextView: TextView = findViewById(R.id.idTextView)
        val phoneNumberTextView: TextView = findViewById(R.id.phoneNumberTextView)

        // Get current user
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Get user data from Realtime Database
            val userId = currentUser.uid
            database.child("drivers").child(userId).addListenerForSingleValueEvent(object :
                ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Get the name, ID, and phone number from the database
                        val name = snapshot.child("name").value as? String ?: ""
                        val driverId = snapshot.child("idNumber").value as? String ?: ""
                        val phoneNumber = snapshot.child("phoneNumber").value as? String ?: ""

                        // Update UI with user data
                        welcomeTextView.text = "Welcome to UniMAP Bus Driver Portal"
                        nameTextView.text = "Driver: $name"
                        idTextView.text = "ID: $driverId"
                        phoneNumberTextView.text = "Your Phone Number is: $phoneNumber"
                    } else {
                        // If the snapshot does not exist
                        welcomeTextView.text = "Welcome to UniMAP Bus Driver Portal"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                    Log.e(TAG, "Error getting driver data: ${error.message}")
                    val welcomeTextView: TextView = findViewById(R.id.welcomeTextView)
                    welcomeTextView.text = "Welcome to UniMAP Bus Driver Portal"
                }
            })
        } else {
            val welcomeTextView: TextView = findViewById(R.id.welcomeTextView)
            welcomeTextView.text = "Welcome to UniMAP Bus Driver Portal"
        }

        // Logout Button
        val logoutButton: Button = findViewById(R.id.logoutButton)
        logoutButton.setOnClickListener {
            // Sign out the user
            auth.signOut()
            // Navigate to the login activity
            val intent = Intent(this, BusDriverLogin::class.java)
            startActivity(intent)
            finish() // Close the current activity to prevent going back
        }

        // Change Password Button
        val changePasswordButton = findViewById<Button>(R.id.changePasswordButton)
        changePasswordButton.setOnClickListener {
            val intent = Intent(this, ChangePassword::class.java)
            startActivity(intent)
        }

        // View Assigned Routes (This button still goes to UniMAPBusSchedulePage as per your original code)
        val viewRoutesButton: Button = findViewById(R.id.viewRoutesButton)
        viewRoutesButton.setOnClickListener {
            val intent = Intent(this, UniMAPBusSchedulePage::class.java)
            startActivity(intent)
        }

        // "View Assigned Routes" Button - now directs to BusDriverAssignedRoutesActivity (as per original request)
        val updateStatusButton: Button = findViewById(R.id.updateStatusButton)
        updateStatusButton.setOnClickListener {
            val intent = Intent(this, BusDriverAssignedRoutesActivity::class.java) // Reverted to original target
            startActivity(intent)
        }

        // NEW: "Start Bus Route" Button - directs to BusDriverStartRouteActivity
        val startRouteButton: Button = findViewById(R.id.startRouteButton) // Assuming you'll add this ID to your layout
        startRouteButton.setOnClickListener {
            val intent = Intent(this, BusDriverStartRouteActivity::class.java)
            startActivity(intent)
        }
    }
}
