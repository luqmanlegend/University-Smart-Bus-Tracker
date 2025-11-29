package com.example.unimapsmartbustracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import java.util.regex.Pattern // Import for isValidEmail

class BusDriverRegister : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val TAG = "BusDriverRegister"
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_busdriverregister)

        // Initialize Firebase Authentication
        auth = Firebase.auth
        database = Firebase.database.reference

        val nameEditText = findViewById<EditText>(R.id.registerNameEditText)
        val emailEditText = findViewById<EditText>(R.id.registerEmailEditText)
        val phoneNumberEditText = findViewById<EditText>(R.id.registerPhoneNumberEditText) // Find the phone number EditText
        val idNumberEditText = findViewById<EditText>(R.id.registerIdNumberEditText)
        val passwordEditText = findViewById<EditText>(R.id.registerPasswordEditText)
        val confirmPasswordEditText = findViewById<EditText>(R.id.confirmPasswordEditText)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val loginTextView = findViewById<TextView>(R.id.loginTextView)

        // Get the user role (student or driver) if passed from LoginActivity
        val userRole = intent.getStringExtra("userRole")

        registerButton.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val phoneNumber = phoneNumberEditText.text.toString().trim() // Get phone number
            val idNumber = idNumberEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()

            // Basic input validation
            if (name.isEmpty() || email.isEmpty() || phoneNumber.isEmpty() || idNumber.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) { // Added phoneNumber check
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if (!isValidEmail(email)) {
                Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check for unique ID Number and Phone Number
            checkDuplicateIdAndPhone(idNumber, phoneNumber) { isDuplicate ->
                if (isDuplicate) {
                    Toast.makeText(
                        this@BusDriverRegister,
                        "ID Number or Phone Number already exists. Please choose a different one.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // ID number and Phone Number are unique, proceed with registration
                    registerNewDriver(name, email, phoneNumber, idNumber, password)
                }
            }
        }

        loginTextView.setOnClickListener {
            val intent = Intent(this, BusDriverLogin::class.java)
            // Pass the user role back to the login activity (optional)
            intent.putExtra("userRole", userRole)
            startActivity(intent)
        }
    }

    private fun registerNewDriver(name: String, email: String, phoneNumber: String, idNumber: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    val user = auth.currentUser
                    Log.d(TAG, "Registration successful: ${user?.uid}")
                    Toast.makeText(baseContext, "Registration successful.", Toast.LENGTH_SHORT).show()

                    // Store all user data in Realtime Database
                    if (user != null) {
                        val driverData =
                            BusDriverData(name, email, phoneNumber, idNumber, "Driver") // Include role here
                        database.child("drivers").child(user.uid).setValue(driverData)
                            .addOnSuccessListener {
                                Log.d(TAG, "Driver data saved to database")
                                val intent =
                                    Intent(this, BusDriverLogin::class.java) // Change to your BusDriverLogin
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to save driver data: ${e.message}")
                                Toast.makeText(
                                    baseContext,
                                    "Failed to save driver data. Please try again.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                // Consider deleting the user from Authentication if data saving fails
                                user.delete()
                                    .addOnCompleteListener { deleteTask ->
                                        if (deleteTask.isSuccessful) {
                                            Log.d(TAG, "Deleted user after data save failure")
                                        } else {
                                            Log.e(
                                                TAG,
                                                "Failed to delete user: ${deleteTask.exception?.message}"
                                            )
                                        }
                                    }
                            }
                    }

                } else {
                    // If sign in fails, display a message to the user.
                    Log.e(TAG, "Registration failed: ${task.exception?.message}")
                    Toast.makeText(
                        baseContext, "Registration failed. ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    data class BusDriverData(
        val name: String,
        val email: String,
        val phoneNumber: String, // Add phoneNumber to the data class
        val idNumber: String,
        val role: String //Added Role
    ) //Data class to store driver info

    // Helper function to validate email format
    private fun isValidEmail(email: String): Boolean {
        return Pattern.compile(
            "^(([\\w-]+\\.)+[\\w-]+|([a-zA-Z]{1}|[\\w-]{2,}))@"
                    + "((([0-1]?[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\.([0-1]?[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\."
                    + "([0-1]?[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\.([0-1]?[0-9]{1,2}|25[0-5]|2[0-4][0-9])){1}|"
                    + "([a-zA-Z]+[\\w-]+\\.)+[a-zA-Z]{2,4})$"
        ).matcher(email).matches()
    }

    private fun checkDuplicateIdAndPhone(idNumber: String, phoneNumber: String, callback: (Boolean) -> Unit) {
        var isDuplicate = false;
        database.child("drivers").orderByChild("idNumber").equalTo(idNumber)
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (snapshot.exists()) {
                        isDuplicate = true;
                        callback(isDuplicate);
                        return;
                    } else {
                        // Check phone number against both students and drivers
                        checkDuplicatePhoneNumber(phoneNumber) { phoneIsDuplicate ->
                            isDuplicate = phoneIsDuplicate
                            callback(isDuplicate)
                        }
                    }
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.e(TAG, "Database error checking ID Number: ${error.message}")
                    callback(true) // Return true on error to prevent registration
                }
            })
    }

    private fun checkDuplicatePhoneNumber(phoneNumber: String, callback: (Boolean) -> Unit) {
        var isDuplicate = false
        database.child("students").orderByChild("phoneNumber").equalTo(phoneNumber)
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (snapshot.exists()) {
                        isDuplicate = true
                        callback(isDuplicate)
                        return
                    } else {
                        database.child("drivers").orderByChild("phoneNumber").equalTo(phoneNumber)
                            .addListenerForSingleValueEvent(object :
                                com.google.firebase.database.ValueEventListener {
                                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                                    if (snapshot.exists()) {
                                        isDuplicate = true
                                    }
                                    callback(isDuplicate)
                                }

                                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                                    Log.e(
                                        TAG,
                                        "Database error checking phone number in drivers: ${error.message}"
                                    )
                                    callback(
                                        true
                                    ) // Return true on error to prevent registration.
                                }
                            })
                    }
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.e(
                        TAG,
                        "Database error checking phone number in students: ${error.message}"
                    )
                    callback(
                        true
                    ) // Return true on error to prevent registration.
                }
            })
    }
}
