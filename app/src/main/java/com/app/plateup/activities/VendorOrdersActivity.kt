package com.app.plateup.activities

import android.content.Intent
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
import com.app.plateup.adapters.VendorOrdersAdapter
import com.app.plateup.adapters.VendorOrdersAdapter.VendorOrderRow
import com.app.plateup.databinding.ActivityVendorOrdersBinding
import com.app.plateup.models.Order
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class VendorOrdersActivity : BaseActivity() {

    private lateinit var binding: ActivityVendorOrdersBinding
    private lateinit var database: DatabaseReference
    private lateinit var preferences: SharedPreferences
    private lateinit var orderRows: ArrayList<VendorOrderRow>
    private lateinit var adapter: VendorOrdersAdapter
    private var canteenId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVendorOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().reference
        preferences = getSharedPreferences("vendor_session", MODE_PRIVATE)

        canteenId = preferences.getString("canteen_id", "")!!

        database = FirebaseDatabase.getInstance().reference

        orderRows = ArrayList()
        adapter = VendorOrdersAdapter(
            this,
            orderRows,
            onOrderClick = { order ->
                val intent = Intent(this, VendorOrderDetailsActivity::class.java)
                intent.putExtra("orderId", order.orderId)
                startActivity(intent)
            }
        )

        binding.ordersRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.ordersRecyclerView.adapter = adapter
        binding.ordersRecyclerView.applySystemInsets(applyTop = false, applyBottom = true, useMargin = false)

        loadOrders()

        binding.backImage.setOnClickListener { finish() }

    }

    private fun loadOrders() {
        val ordersRef = database.child("orders").orderByChild("canteenId").equalTo(canteenId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val liveOrders = ArrayList<Order>()
                val pastOrders = ArrayList<Order>()

                for (child in snapshot.children) {
                    val order = child.getValue(Order::class.java)
                    if (order != null) {
                        if (order.isLiveOrder()) {
                            liveOrders.add(order)
                        } else if (order.isPastOrder()) {
                            pastOrders.add(order)
                        }
                    }
                }
                
                // ... (rest of sorting and UI logic)

                liveOrders.sortWith(
                    compareBy<Order> { it.liveOrderPriority() }.thenByDescending { it.timestamp }
                )
                pastOrders.sortByDescending { it.timestamp }

                // Limit past orders to most recent 30
                val limitedPastOrders = if (pastOrders.size > 30) pastOrders.take(30) else pastOrders

                orderRows.clear()
                if (liveOrders.isNotEmpty()) {
                    orderRows.add(VendorOrderRow.Header("Ongoing Orders"))
                    orderRows.addAll(liveOrders.map { VendorOrderRow.OrderItem(it) })
                }
                if (limitedPastOrders.isNotEmpty()) {
                    orderRows.add(VendorOrderRow.Header("Past Orders"))
                    orderRows.addAll(limitedPastOrders.map { VendorOrderRow.OrderItem(it) })
                }

                binding.emptyStateText.visibility =
                    if (orderRows.isEmpty()) View.VISIBLE
                    else View.GONE

                adapter.notifyDataSetChanged()

            }

            override fun onCancelled(error: DatabaseError) {
                showError(error.message)
            }
        }
        registerListener(ordersRef, listener)
    }


    private fun Order.isLiveOrder(): Boolean {
        return status in setOf("PLACED", "AWAITING_PAYMENT", "PREPARING", "READY")
    }

    private fun Order.isPastOrder(): Boolean {
        return status in setOf("COLLECTED", "COMPLETED", "REJECTED", "EXPIRED", "CANCELLED")
    }

    private fun Order.liveOrderPriority(): Int {
        return when (status) {
            "PREPARING" -> 0
            "PLACED" -> 1
            "AWAITING_PAYMENT" -> 2
            "READY" -> 3
            else -> 4
        }
    }
}
