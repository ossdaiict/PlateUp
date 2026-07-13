package com.app.plateup.payments

import android.app.Activity
import com.app.plateup.models.PaymentData
import com.app.plateup.models.PaymentResult
import com.razorpay.Checkout
import com.razorpay.PaymentData as RazorpayPaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject

class RazorpayGateway : PaymentGateway {

    companion object {
        private var currentCallback: ((PaymentResult) -> Unit)? = null

        fun handleSuccess(paymentId: String?, data: RazorpayPaymentData?) {
            currentCallback?.invoke(PaymentResult.Success(paymentId ?: "", data?.signature))
            currentCallback = null
        }

        fun handleError(code: Int, response: String?) {
            currentCallback?.invoke(PaymentResult.Failure(code, response))
            currentCallback = null
        }
    }

    override fun initiatePayment(
        activity: Activity,
        paymentData: PaymentData,
        onResult: (PaymentResult) -> Unit
    ) {
        currentCallback = onResult
        val checkout = Checkout()
        val keyId = paymentData.gatewayData["keyId"] ?: ""
        checkout.setKeyID(keyId)

        try {
            val options = JSONObject()
            options.put("name", "PlateUp")
            options.put("description", "Order Payment")
            options.put("order_id", paymentData.orderId)
            options.put("theme.color", "#FF7A00")
            options.put("currency", paymentData.currency)
            options.put("amount", paymentData.amount)
            
            // Pass through other prefill or method options if available in gatewayData
            val prefill = JSONObject()
            paymentData.gatewayData["email"]?.let { prefill.put("email", it) }
            paymentData.gatewayData["contact"]?.let { prefill.put("contact", it) }
            paymentData.gatewayData["name"]?.let { prefill.put("name", it) }
            options.put("prefill", prefill)

            checkout.open(activity, options)
        } catch (e: Exception) {
            onResult(PaymentResult.Failure(-1, e.message))
            currentCallback = null
        }
    }
}
