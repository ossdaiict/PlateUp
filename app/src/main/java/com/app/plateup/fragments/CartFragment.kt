package com.app.plateup.fragments

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.plateup.R
import com.app.plateup.activities.CartDetailsActivity
import com.app.plateup.adapters.CartGroupsAdapter
import com.app.plateup.databinding.FragmentCartBinding
import com.app.plateup.models.Canteen
import com.app.plateup.models.CartGroup
import com.app.plateup.models.CartItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

class CartFragment : BaseFragment() {

    private lateinit var binding: FragmentCartBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var cartGroups: ArrayList<CartGroup>
    private lateinit var adapter: CartGroupsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        cartGroups = ArrayList()

        adapter = CartGroupsAdapter(
            requireContext(),
            cartGroups,
            onViewCartClick = { cartGroup ->
                val intent = Intent(requireContext(), CartDetailsActivity::class.java)
                intent.putExtra("canteenId", cartGroup.canteenId)
                intent.putExtra("canteenName", cartGroup.canteenName)
                intent.putExtra("canteenPackagingFee", cartGroup.canteenPackagingFee)
                startActivity(intent)
            }
        )

        binding.cartRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.cartRecyclerView.adapter = adapter
        binding.cartRecyclerView.applySystemInsets(applyTop = false, applyBottom = true, useMargin = false)

        loadCartGroups()

    }

    private fun loadCartGroups() {
        val uid = auth.currentUser?.uid ?: return
        val cartRef = database.child("carts").child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                cartGroups.clear()
                adapter.notifyDataSetChanged()
                for (canteenSnapshot in snapshot.children) {
                    val canteenId = canteenSnapshot.key ?: continue
                    val items = ArrayList<CartItem>()
                    var totalAmount = 0
                    for (itemSnapshot in canteenSnapshot.children) {
                        val cartItem = itemSnapshot.getValue(CartItem::class.java)
                        if (cartItem != null) {
                            items.add(cartItem)
                            totalAmount += cartItem.price * cartItem.quantity
                        }
                    }

                    if (items.isEmpty()) continue

                    val canteenName = items[0].canteenName
                    val canteenPackagingFee = items[0].canteenPackagingFee
                    val cartGroup = CartGroup(
                        canteenId = canteenId,
                        canteenName = canteenName,
                        items = items,
                        totalAmount = totalAmount,
                        canteenPackagingFee = canteenPackagingFee
                    )

                    cartGroups.add(cartGroup)
                    adapter.notifyDataSetChanged()

                }
                binding.emptyStateText.visibility =
                    if (cartGroups.isEmpty()) View.VISIBLE
                    else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                showError(error.message)
            }
        }
        registerListener(cartRef, listener)
    }


}