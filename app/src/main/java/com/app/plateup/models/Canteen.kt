package com.app.plateup.models

data class Canteen (
    val id: String = "",
    val name: String = "",
    val vendorCode: String = "",
    var itemCount: Int = 0,
    var openingTime: String = "",
    var closingTime: String = "",
    var open24Hours: Boolean = false,
    var packagingFee: Double = 0.0,
    var configurationComplete: Boolean = false,
    var paymentProvider: String = "PAYTM",
    var paymentStatus: String = "NOT_CONFIGURED",
    var providerAccountId: String = "",
    var availabilityMode: String = "AUTO",
    var availabilityUpdatedAt: Long = 0
)