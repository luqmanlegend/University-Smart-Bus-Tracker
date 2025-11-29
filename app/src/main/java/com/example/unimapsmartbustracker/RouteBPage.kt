package com.example.unimapsmartbustracker

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent // Import Intent

class RouteBPage : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_bpage)

        val backToMainButton: Button = findViewById(R.id.backToMainButton)
        val viewScheduleButton: Button = findViewById(R.id.viewScheduleButton) // Find the new button
        val viewBusPathButton: Button = findViewById(R.id.viewBusPathButton) // Find Bus Path button

        backToMainButton.setOnClickListener {
            finish() // This will navigate back to the previous activity
        }

        // Set OnClickListener for the viewScheduleButton
        viewScheduleButton.setOnClickListener {
            val intent = Intent(this, RouteBSchedulePage::class.java) // Create intent to start RouteASchedulePage
            startActivity(intent) // Start the activity
        }

        // Set OnClickListener for the viewScheduleButton
        viewBusPathButton.setOnClickListener {
            val intent = Intent(this, RouteBBusPath::class.java) // Create intent to start RouteASchedulePage
            startActivity(intent) // Start the activity
        }
    }
}
