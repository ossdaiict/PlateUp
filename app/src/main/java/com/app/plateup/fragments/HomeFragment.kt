package com.app.plateup.fragments

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.plateup.activities.MenuItemDetailActivity
import com.app.plateup.activities.StudentMenuActivity
import com.app.plateup.activities.StudentNotificationsActivity
import com.app.plateup.adapters.SearchResultsAdapter
import com.app.plateup.adapters.StudentCanteensAdapter
import com.app.plateup.databinding.FragmentHomeBinding
import com.app.plateup.models.Canteen
import com.app.plateup.models.MenuItem
import com.app.plateup.models.Notification
import com.app.plateup.models.SearchResultMenuItem
import com.app.plateup.models.Student
import com.app.plateup.utils.CanteenUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class HomeFragment : BaseFragment() {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var canteensList: ArrayList<Canteen>
    private lateinit var adapter: StudentCanteensAdapter
    private lateinit var allSearchItems: ArrayList<SearchResultMenuItem>
    private lateinit var searchResultsList: ArrayList<SearchResultMenuItem>
    private lateinit var searchAdapter: SearchResultsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentHomeBinding.inflate(inflater, container, false)

        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        database = FirebaseDatabase.getInstance().reference
        auth = FirebaseAuth.getInstance()
        canteensList = ArrayList()
        searchResultsList = ArrayList()
        allSearchItems = ArrayList()

        adapter = StudentCanteensAdapter(
            requireContext(),
            canteensList,
            onCanteenClick = { canteen ->
                openMenu(canteen)
            }
        )

        searchAdapter = SearchResultsAdapter(
            requireContext(),
            searchResultsList
        )

        loadGreeting()
        listenToNotificationBadge()

        binding.canteensRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.canteensRecyclerView.adapter = adapter
        binding.canteensRecyclerView.applySystemInsets(applyTop = false, applyBottom = true, useMargin = false)

        binding.searchResultsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.searchResultsRecyclerView.adapter = searchAdapter
        binding.searchResultsRecyclerView.applySystemInsets(applyTop = false, applyBottom = true, useMargin = false)

        loadCanteens()

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {

            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(s.toString())
            }

        })

        binding.notificationsBtn.setOnClickListener {
            startActivity(Intent(requireContext(), StudentNotificationsActivity::class.java))
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadGreeting() {
        lifecycleScope.launch {
            try {
                val uid = auth.uid ?: return@launch

                val snapshot = database.child("students/$uid").get().await()
                val student = snapshot.getValue(Student::class.java)
                val name = student?.name
                binding.greetingText.text = "Hello, $name!"

            } catch (e: Exception) {
                if (isAdded) showError(e.message ?: "Unknown error occurred")
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadCanteens() {
        // Show shimmer
        binding.shimmerViewContainer.startShimmer()
        binding.shimmerViewContainer.visibility = View.VISIBLE
        binding.canteensRecyclerView.visibility = View.GONE

        val canteensRef = database.child("canteens")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                
                canteensList.clear()
                for (child in snapshot.children) {
                    val canteen = child.getValue(Canteen::class.java)
                    if (canteen != null) {
                        val menuRef = database.child("menus").child(canteen.id)
                        val menuListener = object : ValueEventListener {
                            override fun onDataChange(menuSnapshot: DataSnapshot) {
                                if (!isAdded) return
                                
                                var count = 0
                                for (itemChild in menuSnapshot.children) {
                                    val menuItem = itemChild.getValue(MenuItem::class.java)?.copy(
                                        id = itemChild.key ?: "",
                                        canteenId = canteen.id
                                    )
                                    if (menuItem?.available == true) {
                                        count++
                                    }
                                }
                                canteen.itemCount = count
                                canteensList.removeAll { it.id == canteen.id }
                                canteensList.add(canteen)

                                for (itemChild in menuSnapshot.children) {
                                    val menuItem = itemChild.getValue(MenuItem::class.java)?.copy(
                                        id = itemChild.key ?: "",
                                        canteenId = canteen.id
                                    )
                                    if (menuItem != null) {
                                        allSearchItems.removeAll { it.menuItem.id == menuItem.id }
                                        val result = SearchResultMenuItem(
                                            menuItem = menuItem,
                                            canteenName =  canteen.name,
                                            canteenOpen = CanteenUtils.isCurrentlyOpen(canteen)
                                        )
                                        allSearchItems.add(result)
                                    }
                                }
                                
                                // Hide shimmer when first batch arrives
                                binding.shimmerViewContainer.stopShimmer()
                                binding.shimmerViewContainer.visibility = View.GONE
                                if (binding.searchInput.text.isNullOrEmpty()) {
                                    binding.canteensRecyclerView.visibility = View.VISIBLE
                                    binding.searchResultsRecyclerView.visibility = View.GONE
                                } else {
                                    binding.canteensRecyclerView.visibility = View.GONE
                                    binding.searchResultsRecyclerView.visibility = View.VISIBLE
                                }
                                
                                adapter.notifyDataSetChanged()
                            }

                            override fun onCancelled(error: DatabaseError) {
                                if (isAdded) showError(error.message)
                            }

                        }
                        registerListener(menuRef, menuListener)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (isAdded) showError(error.message)
            }

        }
        registerListener(canteensRef, listener)
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
    private fun openMenu(canteen: Canteen) {
        val intent = Intent(requireContext(), StudentMenuActivity::class.java)
        val isOpen = CanteenUtils.isCurrentlyOpen(canteen)
        intent.putExtra("canteenId", canteen.id)
        intent.putExtra("canteenName", canteen.name)
        intent.putExtra("canteenPackagingFee", canteen.packagingFee.toInt())
        intent.putExtra("canteenOpen", isOpen)
        if (!isOpen) {
            intent.putExtra("canteenOpeningMessage", CanteenUtils.getOpeningMessage(canteen))
        }
        startActivity(intent)
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            binding.canteensRecyclerView.visibility = View.VISIBLE
            binding.searchResultsRecyclerView.visibility = View.GONE
            return
        }

        binding.canteensRecyclerView.visibility = View.GONE
        binding.searchResultsRecyclerView.visibility = View.VISIBLE

        searchResultsList.clear()

        for (result in allSearchItems) {
            val menuItem = result.menuItem
            if (menuItem.name.lowercase().contains(query.lowercase()) ||
                menuItem.category.lowercase().contains(query.lowercase()) ||
                result.canteenName.lowercase().contains(query.lowercase())) {
                searchResultsList.add(result)
            }
        }
        searchAdapter.notifyDataSetChanged()

    }

    private fun listenToNotificationBadge() {
        val uid = auth.currentUser?.uid ?: return
        val ref = database.child("notifications").child(uid).orderByChild("read").equalTo(false)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val unreadCount = snapshot.childrenCount.toInt()
                updateNotificationBadge(unreadCount)
            }
            override fun onCancelled(error: DatabaseError) {
                if (isAdded) showError(error.message)
            }
        }
        registerListener(ref, listener)
    }

    private fun updateNotificationBadge(unreadCount: Int) {
        if (unreadCount > 0) {
            binding.notificationBadge.visibility = View.VISIBLE
            binding.notificationBadge.text = when {
                unreadCount == 1 -> ""
                unreadCount > 99 -> "99+"
                else -> unreadCount.toString()
            }
        } else {
            binding.notificationBadge.visibility = View.GONE
        }
    }


}
