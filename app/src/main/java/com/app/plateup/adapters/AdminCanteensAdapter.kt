package com.app.plateup.adapters

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.R
import com.app.plateup.databinding.ItemStudentCanteenBinding
import com.app.plateup.models.Canteen
import com.app.plateup.utils.CanteenUtils

class AdminCanteensAdapter(
    private val context: Context,
    private val canteensList: ArrayList<Canteen>,
    private val onCanteenClick: (Canteen) -> Unit
) : RecyclerView.Adapter<AdminCanteensAdapter.ViewHolder>() {

    private var filteredList = ArrayList<Canteen>(canteensList)

    inner class ViewHolder(val binding: ItemStudentCanteenBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStudentCanteenBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = filteredList.size

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val canteen = filteredList[position]
        val uiState = CanteenUtils.getUiState(context, canteen)

        holder.binding.canteenNameText.text = canteen.name
        holder.binding.itemCountText.text = when {
            canteen.itemCount > 1 -> "${canteen.itemCount} items available"
            canteen.itemCount == 1 -> "1 item available"
            else -> "No items available"
        }

        holder.binding.root.alpha = uiState.alpha
        holder.binding.statusChip.text = uiState.chipText
        holder.binding.statusChip.setTextColor(uiState.statusColor)
        holder.binding.statusChip.setBackgroundResource(
            if (uiState.isOpen) R.drawable.bg_open_chip else R.drawable.bg_close_chip
        )

        holder.binding.statusText.text = uiState.statusText

        if (canteen.packagingFee > 0) {
            holder.binding.packagingFeeText.visibility = View.VISIBLE
            holder.binding.packagingFeeText.text = "📦 Packaging ₹${canteen.packagingFee.toInt()}"
        } else {
            holder.binding.packagingFeeText.visibility = View.GONE
        }

        holder.binding.root.setOnClickListener {
            onCanteenClick(canteen)
        }
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            ArrayList(canteensList)
        } else {
            val result = canteensList.filter { 
                it.name.contains(query, ignoreCase = true)
            }
            ArrayList(result)
        }
        notifyDataSetChanged()
    }
    
    fun updateData(newList: List<Canteen>) {
        canteensList.clear()
        canteensList.addAll(newList)
        filteredList = ArrayList(canteensList)
        notifyDataSetChanged()
    }
}