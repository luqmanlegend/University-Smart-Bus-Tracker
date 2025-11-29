package com.example.unimapsmartbustracker

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import android.util.Log
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider

class ChangePassword : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val TAG = "ChangePassword"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_changepassword)

        // Initialize Firebase Authentication
        auth = Firebase.auth

        val oldPasswordEditText = findViewById<EditText>(R.id.oldPasswordEditText)
        val newPasswordEditText = findViewById<EditText>(R.id.newPasswordEditText)
        val confirmNewPasswordEditText = findViewById<EditText>(R.id.confirmNewPasswordEditText)
        val changePasswordButton = findViewById<Button>(R.id.changePasswordButton)

        changePasswordButton.setOnClickListener {
            val oldPassword = oldPasswordEditText.text.toString().trim()
            val newPassword = newPasswordEditText.text.toString().trim()
            val confirmNewPassword = confirmNewPasswordEditText.text.toString().trim()

            // Basic input validation
            if (oldPassword.isEmpty()) {
                Toast.makeText(this, "Please enter your old password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPassword.isEmpty() || confirmNewPassword.isEmpty()) {
                Toast.makeText(this, "Please enter your new password and confirm it", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPassword != confirmNewPassword) {
                Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPassword.length < 6) {  // Firebase requires at least 6 characters
                Toast.makeText(this, "New password must be at least 6 characters long", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Get the current user
            val user = auth.currentUser
            if (user != null) {
                // Create an AuthCredential using the user's email and old password.
                val credential = EmailAuthProvider.getCredential(user.email!!, oldPassword)

                // Re-authenticate the user with the credential
                user.reauthenticate(credential)
                    .addOnCompleteListener { reAuthTask ->
                        if (reAuthTask.isSuccessful) {
                            // User re-authenticated successfully.  Now, change the password.
                            user.updatePassword(newPassword)
                                .addOnCompleteListener { updatePasswordTask ->
                                    if (updatePasswordTask.isSuccessful) {
                                        Log.d(TAG, "Password changed successfully.")
                                        Toast.makeText(
                                            baseContext,
                                            "Password changed successfully. Please log in with your new password.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        finish() // Close this activity and go back to the previous activity
                                    } else {
                                        Log.e(TAG, "Error changing password: ${updatePasswordTask.exception?.message}")
                                        Toast.makeText(
                                            baseContext,
                                            "Failed to change password: ${updatePasswordTask.exception?.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                        } else {
                            // User re-authentication failed.
                            Log.e(TAG, "Re-authentication failed: ${reAuthTask.exception?.message}")
                            Toast.makeText(
                                baseContext,
                                "Incorrect old password. Please enter your correct old password.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            } else {
                // No user is signed in.  This should not normally happen, but handle it to be safe.
                Toast.makeText(this, "No user is currently signed in. Please sign in and try again.", Toast.LENGTH_LONG).show()
                Log.w(TAG, "No user signed in.")
            }
        }
    }
}