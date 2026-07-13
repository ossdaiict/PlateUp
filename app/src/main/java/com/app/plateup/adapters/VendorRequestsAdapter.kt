package com.app.plateup.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.R
import com.app.plateup.databinding.ItemOrderSectionHeaderBinding
import com.app.plateup.databinding.ItemVendorRequestBinding
import com.app.plateup.models.MenuRequest
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

class VendorRequestsAdapter(
    private val context: Context,
    private val requestRows: ArrayList<VendorRequestRow>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class VendorRequestRow {
        data class Header(val title: String) : VendorRequestRow()
        data class RequestItem(val request: MenuRequest) : VendorRequestRow()
    }

    inner class RequestViewHolder(
        val binding: ItemVendorRequestBinding
    ) : RecyclerView.ViewHolder(
        binding.root
    )

    inner class HeaderViewHolder(
        val binding: ItemOrderSectionHeaderBinding
    ) : RecyclerView.ViewHolder(
        binding.root
    )

    override fun getItemViewType(position: Int): Int {
        return when (requestRows[position]) {
            is VendorRequestRow.Header -> VIEW_TYPE_HEADER
            is VendorRequestRow.RequestItem -> VIEW_TYPE_REQUEST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val binding = ItemOrderSectionHeaderBinding.inflate(inflater, parent, false)
            HeaderViewHolder(binding)
        } else {
            val binding = ItemVendorRequestBinding.inflate(inflater, parent, false)
            RequestViewHolder(binding)
        }
    }

    override fun getItemCount(): Int {
        return requestRows.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = requestRows[position]) {
            is VendorRequestRow.Header -> bindHeader(holder as HeaderViewHolder, row)
            is VendorRequestRow.RequestItem -> bindRequest(holder as RequestViewHolder, row.request)
        }
    }

    private fun bindHeader(holder: HeaderViewHolder, row: VendorRequestRow.Header) {
        holder.binding.headerText.text = row.title
    }

    private fun bindRequest(holder: RequestViewHolder, request: MenuRequest) {
        val menuItem = request.newMenuItem ?: return

        holder.binding.itemNameText.text = menuItem.name

        if (request.requestType == "add") {
            holder.binding.itemDetailsText.text = "${menuItem.category} • ₹${menuItem.price}"
            holder.binding.priceChangeText.visibility = View.GONE
        } else {
            holder.binding.priceChangeText.visibility = View.VISIBLE
            val oldPrice = request.oldMenuItem?.price ?: 0
            val newPrice = menuItem.price
            holder.binding.priceChangeText.text = "Price Change: ₹$oldPrice ➡️ ₹$newPrice"
        }

        holder.binding.itemDetailsText.text = menuItem.category
        holder.binding.takeawayText.text =
            if (request.newMenuItem.takeawayAvailable) {
                "🥡 Available for Takeaway"
            } else {
                "🍽️ Dine-In Only"
            }
        holder.binding.takeawayText.setTextColor(ContextCompat.getColor(context,
            if (request.newMenuItem.takeawayAvailable) R.color.success
            else R.color.warning
        ))

        when (request.status) {
            "pending" -> {
                holder.binding.statusChip.text = "PENDING"
                holder.binding.statusChip.setTextColor(ContextCompat.getColor(context, R.color.warning))
                holder.binding.statusChip.setBackgroundResource(R.drawable.bg_pending_chip)
            }
            "approved" -> {
                holder.binding.statusChip.text = "APPROVED"
                holder.binding.statusChip.setTextColor(ContextCompat.getColor(context, R.color.success))
                holder.binding.statusChip.setBackgroundResource(R.drawable.bg_approve_btn)
            }
            "rejected" -> {
                holder.binding.statusChip.text = "REJECTED"
                holder.binding.statusChip.setTextColor(ContextCompat.getColor(context, R.color.error))
                holder.binding.statusChip.setBackgroundResource(R.drawable.bg_reject_btn)
            }
        }

        val formatter = SimpleDateFormat("dd MMM yyyy • h:mm a", Locale.getDefault())
        holder.binding.timestampText.text = formatter.format(Date(request.timestamp))

    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_REQUEST = 1
    }

}
