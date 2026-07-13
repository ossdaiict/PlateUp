package com.app.plateup.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.plateup.R
import com.app.plateup.adapters.OrderDetailsAdapter
import com.app.plateup.databinding.ActivityVendorOrderDetailsBinding
import com.app.plateup.models.Notification
import com.app.plateup.models.Order
import com.app.plateup.models.OrderItem
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VendorOrderDetailsActivity : BaseActivity() {

    private lateinit var binding: ActivityVendorOrderDetailsBinding
    private lateinit var database: DatabaseReference
    private lateinit var itemsList: ArrayList<OrderItem>
    private lateinit var adapter: OrderDetailsAdapter
    private var orderId = ""
    private lateinit var currentOrder: Order

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVendorOrderDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomLayout.applySystemInsets(applyTop = false, applyBottom = true)

        database = FirebaseDatabase.getInstance().reference

        orderId = intent.getStringExtra("orderId") ?: ""

        itemsList = ArrayList()
        adapter = OrderDetailsAdapter(
            this,
            itemsList
        )

        binding.orderItemsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.orderItemsRecyclerView.adapter = adapter

        binding.backImage.setOnClickListener { finish() }

        loadOrder()

        binding.primaryActionBtn.setOnClickListener {
            handlePrimaryAction()
        }

        binding.rejectBtn.setOnClickListener {
            showConfirmationDialog(
                title = "Reject Order",
                message = "Are you sure you want to reject this order? This action cannot be undone.",
                positiveButton = "Reject",
                onConfirm = { updateOrderStatus("REJECTED") }
            )
        }

    }

    private fun loadOrder() {
        val orderRef = database.child("orders").child(orderId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val order = snapshot.getValue(Order::class.java) ?: return
                currentOrder = order
                
                // ... UI binding ...

                binding.orderIdText.text = "Order #${order.orderId.takeLast(6)}"
                binding.studentNameText.text = order.studentName
                binding.orderTypeText.text =
                    if (order.orderType == "TAKEAWAY") {
                        "🥡 Takeaway"
                    } else {
                        "🍴 Dine-In"
                    }

                binding.itemsTotalText.text = "₹${order.itemsTotal}"
                binding.grandTotalText.text = "₹${order.totalAmount}"

                if (order.packagingFee > 0) {
                    binding.packagingRow.visibility = View.VISIBLE
                    binding.grandTotalDivider.visibility = View.VISIBLE

                    binding.packagingAmountText.text = "₹${order.packagingFee}"
                } else {
                    binding.packagingRow.visibility = View.GONE
                    binding.grandTotalDivider.visibility = View.GONE
                }

                val formatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                val formattedTime = formatter.format(Date(order.timestamp))
                binding.timeText.text = formattedTime

                binding.statusChip.text = 
                    if (order.status == "AWAITING_PAYMENT") "AWAITING PAYMENT" else order.status

                when (order.status) {
                    "REJECTED", "EXPIRED", "CANCELLED" -> {
                        binding.statusChip.setTextColor(ContextCompat.getColor(this@VendorOrderDetailsActivity, R.color.error))
                        binding.statusChip.setBackgroundResource(R.drawable.bg_close_chip)
                    }
                    "ACCEPTED", "READY", "COLLECTED", "COMPLETED" -> {
                        binding.statusChip.setTextColor(ContextCompat.getColor(this@VendorOrderDetailsActivity, R.color.success))
                        binding.statusChip.setBackgroundResource(R.drawable.bg_open_chip)
                    }
                    "PLACED", "AWAITING_PAYMENT" -> {
                        binding.statusChip.setTextColor(ContextCompat.getColor(this@VendorOrderDetailsActivity, R.color.primary))
                        binding.statusChip.setBackgroundResource(R.drawable.bg_pending_chip)
                    }
                    "PREPARING" -> {
                        binding.statusChip.setTextColor(ContextCompat.getColor(this@VendorOrderDetailsActivity, R.color.admin_auth))
                        binding.statusChip.setBackgroundResource(R.drawable.bg_add_request_chip)
                    }
                }

                itemsList.clear()
                itemsList.addAll(order.items)
                adapter.notifyDataSetChanged()

                updateButtons(order.status)

            }

            override fun onCancelled(error: DatabaseError) {
                showError(error.message)
            }
        }
        registerListener(orderRef, listener)
    }

    private fun updateButtons(status: String) {
        when (status) {
            "PLACED" -> {
                binding.primaryActionBtn.visibility = View.VISIBLE
                binding.primaryActionBtn.text = "Accept Order"
                binding.primaryActionBtn.isEnabled = true
                binding.rejectBtn.visibility = View.VISIBLE
            }
            "AWAITING_PAYMENT" -> {
                binding.primaryActionBtn.visibility = View.VISIBLE
                binding.primaryActionBtn.text = "Awaiting Payment"
                binding.primaryActionBtn.isEnabled = false
                binding.rejectBtn.visibility = View.GONE
            }
            "PREPARING" -> {
                binding.primaryActionBtn.visibility = View.VISIBLE
                binding.primaryActionBtn.text = "Mark Ready"
                binding.primaryActionBtn.isEnabled = true
                binding.rejectBtn.visibility = View.GONE
            }
            "ACCEPTED" -> {
                // Should transition to AWAITING_PAYMENT immediately via backend
                binding.primaryActionBtn.visibility = View.GONE
                binding.rejectBtn.visibility = View.GONE
            }
            else -> {
                binding.primaryActionBtn.visibility = View.GONE
                binding.rejectBtn.visibility = View.GONE
            }
        }
    }

    private fun handlePrimaryAction() {
        when (currentOrder.status) {
            "PLACED" -> updateOrderStatus("ACCEPTED")
            "PREPARING" -> updateOrderStatus("READY")
        }
    }

    private fun updateOrderStatus(status: String) {
        showLoading("Updating order status...")
        val updates = mapOf<String, Any>(
            "status" to status,
            "statusTimestamps/$status" to System.currentTimeMillis()
        )
        database.child("orders").child(orderId).updateChildren(updates)
            .addOnSuccessListener {
                hideLoading()
                if (status == "READY" || status == "COMPLETED") {
                    window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                }
                showSuccess("Order status updated to $status")
            }
            .addOnFailureListener {
                hideLoading()
                showError("Failed to update status: ${it.message}")
            }
    }

}
