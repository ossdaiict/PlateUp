package com.app.plateup.payments

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.app.plateup.models.PaymentData
import com.app.plateup.models.PaymentResult

class MockGateway : PaymentGateway {
    override fun initiatePayment(
        activity: Activity,
        paymentData: PaymentData,
        onResult: (PaymentResult) -> Unit
    ) {
        // Show a dialog to simulate the gateway behavior
        AlertDialog.Builder(activity)
            .setTitle("PlateUp Mock Payment")
            .setMessage("Amount: ₹${paymentData.amount / 100}\n\nWould you like to simulate a successful payment?")
            .setPositiveButton("Simulate Success") { _, _ ->
                onResult(PaymentResult.Success("mock_txn_${System.currentTimeMillis()}", "mock_sig"))
            }
            .setNegativeButton("Simulate Failure") { _, _ ->
                onResult(PaymentResult.Failure(-1, "Development: Simulated payment failure"))
            }
            .setNeutralButton("Cancel") { _, _ ->
                onResult(PaymentResult.Cancelled)
            }
            .setCancelable(false)
            .show()
    }
}
