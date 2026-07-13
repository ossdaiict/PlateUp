package com.app.plateup.activities

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.plateup.R
import com.app.plateup.adapters.OrderDetailsAdapter
import com.app.plateup.databinding.ActivityStudentOrderDetailsBinding
import com.app.plateup.databinding.ItemOrderTrackingStepBinding
import com.app.plateup.models.Order
import com.app.plateup.models.OrderItem
import com.app.plateup.models.PaymentData
import com.app.plateup.models.PaymentResult
import com.app.plateup.payments.PaymentGatewayFactory
import com.app.plateup.payments.RazorpayGateway
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.functions.FirebaseFunctions
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.razorpay.PaymentResultWithDataListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.razorpay.PaymentData as RazorpayPaymentData

class StudentOrderDetailsActivity : BaseActivity(), PaymentResultWithDataListener {

    private lateinit var binding: ActivityStudentOrderDetailsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var functions: FirebaseFunctions
    private lateinit var itemsList: ArrayList<OrderItem>
    private lateinit var adapter: OrderDetailsAdapter
    private var orderId = ""
    private var currentOrder: Order? = null
    private var serverTimeOffset: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStudentOrderDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomLayout.applySystemInsets(applyTop = false, applyBottom = true)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        functions = FirebaseFunctions.getInstance()

        itemsList = ArrayList()

        orderId = intent.getStringExtra("orderId") ?: ""

        val notificationId = intent.getStringExtra("notificationId") ?: ""
        if (notificationId.isNotEmpty()) {
            markNotificationRead(notificationId)
        }

        adapter = OrderDetailsAdapter(
            this,
            itemsList
        )

        binding.orderItemsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.orderItemsRecyclerView.adapter = adapter

        binding.backImage.setOnClickListener { finish() }

        listenToServerTimeOffset()
        loadOrder()

        binding.payNowBtn.setOnClickListener {
            initiatePayment()
        }

    }

    private fun listenToServerTimeOffset() {
        val ref = database.child(".info/serverTimeOffset")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                serverTimeOffset = snapshot.getValue(Long::class.java) ?: 0
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        registerListener(ref, listener)
    }

    private fun loadOrder() {
        val orderRef = database.child("orders").child(orderId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val order = snapshot.getValue(Order::class.java) ?: return
                currentOrder = order
                // ... (rest of the method)
                binding.orderIdText.text = "Order #${order.orderId.takeLast(6)}"
                binding.canteenNameText.text = order.canteenName
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
                binding.statusChip.text = if (order.status == "AWAITING_PAYMENT") "PAYMENT REQUIRED" else order.status
                updateTracking(order)
                updatePickupCard(order)
                updatePaymentUI(order)

                when (order.status) {
                    "PLACED" -> {
                        binding.statusChip.setTextColor(ContextCompat.getColor(this@StudentOrderDetailsActivity, R.color.primary))
                        binding.statusChip.setBackgroundResource(R.drawable.bg_pending_chip)
                    }
                    "AWAITING_PAYMENT" -> {
                        binding.statusChip.setTextColor(ContextCompat.getColor(this@StudentOrderDetailsActivity, R.color.primary))
                        binding.statusChip.setBackgroundResource(R.drawable.bg_pending_chip)
                    }
                    "ACCEPTED", "READY", "COLLECTED", "COMPLETED" -> {
                        binding.statusChip.setTextColor(ContextCompat.getColor(this@StudentOrderDetailsActivity, R.color.success))
                        binding.statusChip.setBackgroundResource(R.drawable.bg_open_chip)
                    }
                    "PREPARING" -> {
                        binding.statusChip.setTextColor(ContextCompat.getColor(this@StudentOrderDetailsActivity, R.color.admin_auth))
                        binding.statusChip.setBackgroundResource(R.drawable.bg_add_request_chip)
                    }
                    "REJECTED", "CANCELLED", "EXPIRED" -> {
                        binding.statusChip.setTextColor(ContextCompat.getColor(this@StudentOrderDetailsActivity, R.color.error))
                        binding.statusChip.setBackgroundResource(R.drawable.bg_close_chip)
                    }
                }

                itemsList.clear()
                itemsList.addAll(order.items)
                adapter.notifyDataSetChanged()

            }

            override fun onCancelled(error: DatabaseError) {
                showError(error.message)
            }
        }
        registerListener(orderRef, listener)
    }

    private fun updatePaymentUI(order: Order) {
        if (order.status == "AWAITING_PAYMENT") {
            binding.paymentRequiredLayout.visibility = View.VISIBLE
            startCountdown(order.paymentDueAt)
        } else {
            binding.paymentRequiredLayout.visibility = View.GONE
            stopCountdown()
        }
    }

    private fun startCountdown(dueAt: Long) {
        stopCountdown()
        countdownRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis() + serverTimeOffset
                val remaining = dueAt - currentTime
                if (remaining > 0) {
                    val minutes = (remaining / 1000) / 60
                    val seconds = (remaining / 1000) % 60
                    binding.paymentCountdownText.text = String.format(Locale.getDefault(), "%02d:%02d remaining", minutes, seconds)
                    handler.postDelayed(this, 1000)
                } else {
                    binding.paymentCountdownText.text = "00:00 - Expired"
                    binding.payNowBtn.isEnabled = false
                }
            }
        }
        handler.post(countdownRunnable!!)
    }

    private fun stopCountdown() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
    }

    private fun initiatePayment() {
        val order = currentOrder ?: return
        binding.payNowBtn.isEnabled = false
        showLoading("Initializing payment...")

        val cartItems = order.items.map { 
            hashMapOf("menuItemId" to it.menuItemId, "quantity" to it.quantity)
        }
        val data = hashMapOf(
            "cartItems" to cartItems,
            "canteenId" to order.canteenId,
            "orderId" to orderId
        )

        functions.getHttpsCallable("initiatePayment").call(data)
            .addOnSuccessListener { result ->
                hideLoading()
                val res = result.data as Map<*, *>
                val provider = res["provider"] as String
                val gatewayData = res["gatewayData"] as Map<String, String>
                val gatewayOrderId = res["orderId"] as String
                val amount = (res["amount"] as Number).toInt()

                val paymentData = PaymentData(
                    orderId = gatewayOrderId,
                    amount = amount,
                    currency = "INR",
                    gatewayData = gatewayData
                )
                startPayment(provider, paymentData)
            }
            .addOnFailureListener {
                hideLoading()
                binding.payNowBtn.isEnabled = true
                showError("Failed to initiate payment: ${it.message}", retryAction = { initiatePayment() })
            }
    }

    private fun startPayment(provider: String, paymentData: PaymentData) {
        try {
            val gateway = PaymentGatewayFactory.getGateway(provider)
            gateway.initiatePayment(this, paymentData) { result ->
                when (result) {
                    is PaymentResult.Success -> verifyPayment(paymentData, result)
                    is PaymentResult.Cancelled -> {
                        binding.payNowBtn.isEnabled = true
                        showError("Payment cancelled")
                    }
                    is PaymentResult.Failure -> {
                        binding.payNowBtn.isEnabled = true
                        showError("Payment failed: ${result.errorMessage}")
                    }
                }
            }
        } catch (e: Exception) {
            binding.payNowBtn.isEnabled = true
            showError("Error: ${e.message}")
        }
    }

    private fun verifyPayment(paymentData: PaymentData, result: PaymentResult.Success) {
        showLoading("Verifying payment...")
        val data = hashMapOf(
            "orderId" to orderId,
            "paymentData" to hashMapOf(
                "orderId" to paymentData.orderId,
                "amount" to paymentData.amount,
                "currency" to paymentData.currency,
                "gatewayData" to paymentData.gatewayData
            ),
            "resultData" to hashMapOf(
                "paymentId" to result.paymentId,
                "signature" to result.signature
            )
        )

        functions.getHttpsCallable("verifyPayment").call(data)
            .addOnSuccessListener {
                hideLoading()
                window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                showSuccess("Payment successful! Canteen is preparing your order.")
            }
            .addOnFailureListener {
                hideLoading()
                binding.payNowBtn.isEnabled = true
                showError("Verification failed: ${it.message}", retryAction = { verifyPayment(paymentData, result) })
            }
    }

    override fun onPaymentSuccess(paymentId: String?, paymentData: RazorpayPaymentData?) {
        RazorpayGateway.handleSuccess(paymentId, paymentData)
    }

    override fun onPaymentError(errorCode: Int, response: String?, paymentData: RazorpayPaymentData?) {
        RazorpayGateway.handleError(errorCode, response)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCountdown()
    }

    private fun updateTracking(order: Order) {
        val timestamps = order.statusTimestamps.toMutableMap()
        if (!timestamps.containsKey("PLACED") && order.timestamp > 0) {
            timestamps["PLACED"] = order.timestamp
        }

        if (order.status == "REJECTED" || order.status == "CANCELLED" || order.status == "EXPIRED") {
            binding.placedStep.root.visibility = View.VISIBLE
            binding.acceptedStep.root.visibility = View.GONE
            binding.preparingStep.root.visibility = View.GONE
            binding.readyStep.root.visibility = View.GONE
            binding.collectedStep.root.visibility = View.GONE
            binding.rejectedStep.root.visibility = View.VISIBLE

            bindTrackingStep(binding.placedStep, "Placed", timestamps["PLACED"], true, false)
            val rejectionLabel = when (order.status) {
                "CANCELLED" -> "Cancelled (Unpaid)"
                "EXPIRED" -> "Expired"
                else -> "Rejected"
            }
            bindTrackingStep(binding.rejectedStep, rejectionLabel, timestamps[order.status], true, true)
            return
        }

        binding.placedStep.root.visibility = View.VISIBLE
        binding.acceptedStep.root.visibility = View.VISIBLE
        binding.preparingStep.root.visibility = View.VISIBLE
        binding.readyStep.root.visibility = View.VISIBLE
        binding.collectedStep.root.visibility = View.VISIBLE
        binding.rejectedStep.root.visibility = View.GONE

        val currentIndex = trackingStatuses.indexOf(order.status).coerceAtLeast(0)
        bindTrackingStep(binding.placedStep, "Placed", timestamps["PLACED"], currentIndex >= 0, false)
        bindTrackingStep(binding.acceptedStep, "Accepted", timestamps["AWAITING_PAYMENT"], currentIndex >= 1, false)
        bindTrackingStep(binding.preparingStep, "Preparing", timestamps["PREPARING"], currentIndex >= 2, false)
        bindTrackingStep(binding.readyStep, "Ready", timestamps["READY"], currentIndex >= 3, false)
        bindTrackingStep(binding.collectedStep, "Collected", timestamps["COLLECTED"] ?: timestamps["COMPLETED"], currentIndex >= 4, true)
    }

    private fun updatePickupCard(order: Order) {
        if (order.status == "READY") {
            binding.pickupCard.visibility = View.VISIBLE
            binding.pickupCodeText.text = order.pickupCode
            generateQrCode(order.pickupToken)
        } else {
            binding.pickupCard.visibility = View.GONE
        }
    }

    private fun generateQrCode(token: String) {
        if (token.isEmpty()) return
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(token, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            binding.pickupQrImage.setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun bindTrackingStep(
        step: ItemOrderTrackingStepBinding,
        label: String,
        timestamp: Long?,
        isReached: Boolean,
        isLast: Boolean
    ) {
        val color = ContextCompat.getColor(
            this,
            if (isReached) R.color.success else R.color.text_hint
        )
        step.dotText.text = if (isReached) "●" else "○"
        step.dotText.setTextColor(color)
        step.labelText.text = label
        step.labelText.setTextColor(
            ContextCompat.getColor(this, if (isReached) R.color.text_primary else R.color.text_secondary)
        )
        step.labelText.typeface = if (isReached) {
            android.graphics.Typeface.DEFAULT_BOLD
        } else {
            android.graphics.Typeface.DEFAULT
        }
        step.timeText.text = timestamp?.let {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it))
        } ?: "Pending"
        step.lineView.visibility = if (isLast) View.GONE else View.VISIBLE
    }

    private fun markNotificationRead(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        database.child("notifications")
            .child(uid)
            .child(notificationId)
            .child("read")
            .setValue(true)
    }


    companion object {
        private val trackingStatuses = listOf("PLACED", "AWAITING_PAYMENT", "PREPARING", "READY", "COLLECTED", "COMPLETED")
    }

}
