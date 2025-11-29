package com.example.unimapsmartbustracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import android.util.Log
import com.google.firebase.auth.ktx.auth

class AdminPage : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private val TAG = "AdminPage"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adminpage)

        // Initialize Firebase Auth and Database
        auth = Firebase.auth
        database = Firebase.database.reference

        // Get UI elements
        val adminNameTextView = findViewById<TextView>(R.id.adminNameTextView)
        val adminIdTextView = findViewById<TextView>(R.id.adminIdTextView)
        val adminPhoneNumberTextView = findViewById<TextView>(R.id.adminPhoneNumberTextView)  // Find the new TextView
        val adminLogoutButton = findViewById<Button>(R.id.adminLogoutButton)
        val adminViewEditDatabaseActivity = findViewById<Button>(R.id.viewEditDatabaseButton)
        val assignBusRouteButton = findViewById<Button>(R.id.assignBusRouteButton) // Add this line

        // Get current user
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid

            database.child("drivers").child(userId).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val name = snapshot.child("name").value as? String ?: ""
                        val adminId = snapshot.child("idNumber").value as? String ?: ""
                        val phoneNumber = snapshot.child("phoneNumber").value as? String ?: "" // Get phone number

                        adminNameTextView.text = "Welcome $name"
                        adminIdTextView.text = "Admin ID: $adminId"
                        adminPhoneNumberTextView.text = "Phone Number: $phoneNumber" // Set phone number text
                    } else {
                        adminNameTextView.text = "Welcome Admin"
                        adminIdTextView.text = "Admin ID: N/A"
                        adminPhoneNumberTextView.text = "Phone Number: N/A" // set default
                        Log.e(TAG, "Admin data not found for UID: $userId")
                        Toast.makeText(
                            this,
                            "Admin data not found. Please contact support.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Error getting admin data: ${error.message}")
                    Toast.makeText(
                        this,
                        "Error retrieving admin data: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    adminNameTextView.text = "Welcome Admin"
                    adminIdTextView.text = "Admin ID: N/A"
                    adminPhoneNumberTextView.text = "Phone Number: N/A" // set default
                }

        } else {
            adminNameTextView.text = "Welcome Admin"
            adminIdTextView.text = "Admin ID: N/A"
            adminPhoneNumberTextView.text = "Phone Number: N/A" // set default
            Toast.makeText(this, "No admin logged in.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, BusDriverLogin::class.java)
            startActivity(intent)
            finish()
        }

        // Set click listener for the logout button
        adminLogoutButton.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, BusDriverLogin::class.java)
            startActivity(intent)
            finish()
        }

        adminViewEditDatabaseActivity.setOnClickListener {
            val intent = Intent(this, AdminDatabasePage::class.java)
            startActivity(intent)
        }

        // Set click listener for the Assign Bus Route button
        assignBusRouteButton.setOnClickListener {  // Add this block
            val intent = Intent(this, AdminAssignBusDriverRouteActivity::class.java)
            startActivity(intent)
        }
    }
}
