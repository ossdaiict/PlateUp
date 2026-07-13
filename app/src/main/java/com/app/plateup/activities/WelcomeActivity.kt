package com.app.plateup.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.app.plateup.databinding.ActivityWelcomeBinding
import com.app.plateup.repositories.SessionResolver
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var sessionResolver: SessionResolver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionResolver = SessionResolver(this)
        
        // Immediate check: If no Firebase user exists, show Welcome UI instantly.
        if (FirebaseAuth.getInstance().currentUser == null) {
            setupWelcomeUI()
        } else {
            // While a user exists, the ProgressBar (loadingProgress) remains visible by default.
            resolveSession()
        }
    }

    private fun resolveSession() {
        lifecycleScope.launch {
            try {
                val session = sessionResolver.resolveStartup()
                handleSession(session)
            } catch (_: Exception) {
                setupWelcomeUI()
            }
        }
    }

    private fun handleSession(session: SessionResolver.Session) {
        when (session) {
            is SessionResolver.Session.Student -> navigateTo(StudentDashboardActivity::class.java)
            is SessionResolver.Session.Vendor -> navigateTo(VendorDashboardActivity::class.java)
            is SessionResolver.Session.Admin -> navigateTo(AdminDashboardActivity::class.java)
            is SessionResolver.Session.RegistrationRequired -> navigateTo(RegisterActivity::class.java)
            SessionResolver.Session.None -> setupWelcomeUI()
        }
    }

    private fun setupWelcomeUI() {
        binding.loadingProgress.visibility = View.GONE
        binding.welcomeContent.visibility = View.VISIBLE
        setupClickListeners()
    }

    private fun navigateTo(destination: Class<*>) {
        val intent = Intent(this, destination)
        startActivity(intent)
        finish()
        // Disable exit animation for a seamless transition from splash
        overridePendingTransition(0, 0)
    }

    private fun setupClickListeners() {
        binding.studentBtn.setOnClickListener { startActivity(Intent(this, StudentAuthenticationActivity::class.java)) }
        binding.canteenBtn.setOnClickListener { startActivity(Intent(this, VendorAuthenticationActivity::class.java)) }
        binding.adminBtn.setOnClickListener { startActivity(Intent(this, AdminAuthenticationActivity::class.java)) }
    }
}
