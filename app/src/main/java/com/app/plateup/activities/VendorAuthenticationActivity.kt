package com.app.plateup.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.app.plateup.R
import com.app.plateup.repositories.SessionResolver
import com.app.plateup.databinding.ActivityVendorAuthenticationBinding
import com.app.plateup.models.Canteen
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.core.content.edit

class VendorAuthenticationActivity : BaseActivity() {

    private lateinit var binding: ActivityVendorAuthenticationBinding
    private lateinit var database: DatabaseReference
    private lateinit var auth: com.google.firebase.auth.FirebaseAuth
    private lateinit var functions: com.google.firebase.functions.FirebaseFunctions
    private lateinit var sessionResolver: SessionResolver
    private val canteenNames = mutableListOf<String>()
    private val canteenMap = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVendorAuthenticationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.vendorLoginBtn.applySystemInsets(applyTop = false, applyBottom = true, useMargin = true)

        database = FirebaseDatabase.getInstance().reference
        auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
        sessionResolver = SessionResolver(this)

        lifecycleScope.launch {
            try {
                sessionResolver.beginAnonymousPortalSession()
                loadCanteens()
            } catch (e: Exception) {
                showError("Auth initialization failed: ${e.message}")
            }
        }

        binding.backImage.setOnClickListener { finish() }

        binding.vendorLoginBtn.setOnClickListener {
            val selectedCanteen = binding.canteenDropdown.text.toString().trim()
            val enteredCode = binding.vendorCodeInput.text.toString().trim()

            if (selectedCanteen.isEmpty()) {
                showError("Please select a canteen!")
                return@setOnClickListener
            }
            if (enteredCode.isEmpty()) {
                showError("Please enter vendor access code!")
                return@setOnClickListener
            }
            val canteenId = canteenMap[selectedCanteen] ?: return@setOnClickListener

            if (auth.currentUser == null) {
                showError("Authentication not initialized. Please try again.")
                return@setOnClickListener
            }

            showLoading("Authenticating...")
            
            val data = hashMapOf(
                "canteenId" to canteenId,
                "vendorCode" to enteredCode
            )

            functions.getHttpsCallable("vendorLogin")
                .call(data)
                .addOnSuccessListener {
                    hideLoading()
                    sessionResolver.saveVendorSession(
                        uid = auth.currentUser!!.uid,
                        canteenId = canteenId,
                        canteenName = selectedCanteen
                    )
                    
                    val intent = Intent(this@VendorAuthenticationActivity, VendorDashboardActivity::class.java).apply {
                        putExtra("WELCOME_MESSAGE", "Welcome, $selectedCanteen!")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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


    private fun loadCanteens() {
        lifecycleScope.launch {
            try {
                val snapshot = database.child("canteens").get().await()
                for (canteenSnapshot in snapshot.children) {
                    val canteen = canteenSnapshot.getValue(Canteen::class.java)
                    if (canteen != null) {
                        canteenNames.add(canteen.name)
                        canteenMap[canteen.name] = canteen.id
                    }
                }

                val adapter = ArrayAdapter(
                    this@VendorAuthenticationActivity,
                    android.R.layout.simple_list_item_1,
                    canteenNames
                )

                binding.canteenDropdown.setAdapter(adapter)

            } catch (e: Exception) {
                showError(e.message ?: "Unknown error occurred")
            }
        }
    }
}
