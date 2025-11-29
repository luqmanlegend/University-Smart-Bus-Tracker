package com.example.unimapsmartbustracker

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent // Import Intent

class RouteAPage : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_apage)

        val backToMainButton: Button = findViewById(R.id.backToMainButton)
        val viewScheduleButton: Button = findViewById(R.id.viewScheduleButton) // Find Schedule Button
        val viewBusPathButton: Button = findViewById(R.id.viewBusPathButton) // Find Bus Path button

        backToMainButton.setOnClickListener {
            finish() // This will navigate back to the previous activity
        }

        // Set OnClickListener for the viewScheduleButton
        viewScheduleButton.setOnClickListener {
            val intent = Intent(this, RouteASchedulePage::class.java) // Create intent to start RouteASchedulePage
            startActivity(intent) // Start the activity
        }

        // Set OnClickListener for the viewScheduleButton
        viewBusPathButton.setOnClickListener {
            val intent = Intent(this, RouteABusPath::class.java) // Create intent to start RouteASchedulePage
            startActivity(intent) // Start the activity
        }
    }
}
