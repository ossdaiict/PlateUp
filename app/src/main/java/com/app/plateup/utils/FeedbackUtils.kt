package com.app.plateup.utils

import java.util.Calendar

object FeedbackUtils {

    /**
     * Returns the timestamp for the beginning of the current day (00:00:00.000).
     */
    fun getTodayStartTimestamp(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Checks if a given timestamp falls within the current day.
     */
    fun isSubmittedToday(timestamp: Long): Boolean {
        return timestamp >= getTodayStartTimestamp()
    }
}
