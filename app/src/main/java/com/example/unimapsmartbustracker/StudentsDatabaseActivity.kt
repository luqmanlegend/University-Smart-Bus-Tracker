package com.example.unimapsmartbustracker

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.ArrayList

class StudentsDatabaseActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var studentsRecyclerView: RecyclerView
    private lateinit var studentsList: ArrayList<Student>
    private lateinit var adapter: StudentAdapter
    private lateinit var searchEditText: EditText
    private val TAG = "StudentsDatabaseActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_students_database)

        // Initialize Firebase
        database = Firebase.database.reference

        // Initialize UI elements
        studentsRecyclerView = findViewById(R.id.studentsRecyclerView)
        studentsRecyclerView.layoutManager = LinearLayoutManager(this)
        studentsList = ArrayList()
        adapter = StudentAdapter(studentsList)  // Correct Adapter
        studentsRecyclerView.adapter = adapter
        searchEditText = findViewById(R.id.searchEditText)

        loadStudentsData()
        setupSearchFilter()
    }

    private fun loadStudentsData() {
        database.child("students").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                studentsList.clear()
                for (postSnapshot in snapshot.children) {
                    val student = postSnapshot.getValue(Student::class.java)
                    student?.let {
                        // Set the firebase key from the snapshot key
                        it.firebaseKey = postSnapshot.key ?: ""
                        studentsList.add(it)
                    }
                }
                adapter.updateList(studentsList)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to read student data: ${error.message}")
            }
        })
    }

    private fun setupSearchFilter() {
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not needed for this implementation
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Not needed for this implementation
            }

            override fun afterTextChanged(editable: android.text.Editable?) {
                filterStudents(editable.toString())
            }
        })
    }

    private fun filterStudents(query: String) {
        val filteredList = ArrayList<Student>()
        for (student in studentsList) {
            if (student.name.toLowerCase().contains(query.toLowerCase())) {
                filteredList.add(student)
            }
        }
        adapter.updateList(filteredList)
    }

    // Data class for Student with firebaseKey included
    data class Student(
        val name: String = "",
        val email: String = "",
        val matricNumber: String = "",
        val phoneNumber: String = "",
        val role: String = "Student",
        var firebaseKey: String = "" // Firebase key for deletion
    )

    // Adapter for RecyclerView
    class StudentAdapter(private var studentsList: List<Student>) :
        RecyclerView.Adapter<StudentAdapter.StudentViewHolder>() {

        class StudentViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val nameTextView: android.widget.TextView = itemView.findViewById(R.id.nameTextView)
            val matricNumberTextView: android.widget.TextView =
                itemView.findViewById(R.id.matricNumberTextView)
            val phoneNumberTextView: android.widget.TextView =
                itemView.findViewById(R.id.phoneNumberTextView)
            val emailTextView: android.widget.TextView = itemView.findViewById(R.id.emailTextView)
            val itemViewLayout = itemView //added this
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): StudentViewHolder {
            val itemView = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_student, parent, false) // Use item_student.xml
            return StudentViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
            val currentStudent = studentsList[position]
            holder.nameTextView.text = "Name: ${currentStudent.name}"
            holder.matricNumberTextView.text = "Matric Number: ${currentStudent.matricNumber}"
            holder.phoneNumberTextView.text = "Phone Number: ${currentStudent.phoneNumber}"
            holder.emailTextView.text = "Email: ${currentStudent.email}"

            // Pass firebaseKey to StudentDetailsActivity for deletion
            holder.itemViewLayout.setOnClickListener {
                val context = holder.itemView.context
                val intent = Intent(context, StudentDetailsActivity::class.java)
                intent.putExtra("name", currentStudent.name)
                intent.putExtra("matricNumber", currentStudent.matricNumber)
                intent.putExtra("phoneNumber", currentStudent.phoneNumber)
                intent.putExtra("email", currentStudent.email)
                intent.putExtra("firebaseKey", currentStudent.firebaseKey) // Pass Firebase key here
                context.startActivity(intent)
            }
        }

        override fun getItemCount() = studentsList.size

        fun updateList(newList: List<Student>) {
            studentsList = newList
            notifyDataSetChanged()
        }
    }
}
