package com.app.plateup.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.databinding.ItemOrderDetailBinding
import com.app.plateup.models.OrderItem

class OrderDetailsAdapter(
    private val context: Context,
    private val itemsList: ArrayList<OrderItem>
) : RecyclerView.Adapter<OrderDetailsAdapter.ViewHolder>() {

    inner class ViewHolder(
        val binding: ItemOrderDetailBinding
    ) : RecyclerView.ViewHolder(
        binding.root
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOrderDetailBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itemsList[position]
        holder.binding.itemNameText.text = item.name
        holder.binding.itemDetailsText.text = "${item.category} • x${item.quantity}"
        val totalPrice = item.price * item.quantity
        holder.binding.itemPriceText.text = "₹$totalPrice"
    }

    override fun getItemCount(): Int {
        return itemsList.size
    }
}