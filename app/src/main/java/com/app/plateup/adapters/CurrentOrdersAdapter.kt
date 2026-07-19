package com.app.plateup.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.R
import com.app.plateup.databinding.ItemVendorOrderBinding
import com.app.plateup.models.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CurrentOrdersAdapter(
    private val context: Context,
    private val onOrderClick: (Order) -> Unit
) : ListAdapter<Order, CurrentOrdersAdapter.OrderViewHolder>(OrderDiffCallback()) {

    class OrderViewHolder(val binding: ItemVendorOrderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemVendorOrderBinding.inflate(LayoutInflater.from(context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        bindOrder(holder, getItem(position))
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains("PAYLOAD_AGE")) {
            updateAgeIndicator(holder, getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun bindOrder(holder: OrderViewHolder, order: Order) {
        holder.binding.studentNameText.text = order.studentName
        holder.binding.orderIdText.text = "#${order.orderId.takeLast(6)}"
        
        val itemsSummary = order.items.joinToString(", ") {
            "${it.name} x${it.quantity}"
        }
        holder.binding.itemsText.text = itemsSummary
        holder.binding.totalText.text = "₹${order.totalAmount}"

        holder.binding.orderTypeText.text =
            if (order.orderType == "TAKEAWAY") "🥡 Takeaway" else "🍴 Dine-In"

        val formatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        val formattedTime = formatter.format(Date(order.timestamp))
        holder.binding.timeText.text = formattedTime

        holder.binding.statusChip.text = if (order.status == "AWAITING_PAYMENT") "AWAITING PAYMENT" else order.status

        updateStatusStyles(holder, order.status)
        updateAgeIndicator(holder, order)

        holder.binding.root.setOnClickListener { onOrderClick(order) }
    }

    private fun updateStatusStyles(holder: OrderViewHolder, status: String) {
        when (status) {
            "PLACED", "AWAITING_PAYMENT" -> {
                holder.binding.statusChip.setTextColor(ContextCompat.getColor(context, R.color.primary))
                holder.binding.statusChip.setBackgroundResource(R.drawable.bg_pending_chip)
            }
            "ACCEPTED", "READY", "COLLECTED", "COMPLETED" -> {
                holder.binding.statusChip.setTextColor(ContextCompat.getColor(context, R.color.success))
                holder.binding.statusChip.setBackgroundResource(R.drawable.bg_open_chip)
            }
            "REJECTED", "EXPIRED", "CANCELLED" -> {
                holder.binding.statusChip.setTextColor(ContextCompat.getColor(context, R.color.error))
                holder.binding.statusChip.setBackgroundResource(R.drawable.bg_close_chip)
            }
            "PREPARING" -> {
                holder.binding.statusChip.setTextColor(ContextCompat.getColor(context, R.color.admin_auth))
                holder.binding.statusChip.setBackgroundResource(R.drawable.bg_add_request_chip)
            }
        }
    }

    private fun updateAgeIndicator(holder: OrderViewHolder, order: Order) {
        val ageMs = System.currentTimeMillis() - order.timestamp
        val ageMins = TimeUnit.MILLISECONDS.toMinutes(ageMs)

        holder.binding.ageIndicator.text = if (ageMins == 0L) "Now" else "$ageMins min"
        
        when {
            ageMins < 10 -> {
                holder.binding.ageIndicator.setBackgroundResource(R.drawable.bg_age_green)
            }
            ageMins < 15 -> {
                holder.binding.ageIndicator.setBackgroundResource(R.drawable.bg_age_amber)
            }
            else -> {
                holder.binding.ageIndicator.setBackgroundResource(R.drawable.bg_age_red)
            }
        }
    }

    class OrderDiffCallback : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem.orderId == newItem.orderId
        }

        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem == newItem
        }
    }
}