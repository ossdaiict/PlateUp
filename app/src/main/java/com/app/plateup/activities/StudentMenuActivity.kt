package com.app.plateup.activities

import android.os.Bundle
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.plateup.R
import com.app.plateup.adapters.StudentMenuAdapter
import com.app.plateup.databinding.ActivityStudentMenuBinding
import com.app.plateup.models.Canteen
import com.app.plateup.models.CartItem
import com.app.plateup.models.MenuItem
import com.app.plateup.utils.CanteenUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class StudentMenuActivity : BaseActivity() {

    private lateinit var binding: ActivityStudentMenuBinding
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var menuList: ArrayList<MenuItem>
    private lateinit var adapter: StudentMenuAdapter
    private lateinit var cartQuantities: HashMap<String, Int>
    private lateinit var filteredList: ArrayList<Any>
    private var canteenId = ""

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStudentMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().reference
        auth = FirebaseAuth.getInstance()

        canteenId = intent.getStringExtra("canteenId")!!

        val canteenName = intent.getStringExtra("canteenName")!!
        val canteenPackagingFee = intent.getIntExtra("canteenPackagingFee", 0)
        val canteenOpen = intent.getBooleanExtra("canteenOpen", true)
        val canteenOpeningMessage = intent.getStringExtra("canteenOpeningMessage")

        binding.titleText.text = "${canteenName}'s Menu"

        if (canteenOpen) {
            binding.closedBanner.visibility = View.GONE
        } else {
            binding.closedBanner.visibility = View.VISIBLE
            if (!canteenOpeningMessage.isNullOrBlank()) {
                binding.closedBannerText.text = canteenOpeningMessage
            }
        }

        menuList = ArrayList()
        filteredList = ArrayList()
        cartQuantities = HashMap()
        adapter = StudentMenuAdapter(
            this,
            filteredList,
            cartQuantities,
            canteenOpen,
            onAddClick = { menuItem ->
                addToCart(menuItem, canteenId, canteenName, canteenPackagingFee)
            },
            onIncreaseClick = { menuItem ->
                increaseQuantity(menuItem, canteenId)
            },
            onDecreaseClick = { menuItem ->
                decreaseQuantity(menuItem, canteenId)
            }
        )
        binding.menuRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.menuRecyclerView.adapter = adapter

        binding.menuRecyclerView.applySystemInsets(applyTop = false, applyBottom = true)

        loadMenu(canteenId)
        listenToCart(canteenId)
        listenToCanteenAvailability()

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterMenuItems(s.toString())
            }
        })

        binding.backImage.setOnClickListener { finish() }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun listenToCanteenAvailability() {
        val canteenRef = database.child("canteens").child(canteenId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val canteen = snapshot.getValue(Canteen::class.java) ?: return
                val uiState = CanteenUtils.getUiState(this@StudentMenuActivity, canteen)
                
                if (uiState.isOpen) {
                    binding.closedBanner.visibility = View.GONE
                } else {
                    binding.closedBanner.visibility = View.VISIBLE
                    binding.closedBannerText.text = uiState.statusText
                }
                
                adapter.updateCanteenStatus(uiState.isOpen)
            }

            override fun onCancelled(error: DatabaseError) {
                showError(error.message)
            }
        }
        registerListener(canteenRef, listener)
    }

    private fun loadMenu(canteenId: String) {
        val menuRef = database.child("menus/$canteenId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                menuList.clear()
                for (child in snapshot.children) {
                    val menuItem = child.getValue(MenuItem::class.java)?.copy(
                        id = child.key ?: "",
                        canteenId = canteenId
                    )
                    if (menuItem != null) {
                        menuList.add(menuItem)
                    }
                }
                menuList.sortWith(compareBy<MenuItem> { it.category }.thenBy { it.name })
                populateFilteredList(menuList)
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                showError(error.message)
            }
        }
        registerListener(menuRef, listener)
    }

    private fun listenToCart(canteenId: String) {
        val uid = auth.currentUser?.uid ?: return
        val cartRef = database.child("carts").child(uid).child(canteenId)
        val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    cartQuantities.clear()
                    for (child in snapshot.children) {
                        val cartItem = child.getValue(CartItem::class.java)?.apply {
                            if (menuItemId.isBlank()) menuItemId = child.key ?: ""
                        }
                        if (cartItem != null) {
                            cartQuantities[cartItem.menuItemId] = cartItem.quantity
                        }
                    }
                    adapter.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                    showError(error.message)
                }
        }
        registerListener(cartRef, listener)
    }

    private fun populateFilteredList(items: List<MenuItem>) {
        filteredList.clear()
        if (items.isNotEmpty()) {
            var currentCategory = ""
            for (item in items) {
                if (item.category != currentCategory) {
                    currentCategory = item.category
                    filteredList.add(currentCategory)
                }
                filteredList.add(item)
            }
        }
    }

    private fun filterMenuItems(query: String) {
        if (query.isEmpty()) {
            populateFilteredList(menuList)
        } else {
            val result = menuList.filter { 
                it.name.lowercase().contains(query.lowercase()) || it.category.lowercase().contains(query.lowercase())
            }
            populateFilteredList(result)
        }
        adapter.notifyDataSetChanged()
    }

    private fun addToCart(menuItem: MenuItem, canteenId: String, canteenName: String, canteenPackagingFee: Int) {
        val uid = auth.currentUser?.uid ?: return
        val cartRef = database
            .child("carts")
            .child(uid)
            .child(canteenId)
            .child(menuItem.id)

        val cartItem = CartItem(
            menuItemId = menuItem.id,
            canteenId = canteenId,
            canteenName = canteenName,
            canteenPackagingFee = canteenPackagingFee,
            uid = uid,
            name = menuItem.name,
            category = menuItem.category,
            price = menuItem.price,
            quantity = 1,
            takeawayAvailable = menuItem.takeawayAvailable
        )
        cartRef.setValue(cartItem)
        showSuccess("${menuItem.name} added to cart")
    }

    private fun increaseQuantity(menuItem: MenuItem, canteenId: String) {
        val uid = auth.currentUser?.uid ?: return
        val cartRef = database
            .child("carts")
            .child(uid)
            .child(canteenId)
            .child(menuItem.id)
        cartRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val existingItem = snapshot.getValue(CartItem::class.java)
                val updatedQuantity = (existingItem?.quantity ?: 0) + 1
                cartRef.child("quantity").setValue(updatedQuantity)
            }

            override fun onCancelled(error: DatabaseError) {
                showError(error.message)
            }

        })
    }

    private fun decreaseQuantity(menuItem: MenuItem, canteenId: String) {
        val uid = auth.currentUser?.uid ?: return
        val cartRef = database
            .child("carts")
            .child(uid)
            .child(canteenId)
            .child(menuItem.id)

        cartRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val cartItem = snapshot.getValue(CartItem::class.java) ?: return
                val updatedQuantity = cartItem.quantity - 1
                if (updatedQuantity <= 0) {
                    cartRef.removeValue()
                } else {
                    cartRef.child("quantity").setValue(updatedQuantity)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showError(error.message)
            }

        })
    }
}
