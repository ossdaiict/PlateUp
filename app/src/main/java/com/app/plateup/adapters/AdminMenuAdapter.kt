package com.app.plateup.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.R
import com.app.plateup.databinding.ItemAdminMenuReadOnlyBinding
import com.app.plateup.models.MenuItem
import java.util.Locale

class AdminMenuAdapter(
    private val context: Context,
    private val displayList: ArrayList<Any>,
    private val onItemClick: (MenuItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_CATEGORY = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (displayList[position] is String) VIEW_TYPE_CATEGORY else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_CATEGORY) {
            val view = LayoutInflater.from(context).inflate(R.layout.item_category_separator, parent, false)
            CategoryViewHolder(view)
        } else {
            val binding = ItemAdminMenuReadOnlyBinding.inflate(LayoutInflater.from(context), parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun getItemCount(): Int = displayList.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is CategoryViewHolder) {
            holder.categoryText.text = displayList[position] as String
        } else if (holder is ItemViewHolder) {
            val item = displayList[position] as MenuItem
            
            holder.binding.itemNameText.text = item.name
            holder.binding.itemPriceText.text = "₹${item.price}"
            holder.binding.itemCategoryText.visibility = View.GONE
            
            // Ratings
            if (item.reviewCount > 0) {
                holder.binding.ratingLayout.visibility = View.VISIBLE
                holder.binding.ratingText.text = String.format(Locale.getDefault(), "%.1f", item.averageRating)
                holder.binding.ratingCountText.text = "(${item.reviewCount})"
            } else {
                holder.binding.ratingLayout.visibility = View.GONE
            }
            
            if (item.available) {
                holder.binding.availabilityText.text = "AVAILABLE"
                holder.binding.availabilityText.setTextColor(ContextCompat.getColor(context, R.color.success))
            } else {
                holder.binding.availabilityText.text = "OUT OF STOCK"
                holder.binding.availabilityText.setTextColor(ContextCompat.getColor(context, R.color.error))
            }

            holder.binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    inner class ItemViewHolder(val binding: ItemAdminMenuReadOnlyBinding) : RecyclerView.ViewHolder(binding.root)
    inner class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val categoryText: TextView = view.findViewById(R.id.categoryText)
    }
}
