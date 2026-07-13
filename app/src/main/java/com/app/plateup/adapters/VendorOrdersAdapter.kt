package com.app.plateup.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.R
import com.app.plateup.databinding.ItemOrderSectionHeaderBinding
import com.app.plateup.databinding.ItemVendorOrderBinding
import com.app.plateup.models.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VendorOrdersAdapter(
    private val context: Context,
    private val orderRows: ArrayList<VendorOrderRow>,
    private val onOrderClick: (Order) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class VendorOrderRow {
        data class Header(val title: String) : VendorOrderRow()
        data class OrderItem(val order: Order) : VendorOrderRow()
    }

    inner class OrderViewHolder(
        val binding: ItemVendorOrderBinding
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
            is VendorOrderRow.Header -> VIEW_TYPE_HEADER
            is VendorOrderRow.OrderItem -> VIEW_TYPE_ORDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val binding = ItemOrderSectionHeaderBinding.inflate(inflater, parent, false)
            HeaderViewHolder(binding)
        } else {
            val binding = ItemVendorOrderBinding.inflate(inflater, parent, false)
            OrderViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = orderRows[position]) {
            is VendorOrderRow.Header -> bindHeader(holder as HeaderViewHolder, row)
            is VendorOrderRow.OrderItem -> bindOrder(holder as OrderViewHolder, row.order)
        }
    }

    private fun bindHeader(holder: HeaderViewHolder, row: VendorOrderRow.Header) {
        holder.binding.headerText.text = row.title
    }

    private fun bindOrder(holder: OrderViewHolder, order: Order) {
        holder.binding.studentNameText.text = order.studentName
        val itemsSummary = order.items.joinToString(", ") {
            "${it.name} x${it.quantity}"
        }
        holder.binding.itemsText.text = itemsSummary
        holder.binding.totalText.text = "₹${order.totalAmount}"

        holder.binding.orderTypeText.text =
            if (order.orderType == "TAKEAWAY") {
                "🥡 Takeaway"
            } else {
                "🍴 Dine-In"
            }

        val formatter = SimpleDateFormat("dd MMM, hh: mm a", Locale.getDefault())
        val formattedTime = formatter.format(Date(order.timestamp))
        holder.binding.timeText.text = formattedTime

        holder.binding.statusChip.text = if (order.status == "AWAITING_PAYMENT") "AWAITING PAYMENT" else order.status

        when (order.status) {
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
