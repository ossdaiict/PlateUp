package com.app.plateup.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.R
import com.app.plateup.databinding.ItemSearchResultMenuBinding
import com.app.plateup.models.SearchResultMenuItem

class SearchResultsAdapter(
    private val context: Context,
    private val resultsList: ArrayList<SearchResultMenuItem>
) : RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {

    inner class ViewHolder(
        val binding: ItemSearchResultMenuBinding
    ) : RecyclerView.ViewHolder(
        binding.root
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultMenuBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return resultsList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = resultsList[position]
        val menuItem = result.menuItem
        holder.binding.itemNameText.text = menuItem.name
        holder.binding.itemDetailsText.text = "${menuItem.category} • ₹${menuItem.price}"
        holder.binding.canteenNameText.text = result.canteenName

        if (menuItem.reviewCount > 0) {
            holder.binding.ratingLayout.visibility = View.VISIBLE
            holder.binding.ratingText.text = String.format(java.util.Locale.getDefault(), "%.1f", menuItem.averageRating)
            holder.binding.ratingCountText.text = "(${menuItem.reviewCount})"
        } else {
            holder.binding.ratingLayout.visibility = View.GONE
        }

        if (menuItem.available) {
            holder.binding.availabilityText.text = "AVAILABLE"
            holder.binding.availabilityText.setTextColor(ContextCompat.getColor(context, R.color.success))
            holder.binding.availabilityText.setBackgroundResource(R.drawable.bg_available_chip)
            holder.binding.rootLayout.alpha = 1f
        } else {
            holder.binding.availabilityText.text = "UNAVAILABLE"
            holder.binding.availabilityText.setTextColor(ContextCompat.getColor(context, R.color.error))
            holder.binding.availabilityText.setBackgroundResource(R.drawable.bg_unavailable_chip)
            holder.binding.rootLayout.alpha = 0.6f
        }

        if (result.canteenOpen) {
            holder.binding.canteenStatusChip.text = "OPEN"
            holder.binding.canteenStatusChip.setTextColor(ContextCompat.getColor(context, R.color.success))
            holder.binding.canteenStatusChip.setBackgroundResource(R.drawable.bg_open_chip)
            holder.binding.availabilityText.visibility = View.VISIBLE
            holder.binding.rootLayout.alpha = 1f
        } else {
            holder.binding.canteenStatusChip.text = "CLOSED"
            holder.binding.canteenStatusChip.setTextColor(ContextCompat.getColor(context, R.color.error))
            holder.binding.canteenStatusChip.setBackgroundResource(R.drawable.bg_close_chip)
            holder.binding.availabilityText.visibility = View.GONE
            holder.binding.rootLayout.alpha = 0.6f
        }

        holder.binding.root.setOnClickListener {
            val intent = android.content.Intent(context, com.app.plateup.activities.MenuItemDetailActivity::class.java).apply {
                putExtra("MENU_ITEM_ID", menuItem.id)
                putExtra("CANTEEN_ID", menuItem.canteenId)
                putExtra("FROM_SEARCH", true)
            }
            context.startActivity(intent)
        }
    }

}