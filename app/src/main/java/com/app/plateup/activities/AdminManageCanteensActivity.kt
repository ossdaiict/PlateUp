package com.app.plateup.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.plateup.adapters.AdminCanteensAdapter
import com.app.plateup.databinding.ActivityAdminManageCanteensBinding
import com.app.plateup.models.Canteen
import com.app.plateup.models.MenuItem
import com.google.firebase.database.*

class AdminManageCanteensActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminManageCanteensBinding
    private lateinit var database: DatabaseReference
    private lateinit var adapter: AdminCanteensAdapter
    private val canteensList = ArrayList<Canteen>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminManageCanteensBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().getReference("canteens")

        setupRecyclerView()
        setupSearch()
        loadCanteens()

        binding.backImage.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = AdminCanteensAdapter(this, canteensList) { canteen ->
            val intent = Intent(this, AdminCanteenDetailsActivity::class.java)
            intent.putExtra("canteenId", canteen.id)
            startActivity(intent)
        }
        binding.canteensRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.canteensRecyclerView.adapter = adapter
        binding.canteensRecyclerView.applySystemInsets(applyTop = false, applyBottom = true, useMargin = false)
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
                updateEmptyState()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadCanteens() {
        // Show shimmer
        binding.shimmerViewContainer.startShimmer()
        binding.shimmerViewContainer.visibility = View.VISIBLE
        binding.canteensRecyclerView.visibility = View.GONE
        
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newList = ArrayList<Canteen>()
                val totalCanteens = snapshot.childrenCount
                
                if (totalCanteens == 0L) {
                    binding.shimmerViewContainer.stopShimmer()
                    binding.shimmerViewContainer.visibility = View.GONE
                    binding.canteensRecyclerView.visibility = View.VISIBLE
                    adapter.updateData(newList)
                    updateEmptyState()
                    return
                }

                var processedCanteens = 0
                for (canteenSnapshot in snapshot.children) {
                    val canteen = canteenSnapshot.getValue(Canteen::class.java)
                    if (canteen != null) {
                        // Fetch item count for each canteen
                        val menuRef = FirebaseDatabase.getInstance().getReference("menus").child(canteen.id)
                        menuRef.addValueEventListener(object : ValueEventListener {
                            override fun onDataChange(menuSnapshot: DataSnapshot) {
                                var count = 0
                                for (itemChild in menuSnapshot.children) {
                                    val menuItem = itemChild.getValue(MenuItem::class.java)
                                    if (menuItem?.available == true) {
                                        count++
                                    }
                                }
                                canteen.itemCount = count
                                
                                // Update item in list if already exists, else add
                                val index = newList.indexOfFirst { it.id == canteen.id }
                                if (index != -1) {
                                    newList[index] = canteen
                                } else {
                                    newList.add(canteen)
                                }
                                
                                processedCanteens++
                                // Only hide shimmer when all canteens have been processed at least once
                                if (processedCanteens >= totalCanteens.toInt()) {
                                    binding.shimmerViewContainer.stopShimmer()
                                    binding.shimmerViewContainer.visibility = View.GONE
                                    binding.canteensRecyclerView.visibility = View.VISIBLE
                                    
                                    adapter.updateData(newList)
                                    updateEmptyState()
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {
                                processedCanteens++
                            }
                        })
                    } else {
                        processedCanteens++
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                binding.shimmerViewContainer.stopShimmer()
                binding.shimmerViewContainer.visibility = View.GONE
                showError("Failed to load canteens: ${error.message}")
            }
        })
    }

    private fun updateEmptyState() {
        if (adapter.itemCount == 0) {
            binding.emptyStateText.visibility = View.VISIBLE
        } else {
            binding.emptyStateText.visibility = View.GONE
        }
    }
}