package com.app.plateup.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.databinding.ItemVendorMenuBinding
import com.app.plateup.models.MenuItem
import com.app.plateup.R

class VendorMenuAdapter(
    private val context: Context,
    private val menuList: ArrayList<MenuItem>,
    private val onAvailabilityClick: (MenuItem) -> Unit,
    private val onEditClick: (MenuItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var filteredList = ArrayList<Any>()
    
    companion object {
        private const val VIEW_TYPE_CATEGORY = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    init {
        updateFilteredList(menuList)
    }

    override fun getItemViewType(position: Int): Int {
        return if (filteredList[position] is String) VIEW_TYPE_CATEGORY else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_CATEGORY) {
            val view = LayoutInflater.from(context).inflate(R.layout.item_category_separator, parent, false)
            CategoryViewHolder(view)
        } else {
            val binding = ItemVendorMenuBinding.inflate(LayoutInflater.from(context), parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun getItemCount(): Int {
        return filteredList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is CategoryViewHolder) {
            holder.categoryText.text = filteredList[position] as String
        } else if (holder is ItemViewHolder) {
            val menuItem = filteredList[position] as MenuItem
            holder.binding.itemNameText.text = menuItem.name
            holder.binding.itemDetailsText.text = "₹${menuItem.price}"

            if (menuItem.reviewCount > 0) {
                holder.binding.ratingLayout.visibility = View.VISIBLE
                holder.binding.ratingText.text = String.format(java.util.Locale.getDefault(), "%.1f", menuItem.averageRating)
                holder.binding.ratingCountText.text = "(${menuItem.reviewCount})"
            } else {
                holder.binding.ratingLayout.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                val intent = android.content.Intent(context, com.app.plateup.activities.MenuItemDetailActivity::class.java).apply {
                    putExtra("MENU_ITEM_ID", menuItem.id)
                    putExtra("CANTEEN_ID", menuItem.canteenId)
                }
                context.startActivity(intent)
            }

            if (menuItem.available) {
                holder.binding.availabilityChip.text = "Available"
                holder.binding.availabilityChip.setTextColor(ContextCompat.getColor(context, R.color.accent_dark))
                holder.binding.availabilityChip.setBackgroundResource(R.drawable.bg_available_chip)
            } else {
                holder.binding.availabilityChip.text = "Unavailable"
                holder.binding.availabilityChip.setTextColor(ContextCompat.getColor(context, R.color.error))
                holder.binding.availabilityChip.setBackgroundResource(R.drawable.bg_unavailable_chip)
            }

            if (menuItem.takeawayAvailable) {
                holder.binding.takeawayChip.text = "🥡 Takeaway"
                holder.binding.takeawayChip.setTextColor(ContextCompat.getColor(context, R.color.success))
                holder.binding.takeawayChip.setBackgroundResource(R.drawable.bg_open_chip)
            } else {
                holder.binding.takeawayChip.text = "🍴 Dine-In"
                holder.binding.takeawayChip.setTextColor(ContextCompat.getColor(context, R.color.warning))
                holder.binding.takeawayChip.setBackgroundResource(R.drawable.bg_pending_chip)
            }

            holder.binding.availabilityChip.setOnClickListener {
                onAvailabilityClick(menuItem)
            }

            holder.binding.editPriceBtn.setOnClickListener {
                onEditClick(menuItem)
            }
        }
    }

    private fun updateFilteredList(items: List<MenuItem>) {
        val newList = ArrayList<Any>()
        if (items.isNotEmpty()) {
            val sortedItems = items.sortedWith(compareBy<MenuItem> { it.category }.thenBy { it.name })
            var currentCategory = ""
            for (item in sortedItems) {
                if (item.category != currentCategory) {
                    currentCategory = item.category
                    newList.add(currentCategory)
                }
                newList.add(item)
            }
        }
        filteredList = newList
    }

    fun filter(query: String) {
        if (query.isEmpty()) {
            updateFilteredList(menuList)
        } else {
            val result = menuList.filter { 
                it.name.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true)
            }
            updateFilteredList(result)
        }
        notifyDataSetChanged()
    }

    fun updateData(newList: List<MenuItem>) {
        menuList.clear()
        menuList.addAll(newList)
        updateFilteredList(menuList)
        notifyDataSetChanged()
    }

    inner class ItemViewHolder(val binding: ItemVendorMenuBinding) : RecyclerView.ViewHolder(binding.root)
    inner class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val categoryText: TextView = view.findViewById(R.id.categoryText)
    }
}
