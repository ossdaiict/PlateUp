package com.app.plateup.models

data class MenuItem(
    val id: String = "",
    val canteenId: String = "",
    val name: String = "",
    val price: Int = 0,
    val category: String = "",
    val available: Boolean = true,
    val takeawayAvailable: Boolean = true,
    val averageRating: Float = 0f,
    val reviewCount: Int = 0
)
