package com.example.unimapsmartbustracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class StudentDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_details)

        // Get UI elements
        val studentNameTextView = findViewById<TextView>(R.id.studentNameTextView)
        val matricNumberTextView = findViewById<TextView>(R.id.matricNumberTextView)
        val phoneNumberTextView = findViewById<TextView>(R.id.phoneNumberTextView)
        val emailTextView = findViewById<TextView>(R.id.emailTextView)
        val backToMainButton = findViewById<Button>(R.id.backToMainButton)
        val contactStudentButton = findViewById<Button>(R.id.contactStudentButton)
        val deleteUserButton = findViewById<Button>(R.id.deleteUserButton) // New button

        // Get the student data from the intent
        val studentName = intent.getStringExtra("name")
        val matricNumber = intent.getStringExtra("matricNumber")
        val phoneNumber = intent.getStringExtra("phoneNumber")
        val email = intent.getStringExtra("email")
        val firebaseKey = intent.getStringExtra("firebaseKey") // <-- Get firebase key here

        // Display the student data
        studentNameTextView.text = "Name: $studentName"
        matricNumberTextView.text = "Matric Number: $matricNumber"
        phoneNumberTextView.text = "Phone Number: $phoneNumber"
        emailTextView.text = "Email: $email"

        // Back button
        backToMainButton.setOnClickListener {
            finish()
        }

        // Contact student button
        contactStudentButton.setOnClickListener {
            val intent = Intent(this, ContactStudentActivity::class.java)
            intent.putExtra("name", studentName)
            intent.putExtra("phoneNumber", phoneNumber)
            intent.putExtra("email", email)
            startActivity(intent)
        }


        // Delete user button
        deleteUserButton.setOnClickListener {
            if (firebaseKey.isNullOrEmpty()) {
                Toast.makeText(this, "Student key not available. Cannot delete user.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show confirmation dialog
            AlertDialog.Builder(this)
                .setTitle("Delete Student")
                .setMessage("Are you sure you want to delete student \"$studentName\"?")
                .setPositiveButton("Delete") { dialog, _ ->
                    deleteStudentFromFirebase(firebaseKey)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun deleteStudentFromFirebase(firebaseKey: String) {
        val databaseRef = FirebaseDatabase.getInstance().getReference("students")

        // Use firebaseKey to delete the student node
        databaseRef.child(firebaseKey).removeValue()
            .addOnSuccessListener {
                Toast.makeText(this, "Student deleted successfully.", Toast.LENGTH_SHORT).show()
                finish() // Close this activity and return
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete student: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
