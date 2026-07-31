package com.app.plateup.activities

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.app.plateup.R
import com.app.plateup.databinding.ActivityStudentDashboardBinding
import com.app.plateup.fragments.CartFragment
import com.app.plateup.fragments.HomeFragment
import com.app.plateup.fragments.OrdersFragment
import com.app.plateup.fragments.ProfileFragment
import com.app.plateup.models.CartItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import com.app.plateup.fragments.OrderFeedbackBottomSheet
import com.app.plateup.models.Order
import com.app.plateup.models.OrderStatus
import android.content.Context
import android.os.Handler
import android.os.Looper

class StudentDashboardActivity : BaseActivity() {

    private lateinit var binding: ActivityStudentDashboardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStudentDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavigation.applySystemInsets(applyTop = false, applyBottom = true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                101
            )
        }

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        replaceFragment(HomeFragment())

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val uid = auth.currentUser?.uid
            if (uid != null) {
                database.child("students").child(uid).child("fcmToken").setValue(token)
            }
        }

        binding.bottomNavigation.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.homeFragment -> {
                    replaceFragment(HomeFragment())
                }
                R.id.ordersFragment -> {
                    replaceFragment(OrdersFragment())
                }
                R.id.cartFragment -> {
                    replaceFragment(CartFragment())
                }
                R.id.profileFragment -> {
                    replaceFragment(ProfileFragment())
                }
            }
            true
        }

        listenToCartBadge()

        val welcomeMessage = intent.getStringExtra("WELCOME_MESSAGE")
        if (welcomeMessage != null) {
            binding.root.postDelayed({
                showSuccess(welcomeMessage)
            }, 500)
        }

        checkAndShowFeedbackPrompt()
    }

    private fun checkAndShowFeedbackPrompt() {
        val uid = auth.currentUser?.uid ?: return
        
        // Find most recent unreviewed collected order
        database.child("orders")
            .orderByChild("userId")
            .equalTo(uid)
            .limitToLast(10) // Check last 10 orders to find one that needs feedback
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val orders = ArrayList<Order>()
                    for (child in snapshot.children) {
                        val order = child.getValue(Order::class.java)
                        if (order != null && 
                            (order.status == OrderStatus.COLLECTED || order.status == OrderStatus.COMPLETED) && 
                            !order.hasFeedback) {
                            orders.add(order)
                        }
                    }

                    if (orders.isNotEmpty()) {
                        // Show for the most recent one
                        orders.sortByDescending { it.timestamp }
                        val targetOrder = orders[0]

                        // Check order-specific cooldown
                        val prefs = getSharedPreferences("feedback_prefs", Context.MODE_PRIVATE)
                        val lastNotNowTime = prefs.getLong("last_not_now_${targetOrder.orderId}", 0L)
                        val currentTime = System.currentTimeMillis()

                        if (currentTime - lastNotNowTime < 24 * 60 * 60 * 1000) {
                            return
                        }
                        
                        // Delay 2 seconds as per plan
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!isFinishing && !isDestroyed) {
                                val bottomSheet = OrderFeedbackBottomSheet.newInstance(targetOrder)
                                bottomSheet.show(supportFragmentManager, "OrderFeedbackBottomSheet")
                            }
                        }, 2000)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun listenToCartBadge() {
        val uid = auth.currentUser?.uid ?: return
        val cartRef = database.child("carts").child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var count = 0
                for (canteenSnapshot in snapshot.children) {
                    for (itemSnapshot in canteenSnapshot.children) {
                        val cartItem = itemSnapshot.getValue(CartItem::class.java)
                        count += cartItem?.quantity ?: 0
                    }
                }
                val badge = binding.bottomNavigation.getOrCreateBadge(R.id.cartFragment)
                badge.backgroundColor = getColor(R.color.cart_badge)
                badge.badgeTextColor = getColor(R.color.text_white)
                if (count > 0) {
                    badge.isVisible = true
                    badge.number = count
                } else {
                    badge.isVisible = false
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showError(error.message)
            }
        }
        registerListener(cartRef, listener)
    }

}