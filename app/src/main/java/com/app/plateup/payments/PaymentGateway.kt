package com.app.plateup.payments

import android.app.Activity
import com.app.plateup.models.PaymentData
import com.app.plateup.models.PaymentResult

interface PaymentGateway {
    fun initiatePayment(
        activity: Activity,
        paymentData: PaymentData,
        onResult: (PaymentResult) -> Unit
    )
}
