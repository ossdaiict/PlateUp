package com.app.plateup.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.R
import com.app.plateup.databinding.ItemStudentMenuBinding
import com.app.plateup.models.MenuItem

class StudentMenuAdapter(
    private val context: Context,
    private val displayList: ArrayList<Any>,
    private val cartQuantities: HashMap<String, Int>,
    private var canteenOpen: Boolean,
    private val onAddClick: (MenuItem) -> Unit,
    private val onIncreaseClick: (MenuItem) -> Unit,
    private val onDecreaseClick: (MenuItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_CATEGORY = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    fun updateCanteenStatus(open: Boolean) {
        canteenOpen = open
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (displayList[position] is String) VIEW_TYPE_CATEGORY else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_CATEGORY) {
            val view = LayoutInflater.from(context).inflate(R.layout.item_category_separator, parent, false)
            CategoryViewHolder(view)
        } else {
            val binding = ItemStudentMenuBinding.inflate(LayoutInflater.from(context), parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun getItemCount(): Int {
        return displayList.size
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is CategoryViewHolder) {
            holder.categoryText.text = displayList[position] as String
        } else if (holder is ItemViewHolder) {
            val menuItem = displayList[position] as MenuItem
            val quantity = cartQuantities[menuItem.id] ?: 0

            holder.itemView.setOnClickListener {
                val intent = android.content.Intent(context, com.app.plateup.activities.MenuItemDetailActivity::class.java).apply {
                    putExtra("MENU_ITEM_ID", menuItem.id)
                    putExtra("CANTEEN_ID", menuItem.canteenId)
                }
                context.startActivity(intent)
            }

            holder.binding.itemNameText.text = menuItem.name
            holder.binding.itemDetailsText.text = "₹${menuItem.price}"

            if (menuItem.reviewCount > 0) {
                holder.binding.ratingLayout.visibility = View.VISIBLE
                holder.binding.ratingText.text = String.format(java.util.Locale.getDefault(), "%.1f", menuItem.averageRating)
                holder.binding.ratingCountText.text = "(${menuItem.reviewCount})"
            } else {
                holder.binding.ratingLayout.visibility = View.GONE
            }

            if (!canteenOpen) {
                holder.binding.rootLayout.alpha = 0.5f
                holder.binding.unavailableText.visibility = View.GONE
                holder.binding.takeawayText.visibility = View.GONE
                holder.binding.quantityLayout.visibility = View.GONE
                holder.binding.addBtn.visibility = View.VISIBLE
                holder.binding.addBtn.apply {
                    text = "CLOSED"
                    isEnabled = false
                }
                return
            }

            if (menuItem.available) {
                holder.binding.rootLayout.alpha = 1f
                holder.binding.unavailableText.visibility = View.GONE
                holder.binding.addBtn.isEnabled = true
                holder.binding.addBtn.text = "ADD"
                holder.binding.takeawayText.visibility =
                    if (menuItem.takeawayAvailable) View.GONE
                    else View.VISIBLE
            } else {
                holder.binding.rootLayout.alpha = 0.5f
                holder.binding.unavailableText.visibility = View.VISIBLE
                holder.binding.addBtn.isEnabled = false
                holder.binding.addBtn.text = "UNAVAILABLE"
                holder.binding.takeawayText.visibility = View.GONE
            }

            if (quantity == 0) {
                holder.binding.addBtn.visibility = View.VISIBLE
                holder.binding.quantityLayout.visibility = View.GONE
            } else {
                holder.binding.addBtn.visibility = View.GONE
                holder.binding.quantityLayout.visibility = View.VISIBLE
                holder.binding.quantityText.text = quantity.toString()
            }

            holder.binding.addBtn.setOnClickListener {
                onAddClick(menuItem)
            }

            holder.binding.increaseBtn.setOnClickListener {
                onIncreaseClick(menuItem)
            }

            holder.binding.decreaseBtn.setOnClickListener {
                onDecreaseClick(menuItem)
            }
        }
    }

    inner class ItemViewHolder(val binding: ItemStudentMenuBinding) : RecyclerView.ViewHolder(binding.root)
    inner class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val categoryText: TextView = view.findViewById(R.id.categoryText)
    }
}
