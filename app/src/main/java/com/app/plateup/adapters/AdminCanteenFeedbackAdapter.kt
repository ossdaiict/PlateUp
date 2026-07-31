package com.app.plateup.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.app.plateup.databinding.ItemAdminCanteenFeedbackBinding
import com.app.plateup.models.OrderFeedback
import java.util.*

class AdminCanteenFeedbackAdapter(
    private val context: Context,
    private val canteensMap: Map<String, List<OrderFeedback>>,
    private val canteenNames: Map<String, String>,
    private val onCanteenClick: (String, String) -> Unit
) : RecyclerView.Adapter<AdminCanteenFeedbackAdapter.ViewHolder>() {

    private var canteenIds = canteenNames.keys.toList()
    private var currentSortMode = "Needs Attention"
    private var isTodayFilterActive = false

    inner class ViewHolder(val binding: ItemAdminCanteenFeedbackBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminCanteenFeedbackBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = canteenIds.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val canteenId = canteenIds[position]
        val feedbackList = if (isTodayFilterActive) {
            canteensMap[canteenId]?.filter { com.app.plateup.utils.FeedbackUtils.isSubmittedToday(it.submittedAt) } ?: emptyList()
        } else {
            canteensMap[canteenId] ?: emptyList()
        }
        val canteenName = canteenNames[canteenId] ?: "Unknown Canteen"

        val avgRating = if (feedbackList.isNotEmpty()) {
            feedbackList.map { it.overallRating }.average().toFloat()
        } else 0f

        holder.binding.canteenNameText.text = canteenName
        holder.binding.ratingBar.rating = avgRating
        holder.binding.ratingText.text = String.format(Locale.US, "%.1f", avgRating)
        holder.binding.totalCountText.text = "(${feedbackList.size} reviews)"

        // Rating distribution summary
        val dist = IntArray(6)
        feedbackList.forEach {
            val r = it.overallRating.toInt()
            if (r in 1..5) dist[r]++
        }
        holder.binding.distributionText.text = "5★: ${dist[5]} | 4★: ${dist[4]} | 3★: ${dist[3]} | 2★: ${dist[2]} | 1★: ${dist[1]}"

        holder.binding.root.setOnClickListener {
            onCanteenClick(canteenId, canteenName)
        }
    }

    fun sort(mode: String) {
        currentSortMode = mode
        val k = 5f // Constant for weighted ranking
        
        // Refresh the list of IDs from the canteenNames map
        var allIds = canteenNames.keys.toList()

        // If today filter is active, only show canteens that have feedback today
        if (isTodayFilterActive) {
            allIds = allIds.filter { id ->
                canteensMap[id]?.any { com.app.plateup.utils.FeedbackUtils.isSubmittedToday(it.submittedAt) } ?: false
            }
        }

        canteenIds = when (mode) {
            "Needs Attention" -> {
                allIds.sortedBy { id ->
                    val list = if (isTodayFilterActive) {
                        canteensMap[id]?.filter { com.app.plateup.utils.FeedbackUtils.isSubmittedToday(it.submittedAt) } ?: emptyList()
                    } else {
                        canteensMap[id] ?: emptyList()
                    }
                    val avg = if (list.isNotEmpty()) list.map { it.overallRating }.average().toFloat() else 5f
                    val count = list.size
                    // Weighted Rank: Lower is more "needs attention"
                    (avg * count + 5f * k) / (count + k)
                }
            }
            "Highest Rating" -> {
                allIds.sortedByDescending { id ->
                    val list = if (isTodayFilterActive) {
                        canteensMap[id]?.filter { com.app.plateup.utils.FeedbackUtils.isSubmittedToday(it.submittedAt) } ?: emptyList()
                    } else {
                        canteensMap[id] ?: emptyList()
                    }
                    if (list.isNotEmpty()) list.map { it.overallRating }.average() else 0.0
                }
            }
            "Most Feedback" -> {
                allIds.sortedByDescending { id ->
                    val list = if (isTodayFilterActive) {
                        canteensMap[id]?.filter { com.app.plateup.utils.FeedbackUtils.isSubmittedToday(it.submittedAt) } ?: emptyList()
                    } else {
                        canteensMap[id] ?: emptyList()
                    }
                    list.size
                }
            }
            "Alphabetical" -> {
                allIds.sortedBy { canteenNames[it] ?: "" }
            }
            else -> allIds
        }
        notifyDataSetChanged()
    }

    fun setTodayFilter(enabled: Boolean) {
        isTodayFilterActive = enabled
        sort(currentSortMode)
    }
}
