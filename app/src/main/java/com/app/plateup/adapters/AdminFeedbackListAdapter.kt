package com.app.plateup.adapters

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.R
import com.app.plateup.databinding.ItemAdminFeedbackListBinding
import com.app.plateup.models.OrderFeedback
import java.text.SimpleDateFormat
import java.util.*

class AdminFeedbackListAdapter(
    private val context: Context,
    private var feedbackList: List<OrderFeedback>
) : RecyclerView.Adapter<AdminFeedbackListAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    inner class ViewHolder(val binding: ItemAdminFeedbackListBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminFeedbackListBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = feedbackList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val feedback = feedbackList[position]

        holder.binding.ratingBar.rating = feedback.overallRating
        holder.binding.dateText.text = dateFormat.format(Date(feedback.submittedAt))
        holder.binding.studentNameText.text = feedback.studentName
        holder.binding.orderNumberText.text = "Order #${feedback.orderNumber}"

        // Comments with Read More/Less
        if (feedback.comments.isEmpty()) {
            holder.binding.commentText.text = "No written comments provided"
            holder.binding.commentText.setTypeface(null, Typeface.ITALIC)
            holder.binding.commentText.setTextColor(ContextCompat.getColor(context, R.color.text_hint))
            holder.binding.readMoreText.visibility = View.GONE
        } else {
            holder.binding.commentText.text = feedback.comments
            holder.binding.commentText.setTypeface(null, Typeface.NORMAL)
            holder.binding.commentText.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            
            // Initial state: collapsed
            holder.binding.commentText.maxLines = 3
            holder.binding.readMoreText.visibility = if (feedback.comments.length > 100) View.VISIBLE else View.GONE
            holder.binding.readMoreText.text = "Read More"
        }

        holder.binding.readMoreText.setOnClickListener {
            if (holder.binding.commentText.maxLines == 3) {
                holder.binding.commentText.maxLines = Int.MAX_VALUE
                holder.binding.readMoreText.text = "Read Less"
            } else {
                holder.binding.commentText.maxLines = 3
                holder.binding.readMoreText.text = "Read More"
            }
        }

        // Highlight low ratings (1-2 stars)
        if (feedback.overallRating <= 2f) {
            holder.binding.feedbackCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.background))
            holder.binding.feedbackCard.strokeColor = ContextCompat.getColor(context, R.color.error)
            holder.binding.feedbackCard.strokeWidth = 2
        } else {
            holder.binding.feedbackCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_background))
            holder.binding.feedbackCard.strokeColor = ContextCompat.getColor(context, R.color.border)
            holder.binding.feedbackCard.strokeWidth = 1
        }
    }

    fun updateData(newList: List<OrderFeedback>) {
        feedbackList = newList
        notifyDataSetChanged()
    }
}
