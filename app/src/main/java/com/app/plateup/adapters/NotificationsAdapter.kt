package com.app.plateup.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.R
import com.app.plateup.databinding.ItemNotificationBinding
import com.app.plateup.models.Notification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationsAdapter(
    private val context: Context,
    private val notificationsList: ArrayList<Notification>,
    private val onNotificationClick: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.ViewHolder>() {

    inner class ViewHolder(
        val binding: ItemNotificationBinding
    ) : RecyclerView.ViewHolder(
        binding.root
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notification = notificationsList[position]
        holder.binding.titleText.text = notification.title
        holder.binding.messageText.text = notification.message

        val formatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        holder.binding.timeText.text = formatter.format(Date(notification.timestamp))

        if (!notification.read) {
            holder.binding.root.strokeWidth = 4
            holder.binding.root.strokeColor = ContextCompat.getColor(context, R.color.primary)
            holder.binding.root.alpha = 1f
            holder.binding.root.cardElevation = 8f
        } else {
            holder.binding.root.strokeWidth = 0
            holder.binding.root.alpha = 0.7f
            holder.binding.root.cardElevation = 2f
        }

        holder.binding.root.setOnClickListener { onNotificationClick(notification) }
    }

    override fun getItemCount(): Int {
        return notificationsList.size
    }
}