package com.app.plateup.models

data class OrderItem(
    var menuItemId: String = "",
    var name: String = "",
    var category: String = "",
    var price: Int = 0,
    var quantity: Int = 0,
    var takeawayAvailable: Boolean = true
)
