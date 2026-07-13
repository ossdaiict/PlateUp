package com.app.plateup.models

data class PaymentData(
    val orderId: String = "",
    val amount: Int = 0,
    val currency: String = "INR",
    val gatewayData: Map<String, String> = emptyMap()
)
