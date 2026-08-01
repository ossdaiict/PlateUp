package com.app.plateup.activities

import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.plateup.R
import com.app.plateup.adapters.AdminCanteenFeedbackAdapter
import com.app.plateup.databinding.ActivityAdminFeedbackDashboardBinding
import com.app.plateup.models.Canteen
import com.app.plateup.models.OrderFeedback
import com.google.firebase.database.*
import java.util.Calendar

class AdminFeedbackDashboardActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminFeedbackDashboardBinding
    private lateinit var database: DatabaseReference
    private lateinit var adapter: AdminCanteenFeedbackAdapter
    
    private val allFeedback = ArrayList<OrderFeedback>()
    private val canteensMap = HashMap<String, MutableList<OrderFeedback>>()
    private val canteenNames = HashMap<String, String>()
    
    private var isTodayFilterActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminFeedbackDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().reference

        isTodayFilterActive = intent.getBooleanExtra("isTodayFilterActive", false)
        
        setupRecyclerView()
        setupListeners()
        loadData()

        binding.backImage.setOnClickListener { finish() }
        binding.sortIcon.setOnClickListener { showSortMenu(it) }
        
        binding.nestedScrollView.applySystemInsets(applyTop = false, applyBottom = true, useMargin = false)
        
        setupInteractiveStats()
        updateFilterIndicator()
        
        binding.clearFilterIcon.setOnClickListener { 
            toggleTodayFilter(false)
        }
        
        binding.clearFilterBtn.setOnClickListener { 
            toggleTodayFilter(false)
        }
    }

    private fun setupRecyclerView() {
        adapter = AdminCanteenFeedbackAdapter(this, canteensMap, canteenNames) { canteenId, canteenName ->
            val intent = Intent(this, AdminCanteenFeedbackActivity::class.java)
            intent.putExtra("canteenId", canteenId)
            intent.putExtra("canteenName", canteenName)
            intent.putExtra("isTodayFilterActive", isTodayFilterActive)
            startActivity(intent)
        }
        binding.canteensRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.canteensRecyclerView.adapter = adapter
        binding.canteensRecyclerView.applySystemInsets(applyTop = false, applyBottom = true, useMargin = false)
    }

    private fun setupListeners() {
        // No special listeners needed for now as loadData handles it
    }

    private fun loadData() {
        showLoading("Calculating insights...")
        
        // First load all canteens to get names
        database.child("canteens").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(canteenSnapshot: DataSnapshot) {
                canteenNames.clear()
                for (child in canteenSnapshot.children) {
                    val canteen = child.getValue(Canteen::class.java)
                    if (canteen != null) {
                        canteenNames[canteen.id] = canteen.name
                    }
                }
                
                // Then load all feedback
                database.child("orderFeedback").addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(feedbackSnapshot: DataSnapshot) {
                        allFeedback.clear()
                        canteensMap.clear()
                        
                        for (child in feedbackSnapshot.children) {
                            val feedback = child.getValue(OrderFeedback::class.java)
                            if (feedback != null) {
                                allFeedback.add(feedback)
                                canteensMap.getOrPut(feedback.canteenId) { mutableListOf() }.add(feedback)
                            }
                        }
                        
                        updateStats()
                        adapter.setTodayFilter(isTodayFilterActive)
                        hideLoading()
                        
                        updateEmptyState()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        hideLoading()
                        showError(error.message)
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                hideLoading()
                showError(error.message)
            }
        })
    }

    private fun updateStats() {
        if (allFeedback.isEmpty()) {
            binding.overallRatingText.text = "0.0"
            binding.totalFeedbackText.text = "0 feedbacks"
            binding.todayRatingText.text = "0.0"
            binding.todayCountText.text = "0 today"
            binding.lowRatedCountText.text = "0"
            return
        }

        val overallAvg = allFeedback.map { it.overallRating }.average()
        binding.overallRatingText.text = String.format(java.util.Locale.US, "%.1f", overallAvg)
        binding.totalFeedbackText.text = "${allFeedback.size} feedbacks"

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayFeedback = allFeedback.filter { it.submittedAt >= today }
        val todayAvg = if (todayFeedback.isNotEmpty()) todayFeedback.map { it.overallRating }.average() else 0.0
        
        binding.todayRatingText.text = String.format(java.util.Locale.US, "%.1f", todayAvg)
        binding.todayCountText.text = "${todayFeedback.size} today"

        val lowRatedCount = allFeedback.count { it.overallRating <= 2f }
        binding.lowRatedCountText.text = lowRatedCount.toString()
    }

    private fun showSortMenu(view: View) {
        val wrapper = ContextThemeWrapper(this, R.style.ThemeOverlay_App_PopupMenu)
        val popup = PopupMenu(wrapper, view)
        popup.menuInflater.inflate(R.menu.menu_feedback_sort, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            val mode = item.title.toString()
            adapter.sort(mode)
            showSuccess("Sorted by $mode")
            true
        }
        popup.show()
    }
    
    private fun setupInteractiveStats() {
        binding.statsLowRatedCard.setOnClickListener {
            adapter.sort("Needs Attention")
            showSuccess("Sorted by Needs Attention")
        }
        
        binding.statsTotalCard.setOnClickListener {
            adapter.sort("Most Feedback")
            showSuccess("Sorted by Most Feedback")
        }
        
        binding.statsTodayCard.setOnClickListener {
            toggleTodayFilter(!isTodayFilterActive)
        }
    }

    private fun toggleTodayFilter(enabled: Boolean) {
        isTodayFilterActive = enabled
        adapter.setTodayFilter(enabled)
        updateFilterIndicator()
        updateEmptyState()
        
        if (enabled) {
            showSuccess("Today's Feedback Filter Active")
        } else {
            showSuccess("Today's Feedback Filter Disabled")
        }
    }

    private fun updateFilterIndicator() {
        binding.filterIndicatorLayout.visibility = if (isTodayFilterActive) View.VISIBLE else View.GONE
        
        val density = resources.displayMetrics.density
        // Highlight the today card when active
        if (isTodayFilterActive) {
            binding.statsTodayCard.strokeWidth = (3 * density).toInt()
            binding.statsTodayCard.strokeColor = getColor(R.color.primary)
        } else {
            binding.statsTodayCard.strokeWidth = (1 * density).toInt()
            binding.statsTodayCard.strokeColor = getColor(R.color.border)
        }
    }

    private fun updateEmptyState() {
        val isEmpty = adapter.itemCount == 0
        binding.emptyStateText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        
        if (isTodayFilterActive && isEmpty) {
            binding.emptyStateText.text = "No feedback entries found for today"
            binding.clearFilterBtn.visibility = View.VISIBLE
        } else {
            binding.emptyStateText.text = "No feedback entries found"
            binding.clearFilterBtn.visibility = View.GONE
        }
    }
}
