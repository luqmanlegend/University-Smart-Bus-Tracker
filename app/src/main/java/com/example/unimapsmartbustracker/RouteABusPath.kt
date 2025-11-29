package com.example.unimapsmartbustracker // Replace with your actual package name

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class RouteABusPath : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assignedroute_a)

        val backButton: Button = findViewById(R.id.backToScheduleButtonA)

        backButton.setOnClickListener {
            finish() // This will navigate back to the previous activity (UniMAPBusSchedulePage)
        }
    }
}