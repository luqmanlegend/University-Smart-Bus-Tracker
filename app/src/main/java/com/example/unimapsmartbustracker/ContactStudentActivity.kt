package com.example.unimapsmartbustracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ContactStudentActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.contact_student_activity)

        // Get UI elements
        val contactByPhoneButton = findViewById<Button>(R.id.contactByPhoneButton)
        val contactByEmailButton = findViewById<Button>(R.id.contactByEmailButton)
        val backButton = findViewById<Button>(R.id.backButton)
        val titleTextView = findViewById<TextView>(R.id.studentsTitleTextView)

        // Get the student data from the intent
        val studentName = intent.getStringExtra("name")
        val studentPhoneNumber = intent.getStringExtra("phoneNumber")
        val studentEmail = intent.getStringExtra("email")

        titleTextView.text = "Contact $studentName";

        // Set click listener for the "Contact by Phone Number" button
        contactByPhoneButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:$studentPhoneNumber") // Use the phone number from intent
            startActivity(intent)
        }

        // Set click listener for the "Contact by Email" button
        contactByEmailButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("mailto:$studentEmail") // Use the email from intent
            startActivity(intent)
        }

        // Set click listener for the "Back" button
        backButton.setOnClickListener {
            finish() // Use finish() to go back to the previous activity
        }
    }
}
