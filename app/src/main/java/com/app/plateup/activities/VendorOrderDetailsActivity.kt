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
import com.app.plateup.models.OrderStatus
import com.app.plateup.models.Student
import com.app.plateup.utils.CanteenUtils
import com.app.plateup.utils.CommunicationUtils
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.os.Handler
import android.os.Looper

class VendorOrderDetailsActivity : BaseActivity() {

    private lateinit var binding: ActivityVendorOrderDetailsBinding
    private lateinit var database: DatabaseReference
    private lateinit var itemsList: ArrayList<OrderItem>
    private lateinit var adapter: OrderDetailsAdapter
    private var orderId = ""
    private lateinit var currentOrder: Order
    private var studentListener: ValueEventListener? = null
    private var studentPhone: String? = null
    private var currentStudentId: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val ageUpdateRunnable = object : Runnable {
        override fun run() {
            if (::currentOrder.isInitialized) {
                updateAgeIndicator()
            }
            handler.postDelayed(this, 60000)
        }
    }

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

        binding.btnContactStudent.setOnClickListener {
            studentPhone?.let { phone ->
                CommunicationUtils.dialNumber(this, phone)
            }
        }

        binding.rejectBtn.setOnClickListener {
            showConfirmationDialog(
                title = "Reject Order",
                message = "Are you sure you want to reject this order? This action cannot be undone.",
                positiveButton = "Reject",
                onConfirm = { updateOrderStatus(OrderStatus.REJECTED) }
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
                    if (order.status == OrderStatus.AWAITING_PAYMENT) "AWAITING PAYMENT" else order.status

                if (order.preparationInstructions.isNotBlank()) {
                    binding.instructionsCard.visibility = View.VISIBLE
                    binding.instructionsText.text = order.preparationInstructions
                } else {
                    binding.instructionsCard.visibility = View.GONE
                }

                when (order.status) {
                    OrderStatus.REJECTED, OrderStatus.EXPIRED, OrderStatus.CANCELLED -> {
                        binding.statusChip.setTextColor(ContextCompat.getColor(this@VendorOrderDetailsActivity, R.color.error))
                        binding.statusChip.setBackgroundResource(R.drawable.bg_close_chip)
                    }
                    OrderStatus.READY, OrderStatus.COLLECTED, OrderStatus.COMPLETED -> {
                        binding.statusChip.setTextColor(ContextCompat.getColor(this@VendorOrderDetailsActivity, R.color.success))
                        binding.statusChip.setBackgroundResource(R.drawable.bg_open_chip)
                    }
                    OrderStatus.PLACED, OrderStatus.AWAITING_PAYMENT -> {
                        binding.statusChip.setTextColor(ContextCompat.getColor(this@VendorOrderDetailsActivity, R.color.primary))
                        binding.statusChip.setBackgroundResource(R.drawable.bg_pending_chip)
                    }
                    OrderStatus.PREPARING -> {
                        binding.statusChip.setTextColor(ContextCompat.getColor(this@VendorOrderDetailsActivity, R.color.admin_auth))
                        binding.statusChip.setBackgroundResource(R.drawable.bg_add_request_chip)
                    }
                }

                itemsList.clear()
                itemsList.addAll(order.items)
                adapter.notifyDataSetChanged()

                updateButtons(order.status)
                updateAgeIndicator()
                updateCommunicationUI(order)

            }

            override fun onCancelled(error: DatabaseError) {
                showError(error.message)
            }
        }
        registerListener(orderRef, listener)
    }

    private fun updateAgeIndicator() {
        if (currentOrder.isPastOrder()) {
            binding.ageIndicator.visibility = View.GONE
            return
        }

        binding.ageIndicator.visibility = View.VISIBLE
        val ageMs = System.currentTimeMillis() - currentOrder.timestamp
        val ageMins = TimeUnit.MILLISECONDS.toMinutes(ageMs)

        binding.ageIndicator.text = if (ageMins == 0L) "Now" else "$ageMins min"

        when {
            ageMins < 10 -> binding.ageIndicator.setBackgroundResource(R.drawable.bg_age_green)
            ageMins < 15 -> binding.ageIndicator.setBackgroundResource(R.drawable.bg_age_amber)
            else -> binding.ageIndicator.setBackgroundResource(R.drawable.bg_age_red)
        }
    }

    private fun updateCommunicationUI(order: Order) {
        if (CanteenUtils.isOrderActiveForCommunication(order.status)) {
            // Priority 1: Phone number embedded in order
            if (order.studentPhone.isNotEmpty()) {
                studentPhone = order.studentPhone
                refreshContactButtonVisibility()
            } else {
                // Priority 2: Fallback to student profile (for legacy orders)
                listenToStudent(order.userId)
            }
        } else {
            stopListeningToStudent()
            studentPhone = null
            refreshContactButtonVisibility()
        }
    }

    private fun refreshContactButtonVisibility() {
        val isActive = ::currentOrder.isInitialized && 
                      CanteenUtils.isOrderActiveForCommunication(currentOrder.status)
        binding.btnContactStudent.visibility = if (isActive && studentPhone != null) View.VISIBLE else View.GONE
    }

    private fun listenToStudent(userId: String) {
        if (userId.isEmpty()) return
        
        if (userId == currentStudentId && studentListener != null) {
            refreshContactButtonVisibility()
            return
        }

        stopListeningToStudent()
        currentStudentId = userId

        val studentRef = database.child("students").child(userId)
        studentListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val student = snapshot.getValue(Student::class.java)
                studentPhone = student?.phoneNumber?.takeIf { it.isNotEmpty() }
                refreshContactButtonVisibility()
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        registerListener(studentRef, studentListener!!)
    }

    private fun stopListeningToStudent() {
        studentListener?.let {
            val studentRef = database.child("students").child(currentStudentId ?: "")
            unregisterListener(studentRef, it)
            studentListener = null
            currentStudentId = null
        }
    }

    private fun Order.isPastOrder(): Boolean {
        return status in setOf(
            OrderStatus.COLLECTED,
            OrderStatus.COMPLETED,
            OrderStatus.REJECTED,
            OrderStatus.EXPIRED,
            OrderStatus.CANCELLED
        )
    }

    override fun onStart() {
        super.onStart()
        handler.post(ageUpdateRunnable)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(ageUpdateRunnable)
        stopListeningToStudent()
    }

    private fun updateButtons(status: String) {
        when (status) {
            OrderStatus.PLACED -> {
                binding.primaryActionBtn.visibility = View.VISIBLE
                binding.primaryActionBtn.text = "Accept Order"
                binding.primaryActionBtn.isEnabled = true
                binding.rejectBtn.visibility = View.VISIBLE
            }
            OrderStatus.AWAITING_PAYMENT -> {
                binding.primaryActionBtn.visibility = View.VISIBLE
                binding.primaryActionBtn.text = "Awaiting Payment"
                binding.primaryActionBtn.isEnabled = false
                binding.rejectBtn.visibility = View.GONE
            }
            OrderStatus.PREPARING -> {
                binding.primaryActionBtn.visibility = View.VISIBLE
                binding.primaryActionBtn.text = "Mark Ready"
                binding.primaryActionBtn.isEnabled = true
                binding.rejectBtn.visibility = View.GONE
            }
            OrderStatus.ACCEPTED -> {
                // Show as Awaiting Payment while backend transitions
                binding.primaryActionBtn.visibility = View.VISIBLE
                binding.primaryActionBtn.text = "Awaiting Payment..."
                binding.primaryActionBtn.isEnabled = false
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
            OrderStatus.PLACED -> updateOrderStatus(OrderStatus.ACCEPTED)
            OrderStatus.PREPARING -> updateOrderStatus(OrderStatus.READY)
        }
    }

    private fun updateOrderStatus(status: String) {
        showLoading("Updating order status...")
        // We set status to ACCEPTED and let the backend transition it to AWAITING_PAYMENT
        // and set the paymentDueAt timestamp.
        val updates = mapOf<String, Any>(
            "status" to status,
            "statusTimestamps/$status" to System.currentTimeMillis()
        )
        database.child("orders").child(orderId).updateChildren(updates)
            .addOnSuccessListener {
                hideLoading()
                if (status == OrderStatus.READY || status == OrderStatus.COMPLETED) {
                    window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                }
                val label = OrderStatus.getDisplayLabel(status)
                showSuccess("Order status changed to \"$label\"")
            }
            .addOnFailureListener {
                hideLoading()
                showError("Failed to update status: ${it.message}")
            }
    }

}
