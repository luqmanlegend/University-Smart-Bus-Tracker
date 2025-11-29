package com.example.unimapsmartbustracker

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.regex.Pattern

class StudentRegister : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private val TAG = "StudentRegisterActivity"

    data class Student(
        val name: String = "",
        val email: String = "",
        val matricNumber: String = "",
        val phoneNumber: String = "",
        val role: String = "Student"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_studentregister)

        auth = Firebase.auth
        database = Firebase.database.reference

        val nameEditText = findViewById<EditText>(R.id.registerNameEditText)
        val emailEditText = findViewById<EditText>(R.id.registerEmailEditText)
        val passwordEditText = findViewById<EditText>(R.id.registerPasswordEditText)
        val confirmPasswordEditText = findViewById<EditText>(R.id.confirmPasswordEditText)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val loginTextView = findViewById<TextView>(R.id.loginTextView)
        val matricNumberEditText = findViewById<EditText>(R.id.registerMatricEditText)
        val phoneNumberEditText = findViewById<EditText>(R.id.registerPhoneNumberEditText)

        registerButton.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()
            val matricNumber = matricNumberEditText.text.toString().trim()
            val phoneNumber = phoneNumberEditText.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || matricNumber.isEmpty() || phoneNumber.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isValidEmail(email)) {
                Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check for duplicate matric number and phone number
            checkDuplicateMatricAndPhone(matricNumber, phoneNumber) { isDuplicate ->
                if (isDuplicate) {
                    Toast.makeText(
                        this,
                        "Matric number or phone number already exists",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // If no duplicates, proceed with registration
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this) { task ->
                            if (task.isSuccessful) {
                                val firebaseUser: FirebaseUser? = auth.currentUser
                                val uid: String? = firebaseUser?.uid

                                Log.d(TAG, "createUserWithEmail:success - User UID: $uid")

                                if (uid != null) {
                                    val student = Student(name, email, matricNumber, phoneNumber)
                                    database.child("students").child(uid).setValue(student)
                                        .addOnSuccessListener {
                                            Log.d(
                                                TAG,
                                                "saveStudentData:success - Student data saved to Realtime Database for UID: $uid"
                                            )
                                            Toast.makeText(
                                                this,
                                                "Registration successful. Please login.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            val intent =
                                                Intent(this, StudentLogin::class.java)
                                            intent.flags =
                                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            startActivity(intent)
                                            finish()
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e(
                                                TAG,
                                                "saveStudentData:failure - Failed to save student data for UID: $uid",
                                                e
                                            )
                                            Toast.makeText(
                                                this,
                                                "Registration succeeded but failed to save profile data: ${e.message}",
                                                Toast.LENGTH_LONG
                                            ).show()

                                            firebaseUser.delete().addOnCompleteListener { deleteTask ->
                                                if (deleteTask.isSuccessful) {
                                                    Log.d(
                                                        TAG,
                                                        "Orphaned auth user deleted successfully."
                                                    )
                                                } else {
                                                    Log.w(
                                                        TAG,
                                                        "Failed to delete orphaned auth user.",
                                                        deleteTask.exception
                                                    )
                                                }
                                            }
                                        }
                                } else {
                                    Log.e(
                                        TAG,
                                        "createUserWithEmail:success but UID is null. Cannot save student data."
                                    )
                                    Toast.makeText(
                                        this,
                                        "Registration error: User ID not found.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    firebaseUser?.delete()
                                }

                            } else {
                                Log.w(TAG, "createUserWithEmail:failure", task.exception)
                                Toast.makeText(
                                    this,
                                    "Authentication failed: ${task.exception?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                }
            }
        }

        loginTextView.setOnClickListener {
            val intent = Intent(this, StudentLogin::class.java)
            startActivity(intent)
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun checkDuplicateMatricAndPhone(
        matricNumber: String,
        phoneNumber: String,
        callback: (Boolean) -> Unit
    ) {
        var isDuplicate = false
        //check for duplicate matric
        database.child("students").orderByChild("matricNumber").equalTo(matricNumber)
            .addListenerForSingleValueEvent(object :
                com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (snapshot.exists()) {
                        isDuplicate = true
                        callback(isDuplicate) // Return true if matric number is duplicate
                        return
                    } else {
                        // Check for duplicate phone number in both students and drivers
                        checkDuplicatePhoneNumber(phoneNumber) { phoneIsDuplicate ->
                            isDuplicate = phoneIsDuplicate
                            callback(isDuplicate)
                        }
                    }
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.e(TAG, "Database error checking matric number: ${error.message}")
                    callback(
                        true
                    ) // Return true on error to prevent registration.  Consider if this is the best approach for your use case.
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
