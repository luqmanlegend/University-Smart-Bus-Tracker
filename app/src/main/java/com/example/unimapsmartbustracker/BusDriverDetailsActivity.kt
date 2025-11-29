package com.example.unimapsmartbustracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class BusDriverDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bus_driver_details)

        // Get UI elements
        val titleTextView = findViewById<TextView>(R.id.studentsTitleTextView) // Title
        val driverNameTextView = findViewById<TextView>(R.id.driverNameTextView)
        val idNumberTextView = findViewById<TextView>(R.id.idNumberTextView)
        val phoneNumberTextView = findViewById<TextView>(R.id.phoneNumberTextView)
        val emailTextView = findViewById<TextView>(R.id.emailTextView)
        val contactDriverButton = findViewById<Button>(R.id.contactStudentButton) // Ensure it is contactDriverButton in layout
        val backToMainButton = findViewById<Button>(R.id.backToMainButton)
        val deleteDriverButton = findViewById<Button>(R.id.deleteDriverButton) // Get Delete button

        // Get the bus driver data from the intent
        val driverName = intent.getStringExtra("name")
        val idNumber = intent.getStringExtra("idNumber")
        val phoneNumber = intent.getStringExtra("phoneNumber")
        val email = intent.getStringExtra("email")
        val route = intent.getStringExtra("route")
        val firebaseKey = intent.getStringExtra("firebaseKey") // <--- Get firebaseKey here

        // Set title
        titleTextView.text = "⚙️BUS DRIVER DETAILS⚙️"

        // Display the bus driver data
        driverNameTextView.text = "Name: $driverName"
        idNumberTextView.text = "ID Number: $idNumber"
        phoneNumberTextView.text = "Phone Number: $phoneNumber"
        emailTextView.text = "Email: $email"

        // Back button
        backToMainButton.setOnClickListener {
            finish()
        }

        // Contact driver button
        contactDriverButton.setOnClickListener {
            val intent = Intent(this, ContactStudentActivity::class.java) // or ContactDriverActivity
            intent.putExtra("name", driverName)
            intent.putExtra("phoneNumber", phoneNumber)
            intent.putExtra("email", email)
            startActivity(intent)
        }


        // Delete driver button
        deleteDriverButton.setOnClickListener {
            if (firebaseKey.isNullOrEmpty()) {
                Toast.makeText(this, "Driver key not available. Cannot delete driver.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Delete Bus Driver")
                .setMessage("Are you sure you want to delete driver \"$driverName\"?")
                .setPositiveButton("Delete") { dialog, _ ->
                    deleteDriverFromFirebase(firebaseKey) // Delete by key
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun deleteDriverFromFirebase(firebaseKey: String) {
        val databaseRef = FirebaseDatabase.getInstance().getReference("drivers")

        databaseRef.child(firebaseKey).removeValue() // Delete with Firebase key
            .addOnSuccessListener {
                Toast.makeText(this, "Bus driver deleted successfully.", Toast.LENGTH_SHORT).show()
                finish() // Close activity and update list
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete bus driver: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
