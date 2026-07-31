package com.app.plateup.fragments

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.app.plateup.databinding.DialogOrderFeedbackBinding
import com.app.plateup.models.Order
import com.app.plateup.models.OrderFeedback
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderFeedbackBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogOrderFeedbackBinding? = null
    private val binding get() = _binding!!
    private var order: Order? = null

    interface OnFeedbackSubmittedListener {
        fun onFeedbackSubmitted()
    }

    private var listener: OnFeedbackSubmittedListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogOrderFeedbackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val orderJson = arguments?.getString("order_data")
        // In a real app, I'd pass the whole object or just ID and load it.
        // For this task, I'll assume the activity provides the order object if needed, 
        // but let's stick to the plan: pass minimal info via arguments.
        
        val canteenName = arguments?.getString("canteenName") ?: ""
        val orderId = arguments?.getString("orderId") ?: ""
        val orderNumber = orderId.takeLast(6)
        val collectedAt = arguments?.getLong("collectedAt", 0L) ?: 0L
        val orderDate = arguments?.getLong("orderDate", 0L) ?: 0L

        binding.titleText.text = "How was your order from $canteenName?"
        
        val formatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        val dateStr = formatter.format(Date(collectedAt))
        binding.orderInfoText.text = "Order #$orderNumber • Collected on $dateStr"

        binding.submitBtn.setOnClickListener {
            submitFeedback(orderId, orderNumber, canteenName, orderDate, collectedAt)
        }

        binding.notNowBtn.setOnClickListener {
            saveNotNowCooldown()
            dismiss()
        }
    }

    private fun submitFeedback(
        orderId: String,
        orderNumber: String,
        canteenName: String,
        orderDate: Long,
        collectedAt: Long
    ) {
        val rating = binding.ratingBar.rating
        if (rating == 0f) {
            Toast.makeText(context, "Please select a rating", Toast.LENGTH_SHORT).show()
            return
        }

        val comments = binding.commentsEditText.text.toString().trim()
        if (comments.isNotEmpty() && comments.isBlank()) {
            Toast.makeText(context, "Comments cannot be just whitespace", Toast.LENGTH_SHORT).show()
            return
        }

        val auth = FirebaseAuth.getInstance()
        val userId = auth.currentUser?.uid ?: return
        val studentName = arguments?.getString("studentName") ?: "Student"
        val canteenId = arguments?.getString("canteenId") ?: ""

        val feedbackId = FirebaseDatabase.getInstance().reference.child("orderFeedback").push().key ?: return
        
        val feedback = OrderFeedback(
            feedbackId = feedbackId,
            orderId = orderId,
            orderNumber = orderNumber,
            userId = userId,
            studentName = studentName,
            canteenId = canteenId,
            canteenName = canteenName,
            overallRating = rating,
            comments = comments,
            orderDate = orderDate,
            orderCollectedAt = collectedAt,
            submittedAt = System.currentTimeMillis()
        )

        val updates = hashMapOf<String, Any?>(
            "orderFeedback/$feedbackId" to feedback,
            "orders/$orderId/hasFeedback" to true
        )

        setCancelable(false)
        binding.submitBtn.isEnabled = false
        binding.notNowBtn.isEnabled = false

        FirebaseDatabase.getInstance().reference.updateChildren(updates)
            .addOnSuccessListener {
                showSuccessState()
            }
            .addOnFailureListener {
                setCancelable(true)
                binding.submitBtn.isEnabled = true
                binding.notNowBtn.isEnabled = true
                Toast.makeText(context, "Failed to submit feedback: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showSuccessState() {
        binding.formLayout.visibility = View.GONE
        binding.successLayout.visibility = View.VISIBLE
        
        Handler(Looper.getMainLooper()).postDelayed({
            if (isAdded) {
                listener?.onFeedbackSubmitted()
                dismiss()
            }
        }, 1500)
    }

    private fun saveNotNowCooldown() {
        val orderId = arguments?.getString("orderId") ?: return
        val prefs = requireContext().getSharedPreferences("feedback_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_not_now_$orderId", System.currentTimeMillis()).apply()
    }

    fun setOnFeedbackSubmittedListener(listener: OnFeedbackSubmittedListener) {
        this.listener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(order: Order): OrderFeedbackBottomSheet {
            val fragment = OrderFeedbackBottomSheet()
            val args = Bundle().apply {
                putString("orderId", order.orderId)
                putString("canteenId", order.canteenId)
                putString("canteenName", order.canteenName)
                putString("studentName", order.studentName)
                putLong("collectedAt", if (order.pickedUpAt > 0) order.pickedUpAt else order.timestamp)
                putLong("orderDate", order.timestamp)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
