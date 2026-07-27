package com.app.plateup.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.app.plateup.R
import com.app.plateup.databinding.ActivityRegisterBinding
import com.app.plateup.models.Student
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

import com.google.firebase.messaging.FirebaseMessaging

import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class RegisterActivity : BaseActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        // Handle prefilled values if returning from OTP screen
        val prefilledName = intent.getStringExtra("PREFILLED_NAME")
        val prefilledPhone = intent.getStringExtra("PREFILLED_PHONE")
        if (prefilledName != null) binding.nameInput.setText(prefilledName)
        if (prefilledPhone != null) binding.phoneInput.setText(prefilledPhone)

        binding.continueBtn.applySystemInsets(applyTop = false, applyBottom = true, useMargin = true)

        binding.continueBtn.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()
            var phoneNumber = binding.phoneInput.text.toString().trim()

            if (name.isEmpty()) {
                binding.nameInput.error = "Name is required"
                return@setOnClickListener
            }
            if (phoneNumber.isEmpty() || phoneNumber.length < 10) {
                binding.phoneInput.error = "Enter a valid 10-digit phone number"
                return@setOnClickListener
            }

            // Standardize phone number for India (+91)
            if (!phoneNumber.startsWith("+")) {
                phoneNumber = if (phoneNumber.startsWith("91") && phoneNumber.length > 10) {
                    "+$phoneNumber"
                } else {
                    "+91$phoneNumber"
                }
            }

            hideKeyboard()
            binding.continueBtn.isEnabled = false
            startPhoneVerification(name, phoneNumber)
        }
    }

    private fun startPhoneVerification(name: String, phoneNumber: String) {
        showLoading("Sending verification code...")

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // This might happen for auto-verification. 
                // We'll pass the credential code if available, otherwise proceed to OTP screen
                hideLoading()
                val intent = Intent(this@RegisterActivity, OtpVerificationActivity::class.java).apply {
                    putExtra("STUDENT_NAME", name)
                    putExtra("PHONE_NUMBER", phoneNumber)
                    putExtra("AUTO_VERIFIED_CODE", credential.smsCode)
                }
                startActivity(intent)
                finish()
            }

            override fun onVerificationFailed(e: FirebaseException) {
                hideLoading()
                binding.continueBtn.isEnabled = true
                showError(e.message ?: "Verification failed")
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                hideLoading()
                val intent = Intent(this@RegisterActivity, OtpVerificationActivity::class.java).apply {
                    putExtra("VERIFICATION_ID", verificationId)
                    putExtra("RESEND_TOKEN", token)
                    putExtra("STUDENT_NAME", name)
                    putExtra("PHONE_NUMBER", phoneNumber)
                }
                startActivity(intent)
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()
        
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

}
