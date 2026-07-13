package com.app.plateup.models

data class CartItem(
    var menuItemId: String = "",
    var canteenId: String = "",
    var uid: String = "",
    var canteenName: String = "",
    var canteenPackagingFee: Int = 0,
    var name: String = "",
    var category: String = "",
    var price: Int = 0,
    var quantity: Int = 0,
    var takeawayAvailable: Boolean = true
)
