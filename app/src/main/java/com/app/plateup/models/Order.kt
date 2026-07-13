package com.app.plateup.models

data class Order(
    var orderId: String = "",
    var userId: String = "",
    var studentName: String = "",
    var canteenId: String = "",
    var canteenName: String = "",
    var totalAmount: Int = 0,
    var status: String = "PLACED",
    var paymentStatus: String = "PENDING",
    var paymentProvider: String = "",
    var paymentData: PaymentData? = null,
    var paymentId: String = "",
    var refundId: String = "",
    var timestamp: Long = 0,
    var statusTimestamps: Map<String, Long> = emptyMap(),
    var items: ArrayList<OrderItem> = ArrayList(),
    var orderType: String = "DINE_IN",
    var packagingFee: Int = 0,
    val itemsTotal: Int = totalAmount - packagingFee,
    var pickupToken: String = "",
    var pickupCode: String = "",
    var pickedUpAt: Long = 0,
    var paymentDueAt: Long = 0,
    var paymentCompletedAt: Long = 0
)
