package com.app.plateup.models

object OrderStatus {
    const val PLACED = "PLACED"
    const val AWAITING_PAYMENT = "AWAITING_PAYMENT"
    const val PREPARING = "PREPARING"
    const val READY = "READY"
    const val COLLECTED = "COLLECTED"
    const val COMPLETED = "COMPLETED"
    const val REJECTED = "REJECTED"
    const val CANCELLED = "CANCELLED"
    const val EXPIRED = "EXPIRED"

    fun getDisplayLabel(status: String): String {
        return when (status) {
            PLACED -> "Placed"
            AWAITING_PAYMENT -> "Awaiting Payment"
            PREPARING -> "Preparing"
            READY -> "Ready"
            COLLECTED -> "Collected"
            COMPLETED -> "Completed"
            REJECTED -> "Rejected"
            CANCELLED -> "Cancelled"
            EXPIRED -> "Expired"
            else -> status
        }
    }
}
