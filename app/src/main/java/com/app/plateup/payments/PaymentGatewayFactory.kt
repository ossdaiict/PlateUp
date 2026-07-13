package com.app.plateup.payments

object PaymentGatewayFactory {
    fun getGateway(provider: String): PaymentGateway {
        return when (provider.uppercase()) {
            "PAYTM" -> PaytmGateway()
            "RAZORPAY" -> RazorpayGateway()
            "MOCK" -> MockGateway()
            else -> throw IllegalArgumentException("Unsupported payment provider: $provider")
        }
    }
}
