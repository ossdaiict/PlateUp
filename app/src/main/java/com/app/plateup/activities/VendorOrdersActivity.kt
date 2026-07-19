package com.app.plateup.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.plateup.adapters.CurrentOrdersAdapter
import com.app.plateup.adapters.HistoryOrdersAdapter
import com.app.plateup.databinding.ActivityVendorOrdersBinding
import com.app.plateup.models.Order
import com.google.android.material.tabs.TabLayout
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class VendorOrdersActivity : BaseActivity() {

    private lateinit var binding: ActivityVendorOrdersBinding
    private lateinit var database: DatabaseReference
    private lateinit var preferences: SharedPreferences
    private var canteenId = ""

    private lateinit var currentAdapter: CurrentOrdersAdapter
    private lateinit var historyAdapter: HistoryOrdersAdapter

    private var ongoingOrders = ArrayList<Order>()
    private var historyOrders = ArrayList<Order>()

    private var currentWorkspace = 0 // 0: Current, 1: History
    private var currentWorkflowTab = 0 // 0: Placed, 1: Payment, 2: Preparing, 3: Ready
    private var searchQuery = ""

    private val handler = Handler(Looper.getMainLooper())
    private val ageUpdateRunnable = object : Runnable {
        override fun run() {
            if (currentWorkspace == 0) {
                currentAdapter.notifyItemRangeChanged(0, currentAdapter.itemCount, "PAYLOAD_AGE")
            }
            handler.postDelayed(this, 60000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVendorOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().reference
        preferences = getSharedPreferences("vendor_session", MODE_PRIVATE)

        canteenId = preferences.getString("canteen_id", "")!!

        setupUI()
        loadOrders()

    }

    private fun setupUI() {
        currentAdapter = CurrentOrdersAdapter(this) { order ->
            openOrderDetails(order.orderId)
        }
        historyAdapter = HistoryOrdersAdapter(this) { order ->
            openOrderDetails(order.orderId)
        }

        binding.ordersRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.ordersRecyclerView.adapter = currentAdapter
        binding.ordersRecyclerView.applySystemInsets(applyTop = false, applyBottom = true, useMargin = false)

        binding.backImage.setOnClickListener { finish() }

        setupWorkspaceTabs()
        setupWorkflowTabs()
        setupSearch()
    }

    private fun setupWorkspaceTabs() {
        binding.workspaceTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentWorkspace = tab?.position ?: 0
                updateWorkspaceUI()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupWorkflowTabs() {
        val workflowStatuses = listOf("PLACED", "AWAITING_PAYMENT", "PREPARING", "READY")
        workflowStatuses.forEach { status ->
            val label = when(status) {
                "AWAITING_PAYMENT" -> "Payment"
                "PLACED" -> "Placed"
                "PREPARING" -> "Preparing"
                "READY" -> "Ready"
                else -> status
            }
            binding.workflowTabLayout.addTab(binding.workflowTabLayout.newTab().setText(label))
        }

        binding.workflowTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentWorkflowTab = tab?.position ?: 0
                updateFilteredList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateWorkspaceUI() {
        if (currentWorkspace == 0) {
            binding.searchLayout.visibility = View.VISIBLE
            binding.workflowTabLayout.visibility = View.VISIBLE
            binding.ordersRecyclerView.adapter = currentAdapter
            updateFilteredList()
        } else {
            binding.searchLayout.visibility = View.GONE
            binding.workflowTabLayout.visibility = View.GONE
            binding.ordersRecyclerView.adapter = historyAdapter
            searchQuery = ""
            binding.searchEditText.setText("")
            updateHistoryList()
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s.toString().trim()
                updateFilteredList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadOrders() {
        val ordersRef = database.child("orders").orderByChild("canteenId").equalTo(canteenId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val live = ArrayList<Order>()
                val history = ArrayList<Order>()

                for (child in snapshot.children) {
                    val order = child.getValue(Order::class.java)
                    if (order != null) {
                        if (order.isLiveOrder()) {
                            live.add(order)
                        } else if (order.isPastOrder()) {
                            history.add(order)
                        }
                    }
                }

                ongoingOrders = live
                historyOrders = ArrayList(history.sortedByDescending { it.statusTimestamps["COMPLETED"] ?: it.timestamp }.take(30))

                updateTabCounts()
                if (currentWorkspace == 0) {
                    updateFilteredList()
                } else {
                    updateHistoryList()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showError(error.message)
            }
        }
        registerListener(ordersRef, listener)
    }

    private fun updateTabCounts() {
        val counts = mutableMapOf("PLACED" to 0, "AWAITING_PAYMENT" to 0, "PREPARING" to 0, "READY" to 0)
        ongoingOrders.forEach { order ->
            if (counts.containsKey(order.status)) {
                counts[order.status] = counts[order.status]!! + 1
            }
        }

        val workflowStatuses = listOf("PLACED", "AWAITING_PAYMENT", "PREPARING", "READY")
        workflowStatuses.forEachIndexed { index, status ->
            val count = counts[status] ?: 0
            val label = when(status) {
                "AWAITING_PAYMENT" -> "Payment"
                "PLACED" -> "Placed"
                "PREPARING" -> "Preparing"
                "READY" -> "Ready"
                else -> status
            }
            binding.workflowTabLayout.getTabAt(index)?.text = "$label ($count)"
        }
    }

    private fun updateFilteredList() {
        val status = when(currentWorkflowTab) {
            0 -> "PLACED"
            1 -> "AWAITING_PAYMENT"
            2 -> "PREPARING"
            3 -> "READY"
            else -> "PLACED"
        }

        val filtered = ongoingOrders.filter { it.status == status }
            .filter { 
                it.studentName.contains(searchQuery, ignoreCase = true) || 
                it.orderId.takeLast(6).contains(searchQuery, ignoreCase = true)
            }
            .sortedBy { it.timestamp } // Oldest First

        currentAdapter.submitList(filtered)
        
        binding.emptyStateText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyStateText.text = if (searchQuery.isNotEmpty()) "No results for '$searchQuery'" else "No orders in this stage"
    }

    private fun updateHistoryList() {
        historyAdapter.submitList(historyOrders)
        binding.emptyStateText.visibility = if (historyOrders.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyStateText.text = "No history found"
    }

    private fun openOrderDetails(orderId: String) {
        val intent = Intent(this, VendorOrderDetailsActivity::class.java)
        intent.putExtra("orderId", orderId)
        startActivity(intent)
    }

    override fun onStart() {
        super.onStart()
        handler.post(ageUpdateRunnable)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(ageUpdateRunnable)
    }

    private fun Order.isLiveOrder(): Boolean {
        return status in setOf("PLACED", "AWAITING_PAYMENT", "PREPARING", "READY")
    }

    private fun Order.isPastOrder(): Boolean {
        return status in setOf("COLLECTED", "COMPLETED", "REJECTED", "EXPIRED", "CANCELLED")
    }
}
