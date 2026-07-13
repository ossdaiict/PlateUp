package com.app.plateup.models

data class Review(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val menuItemId: String = "",
    val rating: Float = 0f,
    val comment: String = "",
    val timestamp: Long = 0L
)
