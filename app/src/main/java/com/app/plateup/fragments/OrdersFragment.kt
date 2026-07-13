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
import com.app.plateup.activities.StudentOrderDetailsActivity
import com.app.plateup.adapters.StudentOrdersAdapter
import com.app.plateup.adapters.StudentOrdersAdapter.StudentOrderRow
import com.app.plateup.databinding.FragmentOrdersBinding
import com.app.plateup.models.Order
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class OrdersFragment : BaseFragment() {

    private lateinit var binding: FragmentOrdersBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var orderRows: ArrayList<StudentOrderRow>
    private lateinit var adapter: StudentOrdersAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        orderRows = ArrayList()
        adapter = StudentOrdersAdapter(
            requireContext(),
            orderRows,
            onOrderClick = { order ->
                val intent = Intent(requireContext(), StudentOrderDetailsActivity::class.java)
                intent.putExtra("orderId", order.orderId)
                startActivity(intent)
            }
        )

        binding.ordersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.ordersRecyclerView.adapter = adapter
        binding.ordersRecyclerView.applySystemInsets(applyTop = false, applyBottom = true, useMargin = false)

        loadOrders()

    }

    private fun loadOrders() {
        val uid = auth.currentUser?.uid ?: return
        
        // Show shimmer
        binding.shimmerViewContainer.startShimmer()
        binding.shimmerViewContainer.visibility = View.VISIBLE
        binding.ordersRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE

        // Server-side filtering
        val ordersRef = database.child("orders").orderByChild("userId").equalTo(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                
                val ongoingOrders = ArrayList<Order>()
                val pastOrders = ArrayList<Order>()

                for (child in snapshot.children) {
                    val order = child.getValue(Order::class.java)
                    if (order != null) {
                        if (order.isOngoingOrder()) {
                            ongoingOrders.add(order)
                        } else if (order.isPastOrder()) {
                            pastOrders.add(order)
                        }
                    }
                }

                ongoingOrders.sortWith(
                    compareBy<Order> { it.studentOrderPriority() }.thenByDescending { it.timestamp }
                )
                pastOrders.sortByDescending { it.timestamp }

                // Limit past orders to most recent 30
                val limitedPastOrders = if (pastOrders.size > 30) pastOrders.take(30) else pastOrders

                orderRows.clear()
                if (ongoingOrders.isNotEmpty()) {
                    orderRows.add(StudentOrderRow.Header("Ongoing Orders"))
                    orderRows.addAll(ongoingOrders.map { StudentOrderRow.OrderItem(it) })
                }
                if (limitedPastOrders.isNotEmpty()) {
                    orderRows.add(StudentOrderRow.Header("Past Orders"))
                    orderRows.addAll(limitedPastOrders.map { StudentOrderRow.OrderItem(it) })
                }

                adapter.notifyDataSetChanged()
                
                // Hide shimmer
                binding.shimmerViewContainer.stopShimmer()
                binding.shimmerViewContainer.visibility = View.GONE
                
                if (orderRows.isEmpty()) {
                    binding.ordersRecyclerView.visibility = View.GONE
                    binding.emptyStateLayout.visibility = View.VISIBLE
                } else {
                    binding.ordersRecyclerView.visibility = View.VISIBLE
                    binding.emptyStateLayout.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (isAdded) {
                    binding.shimmerViewContainer.stopShimmer()
                    binding.shimmerViewContainer.visibility = View.GONE
                    showError(error.message)
                }
            }
        }
        registerListener(ordersRef, listener)
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun Order.isOngoingOrder(): Boolean {
        return status in setOf("PLACED", "AWAITING_PAYMENT", "PREPARING", "READY")
    }

    private fun Order.isPastOrder(): Boolean {
        return status in setOf("COLLECTED", "COMPLETED", "REJECTED", "EXPIRED", "CANCELLED")
    }

    private fun Order.studentOrderPriority(): Int {
        return when (status) {
            "READY" -> 0
            "PREPARING" -> 1
            "AWAITING_PAYMENT" -> 2
            "PLACED" -> 3
            else -> 4
        }
    }

}
