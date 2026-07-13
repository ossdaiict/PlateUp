package com.app.plateup.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.R
import com.app.plateup.databinding.ItemMenuRequestBinding
import com.app.plateup.models.MenuRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MenuRequestsAdapter(
    private val context: Context,
    private val requestsList: ArrayList<MenuRequest>,
    private val onApproveClick: (MenuRequest) -> Unit,
    private val onRejectClick: (MenuRequest) -> Unit
) : RecyclerView.Adapter<MenuRequestsAdapter.ViewHolder>() {

    inner class ViewHolder(
        val binding: ItemMenuRequestBinding
    ) : RecyclerView.ViewHolder(
        binding.root
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMenuRequestBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return requestsList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = requestsList[position]
        val menuItem = request.newMenuItem ?: return
        holder.binding.itemNameText.text = menuItem.name
        if (request.requestType == "add") {
            holder.binding.requestTypeChip.text = "ADD REQUEST"
            holder.binding.requestTypeChip.setTextColor(ContextCompat.getColor(context, R.color.accent_dark))
            holder.binding.requestTypeChip.setBackgroundResource(R.drawable.bg_add_request_chip)
            holder.binding.requestDetailsText.text = "${menuItem.category} • ₹${menuItem.price}"
            holder.binding.priceChangeText.visibility = View.GONE
        } else {
            holder.binding.requestTypeChip.text = "UPDATE REQUEST"
            holder.binding.requestTypeChip.setTextColor(ContextCompat.getColor(context, R.color.admin_auth))
            holder.binding.requestTypeChip.setBackgroundResource(R.drawable.bg_update_request_chip)
            holder.binding.requestDetailsText.text = menuItem.category
            holder.binding.priceChangeText.visibility = View.VISIBLE

            val oldPrice = request.oldMenuItem?.price ?: 0
            val newPrice = menuItem.price
            holder.binding.priceChangeText.text = "Price Change: ₹$oldPrice ➡️ ₹$newPrice"
        }

        holder.binding.canteenNameText.text = request.canteenName

        if (request.newMenuItem.takeawayAvailable) {
            holder.binding.takeawayText.text = "🥡 Available for Takeaway"
            holder.binding.takeawayText.setTextColor(ContextCompat.getColor(context, R.color.success))
        } else {
            holder.binding.takeawayText.text = "🥡 Dine-In Only"
            holder.binding.takeawayText.setTextColor(ContextCompat.getColor(context, R.color.warning))
        }

        val timestamp = request.timestamp
        val formatter = SimpleDateFormat("dd MMM yyyy • h:mm a", Locale.getDefault())
        val formattedTime = formatter.format(Date(timestamp))
        holder.binding.timestampText.text = formattedTime

        holder.binding.approveBtn.setOnClickListener { onApproveClick(request) }
        holder.binding.rejectBtn.setOnClickListener { onRejectClick(request) }

    }


}