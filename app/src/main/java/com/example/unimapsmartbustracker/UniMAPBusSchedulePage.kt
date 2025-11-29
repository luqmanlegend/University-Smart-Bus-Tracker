package com.example.unimapsmartbustracker

import android.os.Bundle
import android.content.Intent
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class UniMAPBusSchedulePage : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_uni_mapbus_schedule_page)

        val routeAButton: Button = findViewById(R.id.routeAButton)
        val routeBButton: Button = findViewById(R.id.routeBButton)
        val backButton: Button = findViewById(R.id.backButton)

        routeAButton.setOnClickListener {
            val intent = Intent(this, RouteAPage::class.java)
            startActivity(intent)
        }

        routeBButton.setOnClickListener {
            val intent = Intent(this, RouteBPage::class.java)
            startActivity(intent)
        }

        backButton.setOnClickListener {
            finish() // This will navigate back to the previous activity (MainActivity)
        }
    }
}