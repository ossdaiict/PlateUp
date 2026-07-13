package com.app.plateup.activities

import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.plateup.R
import com.app.plateup.adapters.AdminMenuAdapter
import com.app.plateup.databinding.ActivityAdminCanteenDetailsBinding
import com.app.plateup.models.Canteen
import com.app.plateup.models.MenuItem
import com.app.plateup.utils.CanteenUtils
import com.google.firebase.database.*

class AdminCanteenDetailsActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminCanteenDetailsBinding
    private lateinit var database: DatabaseReference
    private lateinit var canteenId: String
    private val displayList = ArrayList<Any>()
    private lateinit var menuAdapter: AdminMenuAdapter

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminCanteenDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        canteenId = intent.getStringExtra("canteenId") ?: ""
        database = FirebaseDatabase.getInstance().reference

        if (canteenId.isEmpty()) {
            finish()
            return
        }

        setupRecyclerView()
        loadCanteenDetails()
        loadMenu()

        binding.detailsContainer.applySystemInsets(applyTop = false, applyBottom = true)
        binding.backImage.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        menuAdapter = AdminMenuAdapter(this, displayList) { item ->
            val intent = Intent(this, MenuItemDetailActivity::class.java)
            intent.putExtra("MENU_ITEM_ID", item.id)
            intent.putExtra("CANTEEN_ID", canteenId)
            startActivity(intent)
        }
        binding.adminMenuRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.adminMenuRecyclerView.adapter = menuAdapter
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadCanteenDetails() {
        showLoading("Loading canteen...")
        database.child("canteens").child(canteenId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    hideLoading()
                    val canteen = snapshot.getValue(Canteen::class.java)
                    if (canteen != null) {
                        updateUI(canteen)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    hideLoading()
                    showError("Failed to load details: ${error.message}")
                }
            })
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateUI(canteen: Canteen) {
        binding.titleText.text = canteen.name
        
        // Operational Status
        val uiState = CanteenUtils.getUiState(this, canteen)
        binding.statusChip.text = uiState.chipText
        binding.statusChip.setTextColor(uiState.statusColor)
        binding.statusChip.setBackgroundResource(
            if (uiState.isOpen) R.drawable.bg_open_chip else R.drawable.bg_close_chip
        )
        binding.statusDetailText.text = uiState.statusText

        // Rows
        setupRow(binding.rowHours.root, "Operating Hours", 
            if (canteen.open24Hours) "Open 24 Hours" 
            else "${CanteenUtils.formatTimeForDisplay(canteen.openingTime)} - ${CanteenUtils.formatTimeForDisplay(canteen.closingTime)}")
        
        setupRow(binding.rowAvailability.root, "Availability Mode", 
            if (canteen.availabilityMode == "MANUAL") "Manual Override" else "Automatic (Schedule)")
        
        setupRow(binding.rowPayment.root, "Payment Status", 
            if (canteen.paymentStatus == "CONFIGURED") "Configured (${canteen.paymentProvider})" else "Not Configured")
        
        setupRow(binding.rowPackaging.root, "Packaging Fee", 
            if (canteen.packagingFee > 0) "₹${canteen.packagingFee.toInt()}" else "Free")
    }

    private fun setupRow(view: View, label: String, value: String) {
        view.findViewById<TextView>(R.id.rowLabel).text = label
        view.findViewById<TextView>(R.id.rowValue).text = value
    }

    private fun loadMenu() {
        database.child("menus").child(canteenId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val rawItems = ArrayList<MenuItem>()
                    for (itemSnapshot in snapshot.children) {
                        val item = itemSnapshot.getValue(MenuItem::class.java)
                        if (item != null) {
                            rawItems.add(item)
                        }
                    }
                    rawItems.sortWith(compareBy<MenuItem> { it.category }.thenBy { it.name })
                    
                    displayList.clear()
                    if (rawItems.isNotEmpty()) {
                        var currentCategory = ""
                        for (item in rawItems) {
                            if (item.category != currentCategory) {
                                currentCategory = item.category
                                displayList.add(currentCategory)
                            }
                            displayList.add(item)
                        }
                    }
                    menuAdapter.notifyDataSetChanged()
                    
                    if (displayList.isEmpty()) {
                        binding.emptyMenuText.visibility = View.VISIBLE
                        binding.adminMenuRecyclerView.visibility = View.GONE
                    } else {
                        binding.emptyMenuText.visibility = View.GONE
                        binding.adminMenuRecyclerView.visibility = View.VISIBLE
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Ignore
                }
            })
    }
}
