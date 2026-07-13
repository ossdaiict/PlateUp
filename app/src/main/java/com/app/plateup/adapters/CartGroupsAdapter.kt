package com.app.plateup.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.databinding.ItemCartGroupBinding
import com.app.plateup.models.CartGroup
import java.util.ArrayList

class CartGroupsAdapter(
    private val context: Context,
    private val cartGroups: ArrayList<CartGroup>,
    private val onViewCartClick: (CartGroup) -> Unit
) : RecyclerView.Adapter<CartGroupsAdapter.ViewHolder>() {

    inner class ViewHolder(
        val binding: ItemCartGroupBinding
    ) : RecyclerView.ViewHolder(
        binding.root
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCartGroupBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return cartGroups.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cartGroup = cartGroups[position]

        holder.binding.canteenNameText.text = cartGroup.canteenName

        val summary = cartGroup.items.joinToString(", ") {
            "${it.name} x${it.quantity}"
        }

        holder.binding.itemsSummaryText.text = summary

        holder.binding.totalText.text = "₹${cartGroup.totalAmount}"

        holder.binding.viewCartBtn.setOnClickListener {
            onViewCartClick(cartGroup)
        }

    }

}