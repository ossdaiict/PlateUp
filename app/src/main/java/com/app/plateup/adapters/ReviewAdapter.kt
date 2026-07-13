package com.app.plateup.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.databinding.ItemReviewBinding
import com.app.plateup.models.Review
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReviewAdapter(
    private var reviews: List<Review>,
    private val currentUserId: String,
    private val isAdmin: Boolean,
    private val onDeleteClick: (Review) -> Unit,
    private val onEditClick: (Review) -> Unit
) : RecyclerView.Adapter<ReviewAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val review = reviews[position]
        holder.binding.userNameText.text = review.userName
        holder.binding.reviewRatingBar.rating = review.rating
        holder.binding.reviewCommentText.text = review.comment
        
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        holder.binding.reviewDateText.text = dateFormat.format(Date(review.timestamp))

        // Show edit/delete buttons if the user is the author or an admin
        if (isAdmin || review.userId == currentUserId) {
            holder.binding.adminControls.visibility = View.VISIBLE
            // Only authors can edit their reviews
            holder.binding.editReviewBtn.visibility = if (review.userId == currentUserId) View.VISIBLE else View.GONE
        } else {
            holder.binding.adminControls.visibility = View.GONE
        }

        holder.binding.deleteReviewBtn.setOnClickListener { onDeleteClick(review) }
        holder.binding.editReviewBtn.setOnClickListener { onEditClick(review) }
    }

    override fun getItemCount(): Int = reviews.size

    fun updateReviews(newReviews: List<Review>) {
        reviews = newReviews
        notifyDataSetChanged()
    }
}
