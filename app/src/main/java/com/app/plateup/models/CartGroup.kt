package com.app.plateup.models

data class CartGroup(
    var canteenId: String = "",
    var canteenName: String = "",
    var canteenPackagingFee: Int = 0,
    var items: ArrayList<CartItem> = ArrayList(),
    var totalAmount: Int = 0
)
