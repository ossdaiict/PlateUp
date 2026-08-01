package com.app.plateup.activities

import android.os.Bundle
import com.app.plateup.databinding.ActivityAdminChangePasswordBinding
import com.google.firebase.functions.FirebaseFunctions

class AdminChangePasswordActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminChangePasswordBinding
    private lateinit var functions: FirebaseFunctions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAdminChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAutomaticSystemInsets(binding.root)

        functions = FirebaseFunctions.getInstance()

        binding.backImage.setOnClickListener { finish() }

        binding.changePasswordBtn.setOnClickListener {
            validateAndChangePassword()
        }
    }

    private fun validateAndChangePassword() {
        val currentPassword = binding.currentPasswordInput.text.toString().trim()
        val newPassword = binding.newPasswordInput.text.toString().trim()
        val confirmPassword = binding.confirmPasswordInput.text.toString().trim()

        if (currentPassword.isEmpty()) {
            showError("Current password is required")
            return
        }
        if (newPassword.isEmpty()) {
            showError("New password is required")
            return
        }
        if (newPassword.length < 8) {
            showError("New password must be at least 8 characters long")
            return
        }
        if (newPassword != confirmPassword) {
            showError("Passwords do not match")
            return
        }
        if (currentPassword == newPassword) {
            showError("New password cannot be the same as current password")
            return
        }

        showLoading("Updating password...")

        val data = hashMapOf(
            "currentPassword" to currentPassword,
            "newPassword" to newPassword
        )

        functions.getHttpsCallable("changeAdminPassword")
            .call(data)
            .addOnSuccessListener {
                hideLoading()
                showSuccess("Password updated successfully")
                finish()
            }
            .addOnFailureListener { e ->
                hideLoading()
                showError(e.message ?: "Failed to update password")
            }
    }
}