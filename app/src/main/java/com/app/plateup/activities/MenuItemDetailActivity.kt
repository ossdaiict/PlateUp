package com.app.plateup.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RatingBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.app.plateup.R
import com.app.plateup.adapters.ReviewAdapter
import com.app.plateup.databinding.ActivityMenuItemDetailBinding
import com.app.plateup.models.Canteen
import com.app.plateup.models.MenuItem
import com.app.plateup.models.Review
import com.app.plateup.models.Student
import com.app.plateup.utils.CanteenUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MenuItemDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityMenuItemDetailBinding
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var menuItemId: String? = null
    private var canteenId: String? = null
    private var menuItem: MenuItem? = null
    private lateinit var reviewAdapter: ReviewAdapter
    private val reviewsList = mutableListOf<Review>()

    private var currentUserId: String = ""
    private var currentUserName: String = ""
    private var isAdmin: Boolean = false
    private var isVendor: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMenuItemDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = FirebaseDatabase.getInstance().reference
        auth = FirebaseAuth.getInstance()

        menuItemId = intent.getStringExtra("MENU_ITEM_ID")
        canteenId = intent.getStringExtra("CANTEEN_ID")
        
        if (menuItemId == null || canteenId == null) {
            finish()
            return
        }

        checkUserRole()
        setupToolbar()
        setupRecyclerView()
        loadMenuItemDetails()
        loadReviews()

        binding.nestedScrollView.applySystemInsets(applyTop = false, applyBottom = true)

        val fromSearch = intent.getBooleanExtra("FROM_SEARCH", false)
        binding.goToMenuBtn.visibility = if (fromSearch) View.VISIBLE else View.GONE
        binding.goToMenuDivider.visibility = if (fromSearch) View.VISIBLE else View.GONE

        binding.addReviewBtn.setOnClickListener {
            showReviewDialog()
        }

        binding.goToMenuBtn.setOnClickListener {
            openCanteenMenu()
        }
    }

    private fun checkUserRole() {
        val adminPrefs = getSharedPreferences("admin_session", MODE_PRIVATE)
        isAdmin = adminPrefs.getBoolean("admin_logged_in", false)

        val vendorPrefs = getSharedPreferences("vendor_session", MODE_PRIVATE)
        isVendor = vendorPrefs.getBoolean("vendor_logged_in", false)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            currentUserId = currentUser.uid
            lifecycleScope.launch {
                val snapshot = database.child("students/$currentUserId").get().await()
                val student = snapshot.getValue(Student::class.java)
                currentUserName = student?.name ?: "Anonymous"
            }
        }
    }

    private fun setupToolbar() {
        binding.backImage.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        reviewAdapter = ReviewAdapter(
            reviewsList,
            currentUserId,
            isAdmin,
            onDeleteClick = { review -> deleteReview(review) },
            onEditClick = { review -> showReviewDialog(review) }
        )
        binding.reviewsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.reviewsRecyclerView.adapter = reviewAdapter
    }

    private fun loadMenuItemDetails() {
        val detailRef = database.child("menus/$canteenId/$menuItemId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                menuItem = snapshot.getValue(MenuItem::class.java)
                menuItem?.let {
                    binding.itemNameText.text = it.name
                    binding.itemCategoryText.text = it.category
                    binding.itemPriceText.text = "₹${it.price}"
                    binding.averageRatingBar.rating = it.averageRating
                    binding.averageRatingText.text = String.format(java.util.Locale.getDefault(), "%.1f", it.averageRating)
                    binding.reviewCountText.text = "(${it.reviewCount} reviews)"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Failed to load details: ${error.message}")
            }
        }
        registerListener(detailRef, listener)
    }

    private fun loadReviews() {
        val reviewRef = database.child("reviews/$menuItemId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                reviewsList.clear()
                var hasUserReviewed = false
                for (reviewSnapshot in snapshot.children) {
                    val review = reviewSnapshot.getValue(Review::class.java)
                    if (review != null) {
                        reviewsList.add(review)
                        if (review.userId == currentUserId) {
                            hasUserReviewed = true
                        }
                    }
                }
                reviewsList.sortByDescending { it.timestamp }
                reviewAdapter.updateReviews(reviewsList)

                // Only show "Add Review" button if student hasn't reviewed yet
                if (!isAdmin && !isVendor && currentUserId.isNotEmpty()) {
                    binding.addReviewBtn.visibility = if (hasUserReviewed) View.GONE else View.VISIBLE
                } else {
                    binding.addReviewBtn.visibility = View.GONE
                }
                
                if (reviewsList.isEmpty()) {
                    binding.noReviewsText.visibility = View.VISIBLE
                    if (isAdmin || isVendor) {
                        binding.noReviewsText.text = "No reviews yet for this item."
                    } else {
                        binding.noReviewsText.text = "No reviews yet. Be the first to review!"
                    }
                } else {
                    binding.noReviewsText.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Failed to load reviews: ${error.message}")
            }
        }
        registerListener(reviewRef, listener)
    }

    private fun showReviewDialog(existingReview: Review? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_review, null)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.dialogRatingBar)
        val commentEdit = dialogView.findViewById<EditText>(R.id.dialogCommentEdit)

        if (existingReview != null) {
            ratingBar.rating = existingReview.rating
            commentEdit.setText(existingReview.comment)
        }

        AlertDialog.Builder(this)
            .setTitle(if (existingReview == null) "Add Review" else "Edit Review")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val rating = ratingBar.rating
                val comment = commentEdit.text.toString().trim()

                if (rating == 0f) {
                    showError("Please select a rating")
                    return@setPositiveButton
                }

                saveReview(existingReview?.id, rating, comment)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveReview(reviewId: String?, rating: Float, comment: String) {
        val id = reviewId ?: database.child("reviews/$menuItemId").push().key ?: return
        val review = Review(
            id = id,
            userId = currentUserId,
            userName = currentUserName,
            menuItemId = menuItemId!!,
            rating = rating,
            comment = comment,
            timestamp = System.currentTimeMillis()
        )

        lifecycleScope.launch {
            try {
                showLoading("Saving review...")
                database.child("reviews/$menuItemId/$id").setValue(review).await()
                updateMenuItemRating()
                hideLoading()
                showSuccess("Review saved")
            } catch (e: Exception) {
                hideLoading()
                showError("Error saving review: ${e.message}")
            }
        }
    }

    private fun deleteReview(review: Review) {
        showConfirmationDialog(
            title = "Delete Review",
            message = "Are you sure you want to delete this review?",
            positiveButton = "Delete",
            onConfirm = {
                lifecycleScope.launch {
                    try {
                        showLoading("Deleting review...")
                        database.child("reviews/$menuItemId/${review.id}").removeValue().await()
                        updateMenuItemRating()
                        hideLoading()
                        showSuccess("Review deleted")
                    } catch (e: Exception) {
                        hideLoading()
                        showError("Error deleting review: ${e.message}")
                    }
                }
            }
        )
    }

    private suspend fun updateMenuItemRating() {
        // Recalculate average rating
        try {
            val snapshot = database.child("reviews/$menuItemId").get().await()
            var totalRating = 0f
            var count = 0
            for (reviewSnapshot in snapshot.children) {
                val r = reviewSnapshot.getValue(Review::class.java)
                if (r != null) {
                    totalRating += r.rating
                    count++
                }
            }
            val average = if (count > 0) totalRating / count else 0f
            
            val updates = mapOf(
                "averageRating" to average,
                "reviewCount" to count
            )
            database.child("menus/$canteenId/$menuItemId").updateChildren(updates).await()
        } catch (e: Exception) {
            android.util.Log.e("MenuItemDetail", "Error updating menu item rating", e)
        }
    }

    private fun openCanteenMenu() {
        if (canteenId == null || menuItemId == null) return

        database.child("canteens/$canteenId").addListenerForSingleValueEvent(object : ValueEventListener {
            @RequiresApi(android.os.Build.VERSION_CODES.O)
            override fun onDataChange(snapshot: DataSnapshot) {
                val canteen = snapshot.getValue(Canteen::class.java) ?: return
                
                val intent = Intent(this@MenuItemDetailActivity, StudentMenuActivity::class.java)
                val isOpen = CanteenUtils.isCurrentlyOpen(canteen)
                intent.putExtra("canteenId", canteen.id)
                intent.putExtra("canteenName", canteen.name)
                intent.putExtra("canteenPackagingFee", canteen.packagingFee.toInt())
                intent.putExtra("canteenOpen", isOpen)
                
                if (!isOpen) {
                    intent.putExtra("canteenOpeningMessage", CanteenUtils.getOpeningMessage(canteen))
                }
                
                startActivity(intent)
                finish()
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Error finding canteen: ${error.message}")
            }
        })
    }
}
