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

class StudentLogin : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_studentlogin)

        // Initialize Firebase Authentication
        auth = Firebase.auth
        database = Firebase.database.reference

        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val registerTextView = findViewById<TextView>(R.id.registerTextView)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            // Basic input validation
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Sign in with Firebase Authentication
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Sign in success, update UI with the signed-in user's information
                        val user = auth.currentUser
                        Log.d("StudentLogin", "Login successful: ${user?.uid}")

                        // Get the user's role from the database
                        database.child("students").child(user!!.uid)
                            .addListenerForSingleValueEvent(object :
                                com.google.firebase.database.ValueEventListener {
                                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                                    if (snapshot.exists()) {
                                        val role = snapshot.child("role").getValue(String::class.java)
                                        val storedEmail = snapshot.child("email").getValue(String::class.java)
                                        val storedMatric = snapshot.child("matricNumber").getValue(String::class.java)
                                        val storedPhone = snapshot.child("phoneNumber").getValue(String::class.java)

                                        Log.d("StudentLogin", "User Role: $role")
                                        if (role == "Student") {
                                            if (email == storedEmail) {
                                                val intent = Intent(
                                                    this@StudentLogin,
                                                    MainActivity::class.java
                                                )
                                                startActivity(intent)
                                                finish()
                                            } else {
                                                Toast.makeText(
                                                    baseContext,
                                                    "Login failed. Invalid Credentials",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                auth.signOut()
                                            }
                                        } else {
                                            Toast.makeText(
                                                baseContext,
                                                "Invalid role. Please contact administrator.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            auth.signOut()
                                        }

                                    } else {
                                        Toast.makeText(
                                            baseContext,
                                            "User data not found.  Please contact administrator.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        auth.signOut()
                                    }
                                }

                                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                                    Log.e("StudentLogin", "Database Error: ${error.message}")
                                    Toast.makeText(
                                        baseContext,
                                        "Database error: ${error.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    auth.signOut()
                                }
                            })

                    } else {
                        // If sign in fails, display a message to the user.
                        Log.w("StudentLogin", "Login failed: ${task.exception}")
                        Toast.makeText(
                            baseContext, "Login failed. ${task.exception?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        registerTextView.setOnClickListener {
            val intent = Intent(this, StudentRegister::class.java)
            val userRole = intent.getStringExtra("userRole")
            intent.putExtra("userRole", userRole)
            startActivity(intent)
        }
    }
}
