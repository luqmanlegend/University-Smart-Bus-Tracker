package com.example.unimapsmartbustracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class AdminDatabasePage : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admindatabase)

        // Get UI elements
        val backToMainButton = findViewById<Button>(R.id.backToMainButton)
        val studentsDatabaseButton = findViewById<Button>(R.id.viewStudentsButton)
        val busDriversButton = findViewById<Button>(R.id.viewBusDriversButton)
        val viewAssignedRoutesButton = findViewById<Button>(R.id.viewAssignedRoutesButton) // Add this line

        // Set click listener for the "Back to Main" button
        backToMainButton.setOnClickListener {
            val intent = Intent(this, AdminPage::class.java)
            startActivity(intent)
            finish()
        }

        // Set click listener for the "Students" button
        studentsDatabaseButton.setOnClickListener {
            val intent = Intent(this, StudentsDatabaseActivity::class.java)
            startActivity(intent)
        }

        busDriversButton.setOnClickListener {
            val intent = Intent(this, BusDriversDatabaseActivity::class.java)
            startActivity(intent)
        }

        // Set click listener for the "View Assigned Bus Route" button  // Add this block
        viewAssignedRoutesButton.setOnClickListener {
            val intent = Intent(this, ViewAssignedBusRoutesActivity::class.java)
            startActivity(intent)
        }
    }
}
