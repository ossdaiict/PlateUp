package com.app.plateup.models

sealed class PaymentResult {
    data class Success(val paymentId: String, val signature: String?) : PaymentResult()
    object Cancelled : PaymentResult()
    data class Failure(val errorCode: Int, val errorMessage: String?) : PaymentResult()
}
