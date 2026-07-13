package com.app.plateup.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.app.plateup.R
import com.app.plateup.repositories.SessionResolver
import com.app.plateup.databinding.ActivityAdminAuthenticationBinding
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.core.content.edit

class AdminAuthenticationActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminAuthenticationBinding
    private lateinit var database: DatabaseReference
    private lateinit var auth: com.google.firebase.auth.FirebaseAuth
    private lateinit var functions: com.google.firebase.functions.FirebaseFunctions
    private lateinit var sessionResolver: SessionResolver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAdminAuthenticationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.adminLoginBtn.applySystemInsets(applyTop = false, applyBottom = true, useMargin = true)

        database = FirebaseDatabase.getInstance().reference
        auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
        sessionResolver = SessionResolver(this)

        lifecycleScope.launch {
            try {
                sessionResolver.beginAnonymousPortalSession()
            } catch (e: Exception) {
                showError("Auth initialization failed: ${e.message}")
            }
        }

        binding.backImage.setOnClickListener { finish() }

        binding.adminLoginBtn.setOnClickListener {
            val enteredCode = binding.adminCodeInput.text.toString().trim()
            if (enteredCode.isEmpty()) {
                showError("Please enter admin code to proceed!")
                return@setOnClickListener
            }

            if (auth.currentUser == null) {
                showError("Authentication not initialized. Please try again.")
                return@setOnClickListener
            }

            showLoading("Authenticating...")

            val data = hashMapOf("adminCode" to enteredCode)

            functions.getHttpsCallable("adminLogin")
                .call(data)
                .addOnSuccessListener {
                    hideLoading()
                    sessionResolver.saveAdminSession(auth.currentUser!!.uid)
                    
                    val intent = Intent(this@AdminAuthenticationActivity, AdminDashboardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("WELCOME_MESSAGE", "Welcome, Admin!")
                    }
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    hideLoading()
                    showError(e.message ?: "Authentication failed")
                }
        }
    }
}
