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

class BusDriversDatabaseActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var busDriversRecyclerView: RecyclerView
    private lateinit var busDriversList: ArrayList<BusDriver>
    private lateinit var adapter: BusDriverAdapter
    private lateinit var searchEditText: EditText
    private val TAG = "BusDriversDatabaseActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_busdrivers_database)

        // Initialize Firebase
        database = Firebase.database.reference

        // Initialize UI elements
        busDriversRecyclerView = findViewById(R.id.busDriversRecyclerView)
        busDriversRecyclerView.layoutManager = LinearLayoutManager(this)
        busDriversList = ArrayList()
        adapter = BusDriverAdapter(busDriversList)
        busDriversRecyclerView.adapter = adapter
        searchEditText = findViewById(R.id.searchEditText)

        loadBusDriversData()
        setupSearchFilter()
    }

    private fun loadBusDriversData() {
        database.child("drivers").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                busDriversList.clear()
                for (postSnapshot in snapshot.children) {
                    val busDriver = postSnapshot.getValue(BusDriver::class.java)
                    busDriver?.let {
                        // Filter by role here
                        if (it.role == "Driver") {
                            it.firebaseKey = postSnapshot.key ?: "" // Set firebaseKey here
                            busDriversList.add(it)
                        }
                    }
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to read driver data: ${error.message}")
            }
        })
    }

    private fun setupSearchFilter() {
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Not needed
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Not needed
            }

            override fun afterTextChanged(editable: android.text.Editable?) {
                filterBusDrivers(editable.toString())
            }
        })
    }

    private fun filterBusDrivers(query: String) {
        val filteredList = ArrayList<BusDriver>()
        for (busDriver in busDriversList) {
            if (busDriver.name.toLowerCase().contains(query.toLowerCase())) {
                filteredList.add(busDriver)
            }
        }
        adapter.updateList(filteredList)
    }

    // Data class for BusDriver with firebaseKey
    data class BusDriver(
        val name: String = "",
        val idNumber: String = "",
        val phoneNumber: String = "",
        val email: String = "",
        val role: String = "Driver",
        val route: String = "",
        var firebaseKey: String = "" // Add this field for deletion
    )

    // Adapter for RecyclerView
    class BusDriverAdapter(private var busDriversList: List<BusDriver>) :
        RecyclerView.Adapter<BusDriverAdapter.BusDriverViewHolder>() {

        class BusDriverViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val nameTextView: android.widget.TextView = itemView.findViewById(R.id.nameTextView)
            val idNumberTextView: android.widget.TextView = itemView.findViewById(R.id.idNumberTextView)
            val phoneNumberTextView: android.widget.TextView = itemView.findViewById(R.id.phoneNumberTextView)
            val emailTextView: android.widget.TextView = itemView.findViewById(R.id.emailTextView)
            val itemViewLayout: android.view.View = itemView
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): BusDriverViewHolder {
            val itemView = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_busdrivers, parent, false)
            return BusDriverViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: BusDriverViewHolder, position: Int) {
            val currentBusDriver = busDriversList[position]
            holder.nameTextView.text = "Name: ${currentBusDriver.name}"
            holder.idNumberTextView.text = "ID Number: ${currentBusDriver.idNumber}"
            holder.phoneNumberTextView.text = "Phone Number: ${currentBusDriver.phoneNumber}"
            holder.emailTextView.text = "Email: ${currentBusDriver.email}"

            // Pass firebaseKey to details activity
            holder.itemViewLayout.setOnClickListener {
                val context = holder.itemView.context
                val intent = Intent(context, BusDriverDetailsActivity::class.java)
                intent.putExtra("name", currentBusDriver.name)
                intent.putExtra("idNumber", currentBusDriver.idNumber)
                intent.putExtra("phoneNumber", currentBusDriver.phoneNumber)
                intent.putExtra("email", currentBusDriver.email)
                intent.putExtra("route", currentBusDriver.route)
                intent.putExtra("firebaseKey", currentBusDriver.firebaseKey) // Pass key here
                context.startActivity(intent)
            }
        }

        override fun getItemCount() = busDriversList.size

        fun updateList(newList: List<BusDriver>) {
            busDriversList = newList
            notifyDataSetChanged()
        }
    }
}
