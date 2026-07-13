package com.app.plateup.payments

import android.app.Activity
import android.os.Bundle
import com.app.plateup.models.PaymentData
import com.app.plateup.models.PaymentResult
import com.paytm.pgsdk.PaytmOrder
import com.paytm.pgsdk.PaytmPaymentTransactionCallback
import com.paytm.pgsdk.TransactionManager

class PaytmGateway : PaymentGateway {
    
    private val REQUEST_CODE = 101

    override fun initiatePayment(
        activity: Activity,
        paymentData: PaymentData,
        onResult: (PaymentResult) -> Unit
    ) {
        val orderId = paymentData.orderId
        val mid = paymentData.gatewayData["mid"] ?: ""
        val txnToken = paymentData.gatewayData["txnToken"] ?: ""
        val amount = (paymentData.amount / 100.0).toString() // Paytm takes amount as String in Rupees
        val callbackUrl = paymentData.gatewayData["callbackUrl"] ?: "https://securegw.paytm.in/theia/paytmCallback?ORDER_ID=$orderId"

        val paytmOrder = PaytmOrder(orderId, mid, txnToken, amount, callbackUrl)

        val transactionManager = TransactionManager(paytmOrder, object : PaytmPaymentTransactionCallback {
            override fun onTransactionResponse(bundle: Bundle?) {
                val status = bundle?.getString("STATUS")
                val txnId = bundle?.getString("TXNID")
                val responseMsg = bundle?.getString("RESPMSG")
                
                if (status == "TXN_SUCCESS") {
                    onResult(PaymentResult.Success(txnId ?: "", null))
                } else {
                    onResult(PaymentResult.Failure(-1, responseMsg))
                }
            }

            override fun networkNotAvailable() {
                onResult(PaymentResult.Failure(-2, "Network not available"))
            }

            override fun onErrorProceed(s: String?) {
                onResult(PaymentResult.Failure(-3, s))
            }

            override fun clientAuthenticationFailed(s: String?) {
                onResult(PaymentResult.Failure(-4, s))
            }

            override fun someUIErrorOccurred(s: String?) {
                onResult(PaymentResult.Failure(-5, s))
            }

            override fun onErrorLoadingWebPage(i: Int, s: String?, s1: String?) {
                onResult(PaymentResult.Failure(i, s))
            }

            override fun onBackPressedCancelTransaction() {
                onResult(PaymentResult.Cancelled)
            }

            override fun onTransactionCancel(s: String?, bundle: Bundle?) {
                onResult(PaymentResult.Cancelled)
            }
        })

        transactionManager.setShowPaymentUrl("https://securegw.paytm.in/theia/api/v1/showPaymentPage")
        transactionManager.startTransaction(activity, REQUEST_CODE)
    }
}
