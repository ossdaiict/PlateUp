package com.app.plateup.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.R
import com.app.plateup.databinding.ItemVendorOrderHistoryBinding
import com.app.plateup.models.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryOrdersAdapter(
    private val context: Context,
    private val onOrderClick: (Order) -> Unit
) : ListAdapter<Order, HistoryOrdersAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    class HistoryViewHolder(val binding: ItemVendorOrderHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemVendorOrderHistoryBinding.inflate(LayoutInflater.from(context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val order = getItem(position)
        holder.binding.studentNameText.text = order.studentName
        holder.binding.orderIdText.text = "#${order.orderId.takeLast(6)}"
        holder.binding.totalText.text = "₹${order.totalAmount}"

        holder.binding.statusBadge.text = order.status
        when (order.status) {
            "COMPLETED", "COLLECTED" -> {
                holder.binding.statusBadge.setTextColor(context.getColor(R.color.success))
                holder.binding.statusBadge.setBackgroundResource(R.drawable.bg_open_chip)
            }
            else -> {
                holder.binding.statusBadge.setTextColor(context.getColor(R.color.error))
                holder.binding.statusBadge.setBackgroundResource(R.drawable.bg_close_chip)
            }
        }

        val completionTime = order.statusTimestamps["COMPLETED"] ?: 
                             order.statusTimestamps["COLLECTED"] ?: 
                             order.statusTimestamps["REJECTED"] ?: 
                             order.timestamp
        val formatter = SimpleDateFormat("dd MMM • hh:mm a", Locale.getDefault())
        val formattedTime = formatter.format(Date(completionTime))
        
        // Simple "Today" logic
        val now = System.currentTimeMillis()
        val isToday = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(now)) == 
                      SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(completionTime))
        
        holder.binding.completionTimeText.text = if (isToday) {
            "Today • ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(completionTime))}"
        } else {
            formattedTime
        }

        holder.binding.root.setOnClickListener { onOrderClick(order) }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem.orderId == newItem.orderId
        }

        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem == newItem
        }
    }
}