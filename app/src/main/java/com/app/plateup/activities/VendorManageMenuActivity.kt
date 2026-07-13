package com.app.plateup.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.plateup.R
import com.app.plateup.adapters.VendorMenuAdapter
import com.app.plateup.databinding.ActivityVendorManageMenuBinding
import com.app.plateup.models.MenuItem
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class VendorManageMenuActivity : BaseActivity() {

    private lateinit var binding: ActivityVendorManageMenuBinding
    private lateinit var database: DatabaseReference
    private lateinit var preferences: SharedPreferences
    private lateinit var menuList: ArrayList<MenuItem>
    private lateinit var adapter: VendorMenuAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityVendorManageMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().reference
        preferences = getSharedPreferences("vendor_session", MODE_PRIVATE)
        menuList = ArrayList()

        val canteenId = preferences.getString("canteen_id", "")!!

        loadMenu(canteenId)

        adapter = VendorMenuAdapter(
            this,
            menuList,
            onAvailabilityClick = { menuItem ->
                toggleAvailability(menuItem)
            },
            onEditClick = { menuItem ->
                openEditRequest(menuItem)
            }
        )

        binding.menuRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.menuRecyclerView.adapter = adapter

        binding.menuRecyclerView.applySystemInsets(applyTop = false, applyBottom = true)
        binding.addMenuItemFab.applySystemInsets(applyTop = false, applyBottom = true, useMargin = true)

        setupSearch()

        binding.backImage.setOnClickListener { finish() }

        binding.addMenuItemFab.setOnClickListener {
            val intent = Intent(this, VendorRequestMenuItemActivity::class.java)
            intent.putExtra("requestType", "add")
            startActivity(intent)
        }

    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadMenu(canteenId: String) {
        val menuRef = database.child("menus").child(canteenId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newList = ArrayList<MenuItem>()
                for (child in snapshot.children) {
                    val menuItem = child.getValue(MenuItem::class.java)
                    if (menuItem != null) {
                        newList.add(menuItem)
                    }
                }
                newList.sortWith(compareBy<MenuItem> { it.category }.thenBy { it.name })
                adapter.updateData(newList)
            }

            override fun onCancelled(error: DatabaseError) {
                showError(error.message ?: "Unknown error occurred")
            }
        }
        registerListener(menuRef, listener)
    }

    private fun toggleAvailability(menuItem: MenuItem) {
        val updatedAvailability = !menuItem.available
        database.child("menus")
            .child(menuItem.canteenId)
            .child(menuItem.id)
            .child("available")
            .setValue(updatedAvailability)
    }

    private fun openEditRequest(menuItem: MenuItem) {
        val intent = Intent(this, VendorRequestMenuItemActivity::class.java)
        intent.putExtra("requestType", "update")
        intent.putExtra("menuItemId", menuItem.id)
        intent.putExtra("itemName", menuItem.name)
        intent.putExtra("category", menuItem.category)
        intent.putExtra("price", menuItem.price)
        intent.putExtra("availability", menuItem.available)
        intent.putExtra("takeawayAvailability", menuItem.takeawayAvailable)
        startActivity(intent)
    }

}