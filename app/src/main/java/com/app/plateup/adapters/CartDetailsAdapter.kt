package com.app.plateup.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.databinding.ItemCartDetailBinding
import com.app.plateup.models.CartItem

class CartDetailsAdapter(
    private val context: Context,
    private val cartItems: ArrayList<CartItem>,
    private val onIncreaseClick: (CartItem) -> Unit,
    private val onDecreaseClick: (CartItem) -> Unit
) : RecyclerView.Adapter<CartDetailsAdapter.ViewHolder>() {

    inner class ViewHolder(
        val binding: ItemCartDetailBinding
    ) : RecyclerView.ViewHolder(
        binding.root
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCartDetailBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return cartItems.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cartItem = cartItems[position]
        holder.binding.itemNameText.text = cartItem.name
        holder.binding.itemPriceText.text = "₹${cartItem.price}"

        holder.binding.quantityText.text = cartItem.quantity.toString()
        holder.binding.increaseBtn.setOnClickListener { onIncreaseClick(cartItem) }
        holder.binding.decreaseBtn.setOnClickListener { onDecreaseClick(cartItem) }
    }


}