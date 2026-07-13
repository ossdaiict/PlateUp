package com.app.plateup.activities

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import com.app.plateup.databinding.ActivityVendorDashboardBinding
import com.app.plateup.models.Canteen
import com.app.plateup.models.Order
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import android.util.Base64

class VendorDashboardActivity : BaseActivity() {

    private lateinit var binding: ActivityVendorDashboardBinding
    private lateinit var database: DatabaseReference
    private lateinit var preferences: SharedPreferences
    private lateinit var canteenId: String
    private var newOrdersPulse: AnimatorSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVendorDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.logoutBtn.applySystemInsets(applyTop = false, applyBottom = true, useMargin = true)

        database = FirebaseDatabase.getInstance().reference
        preferences = getSharedPreferences("vendor_session", MODE_PRIVATE)



        canteenId = preferences.getString("canteen_id", "")!!
        val canteenName = preferences.getString("canteen_name", "")!!

        requestNotificationPermission()
        registerVendorFcmToken()
        checkConfigurationStatus()
        listenToLiveOrders()

        binding.titleText.text = canteenName

        binding.logoutBtn.setOnClickListener {
            logoutVendor()
        }

        binding.manageMenuCard.setOnClickListener {
            startActivity(Intent(this, VendorManageMenuActivity::class.java))
        }

        binding.manageOrdersCard.setOnClickListener {
            stopNewOrdersPulse()
            startActivity(Intent(this, VendorOrdersActivity::class.java))
        }

        binding.viewRequestsCard.setOnClickListener {
            startActivity(Intent(this, VendorRequestsActivity::class.java))
        }

        binding.canteenConfigCard.setOnClickListener {
            startActivity(Intent(this, VendorSettingsActivity::class.java))
        }

        binding.scanPickupCard.setOnClickListener {
            startActivity(Intent(this, PickupScannerActivity::class.java))
        }

        val welcomeMessage = intent.getStringExtra("WELCOME_MESSAGE")
        if (welcomeMessage != null) {
            binding.root.postDelayed({
                showSuccess(welcomeMessage)
            }, 500)
        }

    }

    private fun checkConfigurationStatus() {
        val ref = database.child("canteens").child(canteenId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val canteen = snapshot.getValue(Canteen::class.java)
                if (canteen != null && !canteen.configurationComplete) {
                    val intent = Intent(this@VendorDashboardActivity, VendorSettingsActivity::class.java)
                    intent.putExtra("FIRST_SETUP", true)
                    startActivity(intent)
                    finish()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showError(error.message)
            }
        }
        registerListener(ref, listener)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                102
            )
        }
    }

    private fun registerVendorFcmToken() {
        if (canteenId.isEmpty()) {
            return
        }

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            database.child("vendor_credentials")
                .child(canteenId)
                .child("fcmTokens")
                .child(token.toDatabaseKey())
                .setValue(token)
        }
    }

    private fun listenToLiveOrders() {
        if (canteenId.isEmpty()) return

        val ref = database.child("orders").orderByChild("canteenId").equalTo(canteenId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var liveOrdersCount = 0
                var placedOrdersCount = 0

                for (child in snapshot.children) {
                    val order = child.getValue(Order::class.java) ?: continue
                    
                    when (order.status) {
                        "PLACED" -> {
                            liveOrdersCount++
                            placedOrdersCount++
                        }
                        "AWAITING_PAYMENT", "ACCEPTED", "PREPARING", "READY" -> {
                            liveOrdersCount++
                        }
                    }
                }

                updateOrdersBadge(liveOrdersCount, placedOrdersCount)
            }

            override fun onCancelled(error: DatabaseError) {
                showError(error.message)
            }
        }
        registerListener(ref, listener)
    }

    private fun updateOrdersBadge(liveOrdersCount: Int, placedOrdersCount: Int) {
        binding.liveOrdersBadge.visibility =
            if (liveOrdersCount > 0) View.VISIBLE else View.GONE
        binding.liveOrdersBadge.text = liveOrdersCount.toString()

        binding.newOrdersChip.visibility =
            if (placedOrdersCount > 0) View.VISIBLE else View.GONE
        binding.newOrdersChip.text = "$placedOrdersCount new"

        if (placedOrdersCount > 0) {
            startNewOrdersPulse()
        } else {
            stopNewOrdersPulse()
        }
    }

    private fun startNewOrdersPulse() {
        if (newOrdersPulse?.isStarted == true) {
            return
        }

        val scaleX = ObjectAnimator.ofFloat(binding.newOrdersChip, View.SCALE_X, 1f, 1.06f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.newOrdersChip, View.SCALE_Y, 1f, 1.06f, 1f)
        val alpha = ObjectAnimator.ofFloat(binding.newOrdersChip, View.ALPHA, 1f, 0.78f, 1f)

        newOrdersPulse = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1200
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (binding.newOrdersChip.visibility == View.VISIBLE) {
                        start()
                    }
                }
            })
            start()
        }
    }

    private fun stopNewOrdersPulse() {
        newOrdersPulse?.cancel()
        newOrdersPulse = null
        binding.newOrdersChip.scaleX = 1f
        binding.newOrdersChip.scaleY = 1f
        binding.newOrdersChip.alpha = 1f
    }

    override fun onDestroy() {
        stopNewOrdersPulse()
        super.onDestroy()
    }

    private fun logoutVendor() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val token = task.result
            if (task.isSuccessful && token != null && canteenId.isNotEmpty()) {
                database.child("vendor_credentials")
                    .child(canteenId)
                    .child("fcmTokens")
                    .child(token.toDatabaseKey())
                    .removeValue()
            }

            preferences.edit {
                putBoolean("vendor_logged_in", false)
                    .remove("canteen_id")
                    .remove("canteen_name")
            }
            showSuccess("Signing out...")
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
        }
    }

    private fun String.toDatabaseKey(): String {
        return Base64.encodeToString(toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

}
