package com.app.plateup.activities

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.app.plateup.databinding.ActivityVendorRequestMenuItemBinding
import com.app.plateup.models.MenuItem
import com.app.plateup.models.MenuRequest
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class VendorRequestMenuItemActivity : BaseActivity() {

    private lateinit var binding: ActivityVendorRequestMenuItemBinding
    private lateinit var database: DatabaseReference
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVendorRequestMenuItemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.submitRequestBtn.applySystemInsets(applyTop = false, applyBottom = true, useMargin = true)

        database = FirebaseDatabase.getInstance().reference
        preferences = getSharedPreferences("vendor_session", MODE_PRIVATE)

        val canteenId = preferences.getString("canteen_id", "")!!
        val requestType = intent.getStringExtra("requestType")!!
        val canteenName = preferences.getString("canteen_name", "")!!

        if (requestType == "update") {
            val prefillItemName = intent.getStringExtra("itemName") ?: ""
            val prefillCategory = intent.getStringExtra("category") ?: ""
            val prefillPrice = intent.getIntExtra("price", 0)
            val prefillTakeawayAvailability = intent.getBooleanExtra("takeawayAvailability", true)

            binding.itemNameInput.setText(prefillItemName)
            binding.itemCategoryInput.setText(prefillCategory)
            binding.itemPriceInput.setText(prefillPrice.toString())
            binding.takeawaySwitch.isChecked = prefillTakeawayAvailability

            binding.itemNameLayout.isEnabled = false
            binding.itemCategoryLayout.isEnabled = false
            binding.takeawaySwitch.isEnabled = false

            binding.titleText.text = "Request Price Update"
            binding.subtitleText.text = "Submit a pricing change request"
        }

        binding.submitRequestBtn.setOnClickListener {
            val itemName = binding.itemNameInput.text.toString().trim()
            val category = binding.itemCategoryInput.text.toString().trim()
            val priceText = binding.itemPriceInput.text.toString().trim()
            val takeawayAvailable = binding.takeawaySwitch.isChecked

            if (itemName.isEmpty()) {
                showError("Enter item name")
                return@setOnClickListener
            }
            if (category.isEmpty()) {
                showError("Enter category")
                return@setOnClickListener
            }
            if (priceText.isEmpty()) {
                showError("Enter price")
                return@setOnClickListener
            }
            val price = priceText.toInt()
            if (price < 0) {
                showError("Enter valid price")
                return@setOnClickListener
            }

            val existingItemId = intent.getStringExtra("menuItemId") ?: ""
            val existingPrice = intent.getIntExtra("price", 0)

            if (requestType == "update" && price == existingPrice) {
                showError("Please update the price")
                return@setOnClickListener
            }

            val available = intent.getBooleanExtra("available", true)

            val menuItemId =
                if (requestType == "add") {
                    database.child("menus").push().key ?: return@setOnClickListener
                } else {
                    existingItemId
                }

            val requestId = database.child("menu_requests").push().key ?: return@setOnClickListener
            val menuItem = MenuItem(
                id = menuItemId,
                canteenId = canteenId,
                name = itemName,
                price = price,
                category = category,
                available = true,
                takeawayAvailable = takeawayAvailable
            )

            val oldMenuItem =
                if (requestType == "update") {
                    MenuItem(
                        id = menuItemId,
                        canteenId = canteenId,
                        name = itemName,
                        price = existingPrice,
                        category = category,
                        available = available,
                        takeawayAvailable = takeawayAvailable
                    )
                } else {
                    null
                }

            val request = MenuRequest(
                requestId = requestId,
                canteenId = canteenId,
                canteenName = canteenName,
                oldMenuItem = oldMenuItem,
                newMenuItem = menuItem,
                requestType = requestType
            )

            lifecycleScope.launch {
                hideKeyboard()
                showLoading("Submitting request...")
                binding.submitRequestBtn.isEnabled = false
                try {
                    database.child("menu_requests/$requestId").setValue(request).await()
                    hideLoading()
                    showSuccess("Request submitted successfully!")
                    finish()
                } catch (e: Exception) {
                    hideLoading()
                    showError(e.message ?: "Unknown error occurred")
                    binding.submitRequestBtn.isEnabled = true
                }
            }

        }

        binding.backImage.setOnClickListener { finish() }

    }

}