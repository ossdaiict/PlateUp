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

        binding.continueBtn.applySystemInsets(applyTop = false, applyBottom = true, useMargin = true)

        binding.continueBtn.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()
            val phoneNumber = binding.phoneInput.text.toString().trim()

            if (name.isEmpty()) {
                binding.nameInput.error = "Name is required"
                return@setOnClickListener
            }
            if (phoneNumber.isEmpty() || phoneNumber.length < 10) {
                binding.phoneInput.error = "Enter a valid 10-digit phone number"
                return@setOnClickListener
            }

            hideKeyboard()
            binding.continueBtn.isEnabled = false
            showLoading("Creating your account...")

            val firebaseUser = auth.currentUser
            val student = Student(
                uid = firebaseUser?.uid ?: "",
                name = name,
                email = firebaseUser?.email ?: "",
                phoneNumber = phoneNumber
            )

            lifecycleScope.launch {
                try {
                    database.child("students/${student.uid}").setValue(student).await()
                    
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            database.child("students/${student.uid}/fcmToken").setValue(task.result)
                        }
                        
                        hideLoading()
                        val intent = Intent(this@RegisterActivity, StudentDashboardActivity::class.java).apply {
                            putExtra("WELCOME_MESSAGE", "Welcome to PlateUp!")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                } catch (e: Exception) {
                    hideLoading()
                    binding.continueBtn.isEnabled = true
                    showError(e.message ?: "Registration failed")
                }
            }

        }

    }

}