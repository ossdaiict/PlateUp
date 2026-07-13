package com.app.plateup.activities

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.plateup.R
import com.app.plateup.adapters.VendorRequestsAdapter
import com.app.plateup.adapters.VendorRequestsAdapter.VendorRequestRow
import com.app.plateup.databinding.ActivityVendorRequestsBinding
import com.app.plateup.models.MenuRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class VendorRequestsActivity : BaseActivity() {

    private lateinit var binding: ActivityVendorRequestsBinding
    private lateinit var database: DatabaseReference
    private lateinit var preferences: SharedPreferences
    private lateinit var requestRows: ArrayList<VendorRequestRow>
    private lateinit var adapter: VendorRequestsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVendorRequestsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().reference
        preferences = getSharedPreferences("vendor_session", MODE_PRIVATE)
        requestRows = ArrayList()

        adapter = VendorRequestsAdapter(this, requestRows)
        binding.requestsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.requestsRecyclerView.adapter = adapter
        binding.requestsRecyclerView.applySystemInsets(applyTop = false, applyBottom = true, useMargin = false)

        val canteenId = preferences.getString("canteen_id", "")!!
        loadRequests(canteenId)

        binding.backImage.setOnClickListener { finish() }

    }

    private fun loadRequests(canteenId: String) {
        val requestQuery = database.child("menu_requests")
            .orderByChild("canteenId")
            .equalTo(canteenId)
        
        val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val pendingRequests = ArrayList<MenuRequest>()
                    val resolvedRequests = ArrayList<MenuRequest>()

                    for (child in snapshot.children) {
                        val request = child.getValue(MenuRequest::class.java)
                        if (request != null) {
                            if (request.status == "pending") {
                                pendingRequests.add(request)
                            } else {
                                resolvedRequests.add(request)
                            }
                        }
                    }

                    pendingRequests.sortByDescending { it.timestamp }
                    resolvedRequests.sortByDescending { it.timestamp }

                    requestRows.clear()
                    if (pendingRequests.isNotEmpty()) {
                        requestRows.add(VendorRequestRow.Header("Pending Requests"))
                        requestRows.addAll(pendingRequests.map { VendorRequestRow.RequestItem(it) })
                    }
                    if (resolvedRequests.isNotEmpty()) {
                        requestRows.add(VendorRequestRow.Header("Resolved Requests"))
                        requestRows.addAll(resolvedRequests.map { VendorRequestRow.RequestItem(it) })
                    }

                    adapter.notifyDataSetChanged()

                    binding.emptyStateText.visibility =
                        if (requestRows.isEmpty()) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                }

                override fun onCancelled(error: DatabaseError) {
                    showError(error.message)
                }

        }
        registerListener(requestQuery, listener)
    }


}
