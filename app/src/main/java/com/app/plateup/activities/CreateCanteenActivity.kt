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
import com.app.plateup.databinding.ActivityCreateCanteenBinding
import com.app.plateup.models.Canteen
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CreateCanteenActivity : BaseActivity() {

    private lateinit var binding: ActivityCreateCanteenBinding
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCreateCanteenBinding.inflate(layoutInflater)
        setContentView(binding.root)



        database = FirebaseDatabase.getInstance().reference

        binding.backImage.setOnClickListener { finish() }

        binding.createCanteenBtn.setOnClickListener {
            val name = binding.canteenNameInput.text.toString().trim()
            val vendorCode = binding.vendorCodeInput.text.toString().trim()

            if (name.isEmpty()) {
                showError("Please provide the canteen name!")
                return@setOnClickListener
            }
            if (vendorCode.isEmpty()) {
                showError("Please provide the vendor access code!")
                return@setOnClickListener
            }
            
            hideKeyboard()
            showLoading("Creating canteen...")
            
            val canteenId = database.child("canteens").push().key ?: return@setOnClickListener
            val canteen = Canteen(
                id = canteenId,
                name =  name,
                vendorCode = vendorCode
            )

            binding.createCanteenBtn.isEnabled = false
            lifecycleScope.launch {
                try {
                    database.child("canteens/$canteenId").setValue(canteen).await()
                    hideLoading()
                    
                    val intent = Intent(this@CreateCanteenActivity, AdminDashboardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("SUCCESS_MESSAGE", "Canteen created successfully.")
                    }
                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    hideLoading()
                    showError(e.message ?: "Unknown error occurred")
                    binding.createCanteenBtn.isEnabled = true
                }
            }

        }

    }

}