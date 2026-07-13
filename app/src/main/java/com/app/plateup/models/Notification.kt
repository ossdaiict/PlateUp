package com.app.plateup.models

data class Notification(
    var notificationId: String = "",
    var userId: String = "",
    var title: String = "",
    var message: String = "",
    var timestamp: Long = 0,
    var read: Boolean = false,
    var type: String = "",
    var orderId: String = ""
)
