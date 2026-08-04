package com.app.plateup.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.R
import com.app.plateup.databinding.ActivityAdminBulkImportBinding
import com.app.plateup.models.Canteen
import com.app.plateup.models.MenuItem
import com.app.plateup.utils.ImportValidationResult
import com.app.plateup.utils.MenuImportUtils
import com.google.firebase.database.*
import java.io.BufferedReader
import java.io.InputStreamReader

class AdminBulkImportActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminBulkImportBinding
    private lateinit var database: DatabaseReference
    private var canteensList = ArrayList<Canteen>()
    private var selectedCanteen: Canteen? = null
    
    private var validatedItems = listOf<MenuItem>()
    private var currentMenuCount = 0

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBulkImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().reference
        
        setupUI()
        loadCanteens()
    }

    private fun setupUI() {
        binding.backImage.setOnClickListener { finish() }
        
        binding.selectFileBtn.setOnClickListener {
            val position = binding.canteenSpinner.selectedItemPosition
            if (position >= 0 && position < canteensList.size) {
                selectedCanteen = canteensList[position]
                filePickerLauncher.launch("application/json")
            } else {
                showError("Please select a canteen first")
            }
        }

        binding.tryAnotherFileBtn.setOnClickListener {
            filePickerLauncher.launch("application/json")
        }

        binding.confirmImportBtn.setOnClickListener {
            confirmAndReplace()
        }

        binding.cancelBtn.setOnClickListener {
            showSelectionState()
        }

        binding.finishBtn.setOnClickListener {
            finish()
        }
    }

    private fun loadCanteens() {
        showLoading("Loading canteens...")
        database.child("canteens").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                hideLoading()
                canteensList.clear()
                val names = mutableListOf<String>()
                for (child in snapshot.children) {
                    val canteen = child.getValue(Canteen::class.java)
                    if (canteen != null) {
                        canteensList.add(canteen)
                        names.add(canteen.name)
                    }
                }
                
                val adapter = ArrayAdapter(this@AdminBulkImportActivity, android.R.layout.simple_spinner_item, names)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.canteenSpinner.adapter = adapter
            }

            override fun onCancelled(error: DatabaseError) {
                hideLoading()
                showError("Failed to load canteens")
            }
        })
    }

    private fun processFile(uri: Uri) {
        val content = readUriContent(uri)
        if (content == null) {
            showError("Failed to read file")
            return
        }

        val canteenId = selectedCanteen?.id ?: return
        val result = MenuImportUtils.validateAndParse(content, canteenId)

        when (result) {
            is ImportValidationResult.Success -> {
                validatedItems = result.items
                fetchCurrentMenuCountAndShowSummary(result)
            }
            is ImportValidationResult.Error -> {
                showErrorState(result.errors)
            }
        }
    }

    private fun readUriContent(uri: Uri): String? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchCurrentMenuCountAndShowSummary(result: ImportValidationResult.Success) {
        val canteenId = selectedCanteen?.id ?: return
        showLoading("Checking current menu...")
        
        database.child("menus").child(canteenId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                hideLoading()
                currentMenuCount = snapshot.childrenCount.toInt()
                showSummaryState(result)
            }

            override fun onCancelled(error: DatabaseError) {
                hideLoading()
                showSummaryState(result) // Still show summary even if count fails
            }
        })
    }

    private fun showSelectionState() {
        binding.selectionContainer.visibility = View.VISIBLE
        binding.errorContainer.visibility = View.GONE
        binding.summaryContainer.visibility = View.GONE
        binding.successContainer.visibility = View.GONE
    }

    private fun showErrorState(errors: List<String>) {
        binding.selectionContainer.visibility = View.GONE
        binding.errorContainer.visibility = View.VISIBLE
        binding.summaryContainer.visibility = View.GONE
        binding.successContainer.visibility = View.GONE

        binding.errorRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.errorRecyclerView.adapter = ImportErrorsAdapter(errors)
    }

    private fun showSummaryState(result: ImportValidationResult.Success) {
        binding.selectionContainer.visibility = View.GONE
        binding.errorContainer.visibility = View.GONE
        binding.summaryContainer.visibility = View.VISIBLE
        binding.successContainer.visibility = View.GONE

        val canteen = selectedCanteen!!
        
        // Canteen mismatch warning
        if (result.jsonCanteenName != null && !result.jsonCanteenName.equals(canteen.name, ignoreCase = true)) {
            binding.warningCard.visibility = View.VISIBLE
            binding.warningText.text = "File is for '${result.jsonCanteenName}', but importing into '${canteen.name}'"
        } else {
            binding.warningCard.visibility = View.GONE
        }

        binding.currentCountText.text = "$currentMenuCount Items"
        binding.importedCountText.text = "${result.summary.totalItems} Items"
        binding.destructiveText.text = "This will DELETE all current menu items for ${canteen.name}."

        // Category breakdown
        binding.categoryContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        result.summary.categoryBreakdown.forEach { (name, count) ->
            val view = inflater.inflate(R.layout.item_import_category, binding.categoryContainer, false)
            view.findViewById<TextView>(R.id.categoryName).text = name
            view.findViewById<TextView>(R.id.categoryCount).text = "($count)"
            binding.categoryContainer.addView(view)
        }
    }

    private fun confirmAndReplace() {
        val canteen = selectedCanteen ?: return
        showConfirmationDialog(
            title = "Replace Menu?",
            message = "Are you sure you want to replace the entire menu for ${canteen.name}? This cannot be undone.",
            positiveButton = "Replace All",
            onConfirm = {
                executeImport()
            }
        )
    }

    private fun executeImport() {
        val canteenId = selectedCanteen?.id ?: return
        showLoading("Replacing menu...")

        // 1. Fetch current IDs to delete
        database.child("menus").child(canteenId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val updateMap = mutableMapOf<String, Any?>()
                
                // Delete existing
                for (child in snapshot.children) {
                    updateMap["/menus/$canteenId/${child.key}"] = null
                }
                
                // Add new
                for (item in validatedItems) {
                    val newId = database.child("menus").child(canteenId).push().key ?: continue
                    val finalItem = item.copy(id = newId)
                    updateMap["/menus/$canteenId/$newId"] = finalItem
                }
                
                // Atomic update
                database.updateChildren(updateMap).addOnCompleteListener { task ->
                    hideLoading()
                    if (task.isSuccessful) {
                        showSuccessState()
                    } else {
                        showError("Import failed: ${task.exception?.message}")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                hideLoading()
                showError("Failed to access database: ${error.message}")
            }
        })
    }

    private fun showSuccessState() {
        binding.selectionContainer.visibility = View.GONE
        binding.errorContainer.visibility = View.GONE
        binding.summaryContainer.visibility = View.GONE
        binding.successContainer.visibility = View.VISIBLE

        val canteenName = selectedCanteen?.name ?: "Canteen"
        val count = validatedItems.size
        val categories = validatedItems.map { it.category }.distinct().size
        
        binding.successDetailText.text = "Successfully imported $count items in $categories categories into\n$canteenName"
    }

    // Helper Adapter
    class ImportErrorsAdapter(private val errors: List<String>) : RecyclerView.Adapter<ImportErrorsAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val errorText: TextView = view.findViewById(R.id.errorText)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_import_error, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.errorText.text = errors[position]
        }
        override fun getItemCount() = errors.size
    }
}
