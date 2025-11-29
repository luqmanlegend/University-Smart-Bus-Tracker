package com.example.unimapsmartbustracker // Replace with your actual package name

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class RouteBSchedulePage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_bschedule_page)

        val backButton: Button = findViewById(R.id.backToScheduleButtonB)

        backButton.setOnClickListener {
            finish() // This will navigate back to the previous activity (UniMAPBusSchedulePage)
        }
    }
}