package com.app.plateup.activities

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.app.plateup.databinding.ActivityOtpVerificationBinding
import com.app.plateup.models.Student
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class OtpVerificationActivity : BaseActivity() {

    private lateinit var binding: ActivityOtpVerificationBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var studentName: String? = null
    private var phoneNumber: String? = null

    private var resendTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        // Extract temporary registration state
        verificationId = intent.getStringExtra("VERIFICATION_ID")
        resendToken = intent.getParcelableExtra("RESEND_TOKEN")
        studentName = intent.getStringExtra("STUDENT_NAME")
        phoneNumber = intent.getStringExtra("PHONE_NUMBER")

        setupUI()
        startResendTimer()

        // Check for auto-verified code
        val autoVerifiedCode = intent.getStringExtra("AUTO_VERIFIED_CODE")
        if (autoVerifiedCode != null) {
            binding.otpInput.setText(autoVerifiedCode)
            verifyOtp(autoVerifiedCode)
        }

        binding.verifyBtn.setOnClickListener {
            val code = binding.otpInput.text.toString().trim()
            if (code.length < 6) {
                binding.otpInput.error = "Enter 6-digit code"
                return@setOnClickListener
            }
            verifyOtp(code)
        }

        binding.resendBtn.setOnClickListener {
            resendOtp()
        }

        binding.changePhoneBtn.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            intent.putExtra("PREFILLED_NAME", studentName)
            intent.putExtra("PREFILLED_PHONE", phoneNumber)
            startActivity(intent)
            finish()
        }
    }

    private fun setupUI() {
        val maskedPhone = maskPhoneNumber(phoneNumber ?: "")
        binding.otpDescriptionText.text = "Enter the 6-digit code sent to $maskedPhone"
    }

    private fun maskPhoneNumber(phone: String): String {
        if (phone.length < 4) return phone
        val last4 = phone.takeLast(4)
        return "+91 ••••••$last4"
    }

    private fun startResendTimer() {
        binding.resendBtn.visibility = View.GONE
        binding.resendTimerText.visibility = View.VISIBLE
        
        resendTimer?.cancel()
        resendTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.resendTimerText.text = "Resend code in ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                binding.resendTimerText.visibility = View.GONE
                binding.resendBtn.visibility = View.VISIBLE
            }
        }.start()
    }

    private fun verifyOtp(code: String) {
        hideKeyboard()
        showLoading("Verifying code...")
        
        val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
        linkAccount(credential)
    }

    private fun linkAccount(phoneCredential: AuthCredential) {
        val user = auth.currentUser
        if (user == null) {
            hideLoading()
            showError("Session expired. Please sign in again.")
            // Ideally redirect to WelcomeActivity
            return
        }

        user.linkWithCredential(phoneCredential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showLoading("✓ Phone verified! Creating your account...")
                    saveUserToDatabase()
                } else {
                    hideLoading()
                    val e = task.exception
                    if (e is FirebaseAuthUserCollisionException) {
                        showError("This phone number is already verified for another account.")
                    } else if (e is FirebaseAuthInvalidCredentialsException) {
                        showError("Invalid code. Please enter the most recent verification code sent to your phone.")
                    } else {
                        showError(e?.message ?: "Verification failed")
                    }
                }
            }
    }

    private fun saveUserToDatabase() {
        val firebaseUser = auth.currentUser
        val student = Student(
            uid = firebaseUser?.uid ?: "",
            name = studentName ?: "",
            email = firebaseUser?.email ?: "",
            phoneNumber = phoneNumber ?: ""
        )

        lifecycleScope.launch {
            try {
                database.child("students/${student.uid}").setValue(student).await()
                
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        database.child("students/${student.uid}/fcmToken").setValue(task.result)
                    }
                    
                    hideLoading()
                    val intent = Intent(this@OtpVerificationActivity, StudentDashboardActivity::class.java).apply {
                        putExtra("WELCOME_MESSAGE", "Welcome to PlateUp!")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                hideLoading()
                showError(e.message ?: "Failed to save user data")
            }
        }
    }

    private fun resendOtp() {
        showLoading("Resending code...")
        
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber!!)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // This might happen for auto-verification
                    hideLoading()
                    verifyOtp(credential.smsCode ?: "")
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    hideLoading()
                    showError(e.message ?: "Failed to resend code")
                }

                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    hideLoading()
                    verificationId = id
                    resendToken = token
                    startResendTimer()
                    showSuccess("New code sent!")
                }
            })
            .setForceResendingToken(resendToken!!)
            .build()
        
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
    }
}
