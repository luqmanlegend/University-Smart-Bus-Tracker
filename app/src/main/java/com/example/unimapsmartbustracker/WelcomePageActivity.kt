package com.example.unimapsmartbustracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class WelcomePageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcomepage)

        val studentButton: Button = findViewById(R.id.studentButton)
        val driverButton: Button = findViewById(R.id.driverButton)

        studentButton.setOnClickListener {
            // Navigate to Student Login/Register
            val intent = Intent(this, StudentLogin::class.java)
            intent.putExtra("userRole", "student")
            startActivity(intent)
        }

        driverButton.setOnClickListener {
            // Navigate to Driver Login
            val intent = Intent(this, BusDriverLogin::class.java);
            intent.putExtra("userRole", "driver")
            startActivity(intent);
        }
    }
}
