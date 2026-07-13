package com.app.plateup.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.app.plateup.R
import com.app.plateup.databinding.ActivityAdminDashboardBinding
import androidx.core.content.edit

class AdminDashboardActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.logoutBtn.applySystemInsets(applyTop = true, applyBottom = true, useMargin = true)

        preferences = getSharedPreferences("admin_session", MODE_PRIVATE)

        binding.logoutBtn.setOnClickListener {
            showConfirmationDialog(
                title = "Sign Out",
                message = "Are you sure you want to sign out?",
                positiveButton = "Sign Out",
                onConfirm = {
                    preferences.edit {
                        putBoolean("admin_logged_in", false)
                    }
                    showSuccess("Signing out...")
                    val intent = Intent(this@AdminDashboardActivity, WelcomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
            )
        }

        binding.createCanteenCard.setOnClickListener {
            startActivity(Intent(this, CreateCanteenActivity::class.java))
        }

        binding.manageCanteensCard.setOnClickListener {
            startActivity(Intent(this, AdminManageCanteensActivity::class.java))
        }

        binding.viewPendingRequestsCard.setOnClickListener {
            startActivity(Intent(this, AdminPendingRequestsActivity::class.java))
        }

        setupPendingRequestsBadge()

        val message = intent.getStringExtra("WELCOME_MESSAGE") ?: intent.getStringExtra("SUCCESS_MESSAGE")
        if (message != null) {
            binding.root.postDelayed({
                showSuccess(message)
            }, 500)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val message = intent.getStringExtra("SUCCESS_MESSAGE")
        if (message != null) {
            showSuccess(message)
        }
    }

    private fun setupPendingRequestsBadge() {
        val requestsRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("menu_requests")
        val pendingQuery = requestsRef.orderByChild("status").equalTo("pending")

        registerListener(pendingQuery, object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val count = snapshot.childrenCount
                if (count > 0) {
                    binding.pendingBadge.visibility = android.view.View.VISIBLE
                    binding.pendingBadge.text = if (count > 9L) "9+" else count.toString()
                } else {
                    binding.pendingBadge.visibility = android.view.View.GONE
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                // Ignore
            }
        })
    }
}