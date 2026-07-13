package com.app.plateup.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.plateup.R
import com.app.plateup.adapters.MenuRequestsAdapter
import com.app.plateup.databinding.ActivityAdminPendingRequestsBinding
import com.app.plateup.models.MenuItem
import com.app.plateup.models.MenuRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdminPendingRequestsActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminPendingRequestsBinding
    private lateinit var database: DatabaseReference
    private lateinit var requestsList: ArrayList<MenuRequest>
    private lateinit var adapter: MenuRequestsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAdminPendingRequestsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().reference
        requestsList = ArrayList()

        adapter = MenuRequestsAdapter(
            this,
            requestsList,
            onApproveClick = { request ->
                approveRequest(request)
            },
            onRejectClick = { request ->
                rejectRequest(request)
            }
        )

        binding.pendingRequestsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.pendingRequestsRecyclerView.adapter = adapter
        binding.pendingRequestsRecyclerView.applySystemInsets(applyTop = false, applyBottom = true, useMargin = false)

        loadRequests()

        binding.backImage.setOnClickListener { finish() }

    }

    private fun loadRequests() {
        val requestQuery = database.child("menu_requests").orderByChild("status").equalTo("pending")
        val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    requestsList.clear()
                    for (child in snapshot.children) {
                        val request = child.getValue(MenuRequest::class.java)
                        if (request != null) {
                            requestsList.add(request)
                        }
                    }
                    if (requestsList.isEmpty()) {
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.pendingRequestsRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyStateText.visibility = View.GONE
                    binding.pendingRequestsRecyclerView.visibility = View.VISIBLE
                }
                adapter.notifyDataSetChanged()
                    binding.emptyStateText.visibility =
                        if (requestsList.isEmpty()) {
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


    private fun approveRequest(request: MenuRequest) {
        val menuItem = request.newMenuItem ?: return
        lifecycleScope.launch {
            try {
                // If it's an update, we should preserve the current ratings
                if (request.requestType == "update") {
                    val currentSnapshot = database.child("menus")
                        .child(menuItem.canteenId)
                        .child(menuItem.id)
                        .get().await()
                    
                    val currentItem = currentSnapshot.getValue(MenuItem::class.java)
                    if (currentItem != null) {
                        // Create a new MenuItem with updated fields but preserved ratings
                        val itemToSave = menuItem.copy(
                            averageRating = currentItem.averageRating,
                            reviewCount = currentItem.reviewCount
                        )
                        database.child("menus")
                            .child(itemToSave.canteenId)
                            .child(itemToSave.id)
                            .setValue(itemToSave)
                            .await()
                    } else {
                        database.child("menus")
                            .child(menuItem.canteenId)
                            .child(menuItem.id)
                            .setValue(menuItem)
                            .await()
                    }
                } else {
                    database.child("menus")
                        .child(menuItem.canteenId)
                        .child(menuItem.id)
                        .setValue(menuItem)
                        .await()
                }

                database.child("menu_requests")
                    .child(request.requestId)
                    .child("status")
                    .setValue("approved")
                    .await()

                showSuccess("Request approved")

            } catch (e: Exception) {
                showError(e.message ?: "Unknown error occurred")
            }
        }
    }

    private fun rejectRequest(request: MenuRequest) {
        lifecycleScope.launch {
            try {
                database.child("menu_requests")
                    .child(request.requestId)
                    .child("status")
                    .setValue("rejected")
                    .await()

                showSuccess("Request rejected")

            } catch (e: Exception) {
                showError(e.message ?: "Unknown error occurred")
            }
        }
    }
}