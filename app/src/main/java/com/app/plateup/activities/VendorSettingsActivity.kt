package com.app.plateup.activities

import android.app.TimePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import com.app.plateup.R
import com.app.plateup.databinding.ActivityVendorSettingsBinding
import com.app.plateup.models.Canteen
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.functions.FirebaseFunctions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.core.content.edit

class VendorSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityVendorSettingsBinding
    private lateinit var database: DatabaseReference
    private lateinit var functions: FirebaseFunctions
    private lateinit var preferences: SharedPreferences
    private lateinit var canteenId: String
    private var isFirstSetup = false
    private var openingTime = ""
    private var closingTime = ""
    private var currentMode = "AUTO"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVendorSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.saveButton.applySystemInsets(applyTop = false, applyBottom = true, useMargin = true)

        database = FirebaseDatabase.getInstance().reference
        functions = FirebaseFunctions.getInstance()
        preferences = getSharedPreferences("vendor_session", MODE_PRIVATE)

        canteenId = preferences.getString("canteen_id", "")!!

        isFirstSetup = intent.getBooleanExtra("FIRST_SETUP", false)

        setupUI()
        loadCanteen()
        setupListeners()
    }

    private fun setupUI() {
        if (isFirstSetup) {
            binding.titleText.text = "Complete Canteen Setup"
            binding.subtitleText.text = "Let's configure your canteen before you start accepting orders."
            binding.saveButton.text = "Save & Continue"
        } else {
            binding.titleText.text = "Canteen Settings"
            binding.subtitleText.text = "Update your canteen's operating hours and order settings."
            binding.saveButton.text = "Save Changes"
        }
    }

    private fun loadCanteen() {
        showLoading("Loading settings...")
        database.child("canteens")
            .child(canteenId)
            .get()
            .addOnSuccessListener {
                hideLoading()
                val canteen = it.getValue(Canteen::class.java) ?: return@addOnSuccessListener

                openingTime = canteen.openingTime
                closingTime = canteen.closingTime

                binding.openingTimeEdit.setText(formatTimeForDisplay(openingTime))
                binding.closingTimeEdit.setText(formatTimeForDisplay(closingTime))

                binding.packagingFeeEdit.setText(canteen.packagingFee.toString())
                binding.open24HoursSwitch.isChecked = canteen.open24Hours

                binding.paytmMidEdit.setText(canteen.providerAccountId)
                binding.paymentStatusText.text = "Status: ${canteen.paymentStatus}"

                currentMode = canteen.availabilityMode
                when (currentMode) {
                    "FORCE_OPEN" -> binding.availabilityToggleGroup.check(R.id.modeOpenBtn)
                    "FORCE_CLOSED" -> binding.availabilityToggleGroup.check(R.id.modeClosedBtn)
                    else -> binding.availabilityToggleGroup.check(R.id.modeAutoBtn)
                }
            }
            .addOnFailureListener {
                hideLoading()
                showError("Failed to load settings: ${it.message}", retryAction = { loadCanteen() })
            }
    }

    private fun setupListeners() {
        binding.backImage.setOnClickListener { finish() }

        binding.openingTimeEdit.setOnClickListener {
            showTimePicker(true)
        }

        binding.closingTimeEdit.setOnClickListener {
            showTimePicker(false)
        }

        binding.open24HoursSwitch.setOnCheckedChangeListener { _, checked ->
            binding.openingTimeLayout.isEnabled = !checked
            binding.closingTimeLayout.isEnabled = !checked

            binding.openingTimeLayout.alpha = if (checked) 0.5f else 1f
            binding.closingTimeLayout.alpha = if (checked) 0.5f else 1f
        }

        binding.availabilityToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentMode = when (checkedId) {
                    R.id.modeOpenBtn -> "FORCE_OPEN"
                    R.id.modeClosedBtn -> "FORCE_CLOSED"
                    else -> "AUTO"
                }
            }
        }

        binding.saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun showTimePicker(isOpening: Boolean) {
        val selectedTime = if (isOpening) openingTime else closingTime
        var hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        var minute = Calendar.getInstance().get(Calendar.MINUTE)

        if (selectedTime.isNotBlank()) {
            try {
                val parts = selectedTime.split(":")
                hour = parts[0].toInt()
                minute = parts[1].toInt()
            } catch (_: Exception) {}
        }

        TimePickerDialog(
            this,
            { _, hourOfDay, minuteOfHour ->
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                cal.set(Calendar.MINUTE, minuteOfHour)

                val storageFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val displayFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

                val storedTime = storageFormat.format(cal.time)
                val displayTime = displayFormat.format(cal.time)

                if (isOpening) {
                    openingTime = storedTime
                    binding.openingTimeEdit.setText(displayTime)
                } else {
                    closingTime = storedTime
                    binding.closingTimeEdit.setText(displayTime)
                }
            },
            hour,
            minute,
            false
        ).show()
    }

    private fun saveSettings() {
        val packagingFee = binding.packagingFeeEdit.text.toString().trim()
        val paytmMid = binding.paytmMidEdit.text.toString().trim()
        val paytmKey = binding.paytmKeyEdit.text.toString().trim()

        if (!binding.open24HoursSwitch.isChecked) {
            if (openingTime.isBlank()) {
                showError("Select opening time")
                return
            }
            if (closingTime.isBlank()) {
                showError("Select closing time")
                return
            }
        }

        if (packagingFee.isBlank()) {
            showError("Enter packaging fee")
            return
        }

        showLoading("Saving...")

        val updates = hashMapOf<String, Any>(
            "openingTime" to openingTime,
            "closingTime" to closingTime,
            "open24Hours" to binding.open24HoursSwitch.isChecked,
            "packagingFee" to packagingFee.toDouble(),
            "availabilityMode" to currentMode,
            "availabilityUpdatedAt" to System.currentTimeMillis(),
            "configurationComplete" to true
        )

        database.child("canteens")
            .child(canteenId)
            .updateChildren(updates)
            .addOnSuccessListener {
                if (paytmMid.isNotEmpty() && paytmKey.isNotEmpty()) {
                    updatePaymentSettings(paytmMid, paytmKey)
                } else {
                    hideLoading()
                    showSuccess("Settings saved successfully!")
                    finishAndNavigate()
                }
            }
            .addOnFailureListener {
                hideLoading()
                showError("Failed to save settings: ${it.message}")
            }
    }

    private fun updatePaymentSettings(mid: String, key: String) {
        val data = hashMapOf(
            "canteenId" to canteenId,
            "provider" to "PAYTM",
            "accountId" to mid,
            "secret" to key
        )

        functions.getHttpsCallable("updateCanteenPaymentSettings")
            .call(data)
            .addOnSuccessListener {
                hideLoading()
                showSuccess("Payment settings updated!")
                finishAndNavigate()
            }
            .addOnFailureListener {
                hideLoading()
                showError("Failed to update payment settings: ${it.message}")
            }
    }

    private fun finishAndNavigate() {
        if (isFirstSetup) {
            startActivity(Intent(this, VendorDashboardActivity::class.java))
            finish()
        } else {
            finish()
        }
    }

    private fun formatTimeForDisplay(time24: String): String {
        return try {
            val parser = SimpleDateFormat("HH:mm", Locale.getDefault())
            val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
            formatter.format(parser.parse(time24)!!)
        } catch (e: Exception) {
            time24
        }
    }
}
