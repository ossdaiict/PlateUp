package com.app.plateup.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.R
import com.app.plateup.databinding.ItemOrderSectionHeaderBinding
import com.app.plateup.databinding.ItemStudentOrderBinding
import com.app.plateup.models.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StudentOrdersAdapter(
    private val context: Context,
    private val orderRows: ArrayList<StudentOrderRow>,
    private val onOrderClick: (Order) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class StudentOrderRow {
        data class Header(val title: String) : StudentOrderRow()
        data class OrderItem(val order: Order) : StudentOrderRow()
    }

    inner class OrderViewHolder(
        val binding: ItemStudentOrderBinding
    ) : RecyclerView.ViewHolder(
        binding.root
    )

    inner class HeaderViewHolder(
        val binding: ItemOrderSectionHeaderBinding
    ) : RecyclerView.ViewHolder(
        binding.root
    )

    override fun getItemViewType(position: Int): Int {
        return when (orderRows[position]) {
            is StudentOrderRow.Header -> VIEW_TYPE_HEADER
            is StudentOrderRow.OrderItem -> VIEW_TYPE_ORDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val binding = ItemOrderSectionHeaderBinding.inflate(inflater, parent, false)
            HeaderViewHolder(binding)
        } else {
            val binding = ItemStudentOrderBinding.inflate(inflater, parent, false)
            OrderViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = orderRows[position]) {
            is StudentOrderRow.Header -> bindHeader(holder as HeaderViewHolder, row)
            is StudentOrderRow.OrderItem -> bindOrder(holder as OrderViewHolder, row.order)
        }
    }

    private fun bindHeader(holder: HeaderViewHolder, row: StudentOrderRow.Header) {
        holder.binding.headerText.text = row.title
    }

    private fun bindOrder(holder: OrderViewHolder, order: Order) {
        holder.binding.orderIdText.text = "Order #${order.orderId.takeLast(6)}"
        holder.binding.canteenNameText.text = order.canteenName
        val itemsSummary = order.items.joinToString(", ") {
            "${it.name} x${it.quantity}"
        }
        holder.binding.itemsText.text = itemsSummary
        holder.binding.totalText.text = "Grand Total • ₹${order.totalAmount}"
        holder.binding.statusChip.text = if (order.status == "AWAITING_PAYMENT") "PAYMENT REQUIRED" else order.status

        holder.binding.orderTypeText.text =
            if (order.orderType == "TAKEAWAY") {
                "🥡 Takeaway"
            } else {
                "🍴 Dine-In"
            }

        when (order.status) {
            "PLACED", "AWAITING_PAYMENT" -> {
                holder.binding.statusChip.setTextColor(ContextCompat.getColor(context, R.color.primary))
                holder.binding.statusChip.setBackgroundResource(R.drawable.bg_pending_chip)
            }
            "PREPARING" -> {
                holder.binding.statusChip.setTextColor(ContextCompat.getColor(context, R.color.admin_auth))
                holder.binding.statusChip.setBackgroundResource(R.drawable.bg_add_request_chip)
            }
            "ACCEPTED", "READY", "COLLECTED", "COMPLETED" -> {
                holder.binding.statusChip.setTextColor(ContextCompat.getColor(context, R.color.success))
                holder.binding.statusChip.setBackgroundResource(R.drawable.bg_open_chip)
            }
            "REJECTED", "EXPIRED", "CANCELLED" -> {
                holder.binding.statusChip.setTextColor(ContextCompat.getColor(context, R.color.error))
                holder.binding.statusChip.setBackgroundResource(R.drawable.bg_close_chip)
            }
        }

        val formatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        val formattedTime = formatter.format(Date(order.timestamp))
        holder.binding.timeText.text = formattedTime

        holder.binding.root.setOnClickListener { onOrderClick(order) }

    }

    override fun getItemCount(): Int {
        return orderRows.size
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ORDER = 1
    }
}
