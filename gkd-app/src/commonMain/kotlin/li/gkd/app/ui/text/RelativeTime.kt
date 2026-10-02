package li.gkd.app.ui.text

import androidx.compose.runtime.Composable
import li.gkd.app.resources.Res
import li.gkd.app.resources.time_days_ago
import li.gkd.app.resources.time_hours_ago
import li.gkd.app.resources.time_just_now
import li.gkd.app.resources.time_minutes_ago
import li.gkd.app.resources.time_months_ago
import li.gkd.app.resources.time_weeks_ago
import li.gkd.app.resources.time_years_ago
import org.jetbrains.compose.resources.stringResource

@Composable
fun formatTimeAgo(timestamp: Long): String {
    val currentTime = System.currentTimeMillis()
    val timeDifference = currentTime - timestamp

    val minutes = timeDifference / 60000
    val hours = timeDifference / 3600000
    val days = timeDifference / 86400000
    val weeks = days / 7
    val months = (days / 30)
    val years = (days / 365)
    return when {
        years > 0 -> stringResource(Res.string.time_years_ago, years.toString())
        months > 0 -> stringResource(Res.string.time_months_ago, months.toString())
        weeks > 0 -> stringResource(Res.string.time_weeks_ago, weeks.toString())
        days > 0 -> stringResource(Res.string.time_days_ago, days.toString())
        hours > 0 -> stringResource(Res.string.time_hours_ago, hours.toString())
        minutes > 0 -> stringResource(Res.string.time_minutes_ago, minutes.toString())
        else -> stringResource(Res.string.time_just_now)
    }
}
