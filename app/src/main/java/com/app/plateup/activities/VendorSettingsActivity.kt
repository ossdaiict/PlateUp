package com.app.plateup.activities

import android.app.TimePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.plateup.R
import com.app.plateup.adapters.CanteenContactAdapter
import com.app.plateup.databinding.ActivityVendorSettingsBinding
import com.app.plateup.databinding.DialogAddContactBinding
import com.app.plateup.models.Canteen
import com.app.plateup.models.CanteenContact
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

    private val contactsList = mutableListOf<CanteenContact>()
    private lateinit var contactAdapter: CanteenContactAdapter
    private var isDirty = false

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
        setupContactsRecyclerView()
        loadCanteen()
        setupListeners()
        setupBackPressed()
    }

    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isDirty) {
                    showDiscardChangesDialog()
                } else {
                    finish()
                }
            }
        })
    }

    private fun showDiscardChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle("Discard Changes?")
            .setMessage("You have unsaved changes. Are you sure you want to discard them?")
            .setPositiveButton("Discard") { _, _ -> finish() }
            .setNegativeButton("Keep Editing", null)
            .show()
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

    private fun setupContactsRecyclerView() {
        contactAdapter = CanteenContactAdapter(
            contactsList,
            onEdit = { position -> showContactDialog(contactsList[position], position) },
            onDelete = { position -> 
                contactsList.removeAt(position)
                if (contactsList.none { it.isPrimary } && contactsList.isNotEmpty()) {
                    contactsList[0].isPrimary = true
                }
                updateContactsListUI()
                markDirty()
            },
            onPrimaryChanged = { position ->
                contactsList.forEach { it.isPrimary = false }
                contactsList[position].isPrimary = true
                contactAdapter.notifyDataSetChanged()
                markDirty()
            }
        )
        binding.contactsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.contactsRecyclerView.adapter = contactAdapter
    }

    private fun updateContactsListUI() {
        contactAdapter.notifyDataSetChanged()
        if (contactsList.isEmpty()) {
            binding.contactsRecyclerView.visibility = View.GONE
            binding.emptyContactsLayout.visibility = View.VISIBLE
        } else {
            binding.contactsRecyclerView.visibility = View.VISIBLE
            binding.emptyContactsLayout.visibility = View.GONE
        }
        checkValidation()
    }

    private fun showContactDialog(contact: CanteenContact? = null, position: Int? = null) {
        val dialogBinding = DialogAddContactBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        val roles = arrayOf("Vendor", "Manager", "Staff", "Other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, roles)
        dialogBinding.roleDropdown.setAdapter(adapter)

        if (contact != null) {
            dialogBinding.dialogTitle.text = "Edit Contact"
            dialogBinding.nameEdit.setText(contact.name)
            dialogBinding.roleDropdown.setText(contact.role, false)
            dialogBinding.phoneEdit.setText(contact.phoneNumber.replace("+91", ""))
            dialogBinding.saveBtn.text = "Update"
        }

        dialogBinding.cancelBtn.setOnClickListener { dialog.dismiss() }
        dialogBinding.saveBtn.setOnClickListener {
            val name = dialogBinding.nameEdit.text.toString().trim()
            val role = dialogBinding.roleDropdown.text.toString().trim()
            val phone = dialogBinding.phoneEdit.text.toString().trim()

            if (name.isEmpty()) {
                dialogBinding.nameLayout.error = "Name is required"
                return@setOnClickListener
            }
            if (role.isEmpty()) {
                dialogBinding.roleLayout.error = "Role is required"
                return@setOnClickListener
            }
            if (phone.length != 10) {
                dialogBinding.phoneLayout.error = "Valid 10-digit number required"
                return@setOnClickListener
            }

            val normalizedPhone = "+91$phone"
            val newContact = CanteenContact(
                name = name,
                role = role,
                phoneNumber = normalizedPhone,
                isPrimary = contact?.isPrimary ?: (contactsList.isEmpty())
            )

            if (position != null) {
                contactsList[position] = newContact
            } else {
                contactsList.add(newContact)
            }

            updateContactsListUI()
            markDirty()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun markDirty() {
        isDirty = true
        checkValidation()
    }

    private fun checkValidation() {
        val packagingFee = binding.packagingFeeEdit.text.toString().trim()
        
        val isHoursValid = binding.open24HoursSwitch.isChecked || (openingTime.isNotBlank() && closingTime.isNotBlank())
        val isFeeValid = packagingFee.isNotBlank()
        val isContactsValid = contactsList.isNotEmpty()

        binding.saveButton.isEnabled = isHoursValid && isFeeValid && isContactsValid
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

                contactsList.clear()
                contactsList.addAll(canteen.contacts)
                updateContactsListUI()
                
                isDirty = false
                checkValidation()
            }
            .addOnFailureListener {
                hideLoading()
                showError("Failed to load settings: ${it.message}", retryAction = { loadCanteen() })
            }
    }

    private fun setupListeners() {
        binding.backImage.setOnClickListener { 
            if (isDirty) showDiscardChangesDialog() else finish() 
        }

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
            markDirty()
        }

        binding.availabilityToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentMode = when (checkedId) {
                    R.id.modeOpenBtn -> "FORCE_OPEN"
                    R.id.modeClosedBtn -> "FORCE_CLOSED"
                    else -> "AUTO"
                }
                markDirty()
            }
        }

        binding.addContactBtn.setOnClickListener {
            showContactDialog()
        }

        binding.packagingFeeEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                markDirty()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.paytmMidEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                markDirty()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

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
                markDirty()
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

        if (contactsList.isEmpty()) {
            showError("At least one contact is required")
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
            "configurationComplete" to true,
            "contacts" to contactsList
        )

        database.child("canteens")
            .child(canteenId)
            .updateChildren(updates)
            .addOnSuccessListener {
                isDirty = false
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
