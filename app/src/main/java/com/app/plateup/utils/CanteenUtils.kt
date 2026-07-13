package com.app.plateup.utils

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.app.plateup.R
import com.app.plateup.models.Canteen
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalTime
import java.util.Locale

data class AvailabilityUiState(
    val isOpen: Boolean,
    val statusText: String,
    val statusColor: Int,
    val chipText: String,
    val alpha: Float,
    val checkoutEnabled: Boolean,
    val checkoutWarning: String?
)

object CanteenUtils {

    @RequiresApi(Build.VERSION_CODES.O)
    fun isCurrentlyOpen(canteen: Canteen): Boolean {
        return when (canteen.availabilityMode) {
            "FORCE_OPEN" -> true
            "FORCE_CLOSED" -> false
            else -> { // AUTO
                if (canteen.open24Hours) return true
                try {
                    val current = LocalTime.now()
                    val opening = LocalTime.parse(canteen.openingTime)
                    val closing = LocalTime.parse(canteen.closingTime)

                    if (opening.isBefore(closing)) {
                        current >= opening && current < closing
                    } else {
                        current >= opening || current < closing
                    }
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getUiState(context: Context, canteen: Canteen): AvailabilityUiState {
        val isOpen = isCurrentlyOpen(canteen)
        
        val (statusText, chipText) = when (canteen.availabilityMode) {
            "FORCE_CLOSED" -> "Temporarily Closed" to "🔴 CLOSED"
            "FORCE_OPEN" -> "Open (Manual Override)" to "🟢 OPEN"
            else -> { // AUTO
                if (isOpen) {
                    val closeTime = if (canteen.open24Hours) "Open 24 Hours" else "Open until ${formatTimeForDisplay(canteen.closingTime)}"
                    closeTime to "🟢 OPEN"
                } else {
                    getOpeningMessage(canteen) to "🔴 CLOSED"
                }
            }
        }

        val statusColor = ContextCompat.getColor(
            context,
            if (isOpen) R.color.success else R.color.error
        )

        return AvailabilityUiState(
            isOpen = isOpen,
            statusText = statusText,
            statusColor = statusColor,
            chipText = chipText,
            alpha = if (isOpen) 1.0f else 0.55f,
            checkoutEnabled = isOpen,
            checkoutWarning = if (isOpen) null else "Ordering is currently unavailable as the canteen is closed."
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getStatusText(canteen: Canteen): String {
        if (canteen.availabilityMode == "FORCE_CLOSED") return "Temporarily Closed"
        if (canteen.availabilityMode == "FORCE_OPEN") return "Open (Currently)"
        if (canteen.open24Hours) return "Open 24 Hours"

        return if (isCurrentlyOpen(canteen)) {
            "Open until ${formatTimeForDisplay(canteen.closingTime)}"
        } else {
            getOpeningMessage(canteen)
        }
    }

    /**
     * Returns a user-friendly message about when the canteen will next open.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun getOpeningMessage(canteen: Canteen): String {
        if (canteen.open24Hours) return "Open 24 Hours"
        
        // If manually closed, we don't show "Opens at..." because it depends on vendor
        if (canteen.availabilityMode == "FORCE_CLOSED") return "Temporarily Closed"

        return try {
            val current = LocalTime.now()
            val opening = LocalTime.parse(canteen.openingTime)
            val closing = LocalTime.parse(canteen.closingTime)
            val displayTime = formatTimeForDisplay(canteen.openingTime)

            val opensLaterToday = if (opening.isBefore(closing)) {
                current.isBefore(opening)
            } else {
                true
            }

            if (opensLaterToday) {
                val minutesUntilOpen = Duration.between(current, opening).toMinutes()
                    .let { if (it < 0) it + 24 * 60 else it }
                formatCountdown(minutesUntilOpen, displayTime)
            } else {
                "Opens tomorrow at $displayTime"
            }
        } catch (e: Exception) {
            "Opens at ${formatTimeForDisplay(canteen.openingTime)}"
        }
    }

    private fun formatCountdown(totalMinutes: Long, displayTime: String): String {
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60

        val duration = when {
            hours > 0 && mins > 0 -> "${hours} hr${if (hours > 1) "s" else ""} ${mins} min${if (mins > 1) "s" else ""}"
            hours > 0              -> "${hours} hr${if (hours > 1) "s" else ""}"
            else                   -> "${mins} min${if (mins > 1) "s" else ""}"
        }

        return "Opens in $duration ($displayTime)"
    }

    fun formatTimeForDisplay(time24: String): String {
        return try {
            val parser = SimpleDateFormat("HH:mm", Locale.getDefault())
            val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
            formatter.format(parser.parse(time24)!!)
        } catch (e: Exception) {
            time24
        }
    }

}
