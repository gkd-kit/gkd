package li.gkd.app.util

import li.gkd.app.text.UiStrings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ThrottleTimer(
    private val interval: Long = 500L,
) {
    private var lastAccessTime: Long = 0L
    fun expired(): Boolean {
        val t = System.currentTimeMillis()
        if (t - lastAccessTime > interval) {
            lastAccessTime = t
            return true
        }
        return false
    }
}

object TimeUtils {
    fun formatTimeAgo(timestamp: Long): String {
        val currentTime = System.currentTimeMillis()
        val timeDifference = currentTime - timestamp

        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeDifference)
        val hours = TimeUnit.MILLISECONDS.toHours(timeDifference)
        val days = TimeUnit.MILLISECONDS.toDays(timeDifference)
        val weeks = days / 7
        val months = (days / 30)
        val years = (days / 365)
        return when {
            years > 0 -> UiStrings.time_years_ago(years)
            months > 0 -> UiStrings.time_months_ago(months)
            weeks > 0 -> UiStrings.time_weeks_ago(weeks)
            days > 0 -> UiStrings.time_days_ago(days)
            hours > 0 -> UiStrings.time_hours_ago(hours)
            minutes > 0 -> UiStrings.time_minutes_ago(minutes)
            else -> UiStrings.time_just_now
        }
    }

    private val formatDateMap by lazy { hashMapOf<String, SimpleDateFormat>() }

    fun formatDate(timestamp: Long, formatStr: String): String {
        var df = formatDateMap[formatStr]
        if (df == null) {
            df = SimpleDateFormat(formatStr, Locale.getDefault())
            formatDateMap[formatStr] = df
        }
        return df.format(timestamp)
    }

    @Composable
    fun throttle(
        fn: (() -> Unit),
    ): (() -> Unit) {
        val timer = remember { ThrottleTimer() }
        return remember(fn) {
            {
                if (timer.expired()) {
                    fn.invoke()
                }
            }
        }
    }

    @Composable
    fun <T> throttle(
        fn: ((T) -> Unit),
    ): ((T) -> Unit) {
        val timer = remember { ThrottleTimer() }
        return remember(fn) {
            {
                if (timer.expired()) {
                    fn.invoke(it)
                }
            }
        }
    }
}
