package com.app.plateup.models

data class MenuRequest(
    val requestId: String = "",
    val canteenId: String = "",
    val canteenName: String = "",
    val oldMenuItem: MenuItem? = null,
    val newMenuItem: MenuItem?= null,
    val requestType: String = "",
    val status: String = "pending",
    val timestamp: Long = System.currentTimeMillis()
)
