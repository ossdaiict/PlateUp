package com.app.plateup.activities

import android.os.Bundle
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.plateup.adapters.CartDetailsAdapter
import com.app.plateup.databinding.ActivityCartDetailsBinding
import com.app.plateup.models.Canteen
import com.app.plateup.models.CartItem
import com.app.plateup.models.PaymentData
import com.app.plateup.models.PaymentResult
import com.app.plateup.models.Student
import com.app.plateup.payments.PaymentGatewayFactory
import com.app.plateup.payments.RazorpayGateway
import com.app.plateup.utils.CanteenUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.functions.FirebaseFunctions
import com.razorpay.PaymentResultWithDataListener
import com.razorpay.PaymentData as RazorpayPaymentData

class CartDetailsActivity : BaseActivity(), PaymentResultWithDataListener {

    private lateinit var binding: ActivityCartDetailsBinding
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var functions: FirebaseFunctions
    private lateinit var cartItems: ArrayList<CartItem>
    private lateinit var adapter: CartDetailsAdapter
    private var canteenId = ""
    private var canteenName = ""
    private var itemsTotal = 0
    private var packagingTotal = 0
    private var grandTotal = 0
    private var takeawayAllowed = true
    private var packagingFeePerItem = 0
    private var studentName = ""
    private var isCanteenOpen = true

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCartDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomLayout.applySystemInsets(applyTop = false, applyBottom = true)

        database = FirebaseDatabase.getInstance().reference
        auth = FirebaseAuth.getInstance()
        functions = FirebaseFunctions.getInstance()
        
        cartItems = ArrayList()
        canteenId = intent.getStringExtra("canteenId")!!
        canteenName = intent.getStringExtra("canteenName")!!
        packagingFeePerItem = intent.getIntExtra("canteenPackagingFee", 0)

        binding.titleText.text = canteenName

        adapter = CartDetailsAdapter(
            this,
            cartItems,
            onIncreaseClick = { cartItem -> increaseQuantity(cartItem) },
            onDecreaseClick = { cartItem -> decreaseQuantity(cartItem) }
        )

        binding.cartItemsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.cartItemsRecyclerView.adapter = adapter

        loadCart()
        listenToCanteenAvailability()

        binding.orderTypeGroup.setOnCheckedChangeListener { _, _ -> updateTotals() }
        binding.backImage.setOnClickListener { finish() }
        binding.checkoutBtn.setOnClickListener { placeOrder() }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun listenToCanteenAvailability() {
        val canteenRef = database.child("canteens").child(canteenId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val canteen = snapshot.getValue(Canteen::class.java) ?: return
                val uiState = CanteenUtils.getUiState(this@CartDetailsActivity, canteen)
                
                isCanteenOpen = uiState.isOpen
                
                binding.checkoutBtn.isEnabled = uiState.checkoutEnabled
                if (!uiState.isOpen) {
                    binding.checkoutBtn.text = "Canteen Closed"
                    binding.canteenWarningText.visibility = View.VISIBLE
                    binding.canteenWarningText.text = uiState.checkoutWarning
                } else {
                    binding.checkoutBtn.text = "Place Order"
                    binding.canteenWarningText.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showError(error.message)
            }
        }
        registerListener(canteenRef, listener)
    }

    private fun loadCart() {
        val uid = auth.currentUser?.uid ?: return
        val cartRef = database.child("carts").child(uid).child(canteenId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                cartItems.clear()
                for (child in snapshot.children) {
                    val cartItem = child.getValue(CartItem::class.java)?.apply {
                        // Older carts were keyed by menu item ID without also
                        // storing menuItemId in the value.
                        if (menuItemId.isBlank()) menuItemId = child.key ?: ""
                    }
                    if (cartItem != null) cartItems.add(cartItem)
                }

                calculateItemsTotal()
                checkTakeawayAvailability()
                updateTotals()
                adapter.notifyDataSetChanged()

                if (!takeawayAllowed) binding.dineInRadio.isChecked = true
                if (cartItems.isEmpty()) finish()
            }

            override fun onCancelled(error: DatabaseError) {
                showError(error.message)
            }
        }
        registerListener(cartRef, listener)
    }

    private fun calculateItemsTotal() {
        itemsTotal = cartItems.sumOf { it.price * it.quantity }
        binding.itemsTotalText.text = "₹$itemsTotal"
    }

    private fun checkTakeawayAvailability() {
        takeawayAllowed = cartItems.all { it.takeawayAvailable }
        if (!takeawayAllowed) binding.dineInRadio.isChecked = true
        binding.takeawayRadio.isEnabled = takeawayAllowed
        binding.takeawayWarningText.visibility = if (takeawayAllowed) View.GONE else View.VISIBLE
    }

    private fun calculatePackaging() {
        packagingTotal = 0
        if (binding.takeawayRadio.isChecked) {
            val packagedItems = cartItems.sumOf { it.quantity }
            packagingTotal = packagingFeePerItem * packagedItems
        }
    }

    private fun updateTotals() {
        calculatePackaging()
        grandTotal = itemsTotal + packagingTotal
        binding.packagingAmountText.text = "₹$packagingTotal"
        binding.grandTotalText.text = "₹$grandTotal"
        if (binding.takeawayRadio.isChecked) {
            binding.packagingRow.visibility = View.VISIBLE
            binding.grandTotalDivider.visibility = View.VISIBLE
        } else {
            binding.packagingRow.visibility = View.GONE
            binding.grandTotalDivider.visibility = View.GONE
        }
    }

    private fun increaseQuantity(cartItem: CartItem) {
        val uid = auth.currentUser?.uid ?: return
        database.child("carts").child(uid).child(canteenId).child(cartItem.menuItemId).child("quantity")
            .setValue(cartItem.quantity + 1)
    }

    private fun decreaseQuantity(cartItem: CartItem) {
        val uid = auth.currentUser?.uid ?: return
        val cartRef = database.child("carts").child(uid).child(canteenId).child(cartItem.menuItemId)
        val updatedQuantity = cartItem.quantity - 1
        if (updatedQuantity <= 0) cartRef.removeValue() else cartRef.child("quantity").setValue(updatedQuantity)
    }

    private fun placeOrder() {
        val uid = auth.currentUser?.uid ?: return
        
        showConfirmationDialog(
            title = "Place Order",
            message = "Confirm order for ₹$grandTotal?",
            positiveButton = "Place Order",
            onConfirm = {
                binding.checkoutBtn.isEnabled = false
                showLoading("Placing your order...")

                database.child("students").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val student = snapshot.getValue(Student::class.java)
                        studentName = student?.name ?: "Student"
                        
                        val orderItems = ArrayList<Map<String, Any>>()
                        for (cartItem in cartItems) {
                            orderItems.add(hashMapOf(
                                "menuItemId" to cartItem.menuItemId,
                                "name" to cartItem.name,
                                "category" to cartItem.category,
                                "price" to cartItem.price,
                                "quantity" to cartItem.quantity,
                                "takeawayAvailable" to cartItem.takeawayAvailable
                            ))
                        }

                        val orderType = if (binding.takeawayRadio.isChecked) "TAKEAWAY" else "DINE_IN"
                        val orderDetails = hashMapOf(
                            "canteenId" to canteenId,
                            "canteenName" to canteenName,
                            "totalAmount" to grandTotal,
                            "packagingFee" to packagingTotal,
                            "itemsTotal" to itemsTotal,
                            "orderType" to orderType,
                            "items" to orderItems,
                            "studentName" to studentName
                        )

                        val data = hashMapOf("orderDetails" to orderDetails)
                        
                        val call = functions.getHttpsCallable("placeOrder")
                        call.call(data)
                            .addOnSuccessListener { result ->
                                hideLoading()
                                val res = result.data as Map<*, *>
                                val orderId = res["orderId"] as String
                                
                                showSuccess("Order placed successfully! 🎉")
                                val intent = android.content.Intent(this@CartDetailsActivity, StudentOrderDetailsActivity::class.java)
                                intent.putExtra("orderId", orderId)
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener {
                                hideLoading()
                                binding.checkoutBtn.isEnabled = true
                                showError("Failed to place order: ${it.message}", retryAction = { placeOrder() })
                            }
                    }
                    override fun onCancelled(error: DatabaseError) {
                        hideLoading()
                        binding.checkoutBtn.isEnabled = true
                        showError(error.message)
                    }
                })
            }
        )
    }

    private fun startPayment(provider: String, paymentData: PaymentData) {
        // This will now be handled in StudentOrderDetailsActivity
    }

    private fun handlePaymentSuccess(paymentData: PaymentData, result: PaymentResult.Success) {
        // This will now be handled in StudentOrderDetailsActivity
    }

    override fun onPaymentSuccess(paymentId: String?, paymentData: RazorpayPaymentData?) {
        RazorpayGateway.handleSuccess(paymentId, paymentData)
    }

    override fun onPaymentError(errorCode: Int, response: String?, paymentData: RazorpayPaymentData?) {
        RazorpayGateway.handleError(errorCode, response)
    }

}
