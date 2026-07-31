package com.app.plateup.activities

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.plateup.R
import com.app.plateup.adapters.AdminFeedbackListAdapter
import com.app.plateup.databinding.ActivityAdminCanteenFeedbackBinding
import com.app.plateup.models.OrderFeedback
import com.google.firebase.database.*

class AdminCanteenFeedbackActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminCanteenFeedbackBinding
    private lateinit var database: DatabaseReference
    private lateinit var adapter: AdminFeedbackListAdapter
    
    private val allFeedback = ArrayList<OrderFeedback>()
    private var canteenId: String = ""
    private var canteenName: String = ""
    private var isTodayFilterActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminCanteenFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        canteenId = intent.getStringExtra("canteenId") ?: ""
        canteenName = intent.getStringExtra("canteenName") ?: "Canteen Feedback"
        isTodayFilterActive = intent.getBooleanExtra("isTodayFilterActive", false)
        
        binding.titleText.text = canteenName
        database = FirebaseDatabase.getInstance().reference

        setupRecyclerView()
        setupFilters()
        loadFeedback()

        binding.nestedScrollView.applySystemInsets(applyTop = false, applyBottom = true, useMargin = false)
        binding.backImage.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = AdminFeedbackListAdapter(this, emptyList())
        binding.feedbackRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.feedbackRecyclerView.adapter = adapter
    }

    private fun setupFilters() {
        if (isTodayFilterActive) {
            binding.chipToday.isChecked = true
        }

        binding.filterChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            filterFeedback()
        }
    }

    private fun loadFeedback() {
        showLoading("Loading feedback...")
        
        database.child("orderFeedback")
            .orderByChild("canteenId")
            .equalTo(canteenId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    allFeedback.clear()
                    for (child in snapshot.children) {
                        val feedback = child.getValue(OrderFeedback::class.java)
                        if (feedback != null) {
                            allFeedback.add(feedback)
                        }
                    }
                    
                    // Reverse chronological order
                    allFeedback.sortByDescending { it.submittedAt }
                    
                    updateSummary()
                    filterFeedback()
                    hideLoading()
                }

                override fun onCancelled(error: DatabaseError) {
                    hideLoading()
                    showError(error.message)
                }
            })
    }

    private fun updateSummary() {
        if (allFeedback.isEmpty()) {
            binding.avgRatingLargeText.text = "0.0"
            binding.ratingBarSummary.rating = 0f
            binding.totalFeedbackSummaryText.text = "0 feedbacks"
            return
        }

        val avg = allFeedback.map { it.overallRating }.average().toFloat()
        binding.avgRatingLargeText.text = String.format(java.util.Locale.US, "%.1f", avg)
        binding.ratingBarSummary.rating = avg
        binding.totalFeedbackSummaryText.text = "${allFeedback.size} feedbacks"

        val dist = IntArray(6)
        allFeedback.forEach {
            val r = it.overallRating.toInt()
            if (r in 1..5) dist[r]++
        }

        val max = dist.maxOrNull() ?: 1
        binding.progress5.max = max
        binding.progress5.progress = dist[5]
        binding.count5.text = dist[5].toString()

        binding.progress4.max = max
        binding.progress4.progress = dist[4]
        binding.count4.text = dist[4].toString()

        binding.progress3.max = max
        binding.progress3.progress = dist[3]
        binding.count3.text = dist[3].toString()

        binding.progress2.max = max
        binding.progress2.progress = dist[2]
        binding.count2.text = dist[2].toString()

        binding.progress1.max = max
        binding.progress1.progress = dist[1]
        binding.count1.text = dist[1].toString()
    }

    private fun filterFeedback() {
        val checkedId = binding.filterChipGroup.checkedChipId
        
        val filtered = when (checkedId) {
            R.id.chipToday -> allFeedback.filter { com.app.plateup.utils.FeedbackUtils.isSubmittedToday(it.submittedAt) }
            R.id.chip5 -> allFeedback.filter { it.overallRating.toInt() == 5 }
            R.id.chip4 -> allFeedback.filter { it.overallRating.toInt() == 4 }
            R.id.chip3 -> allFeedback.filter { it.overallRating.toInt() == 3 }
            R.id.chip2 -> allFeedback.filter { it.overallRating.toInt() == 2 }
            R.id.chip1 -> allFeedback.filter { it.overallRating.toInt() == 1 }
            else -> allFeedback
        }
        
        adapter.updateData(filtered)
        binding.emptyStateText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        
        if (checkedId == R.id.chipToday && filtered.isEmpty()) {
            binding.emptyStateText.text = "No feedback entries found for today"
        } else {
            binding.emptyStateText.text = "No reviews found"
        }
    }
}
