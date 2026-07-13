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