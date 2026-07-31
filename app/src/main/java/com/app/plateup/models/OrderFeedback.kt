package com.app.plateup.models

data class OrderFeedback(
    var feedbackId: String = "",
    var orderId: String = "",
    var orderNumber: String = "",
    var userId: String = "",
    var studentName: String = "",
    var canteenId: String = "",
    var canteenName: String = "",
    var overallRating: Float = 0f,
    var comments: String = "",
    var orderDate: Long = 0,
    var orderCollectedAt: Long = 0,
    var submittedAt: Long = 0
)
