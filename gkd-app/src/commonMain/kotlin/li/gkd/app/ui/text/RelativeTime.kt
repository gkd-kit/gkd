package li.gkd.app.ui.text

import androidx.compose.runtime.Composable
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.pluralStringResource
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
        years > 0 ->
            pluralStringResource(Res.plurals.time_years_ago, years.toInt(), years.toString())
        months > 0 ->
            pluralStringResource(Res.plurals.time_months_ago, months.toInt(), months.toString())
        weeks > 0 ->
            pluralStringResource(Res.plurals.time_weeks_ago, weeks.toInt(), weeks.toString())
        days > 0 -> pluralStringResource(Res.plurals.time_days_ago, days.toInt(), days.toString())
        hours > 0 ->
            pluralStringResource(Res.plurals.time_hours_ago, hours.toInt(), hours.toString())
        minutes > 0 ->
            pluralStringResource(Res.plurals.time_minutes_ago, minutes.toInt(), minutes.toString())
        else -> stringResource(Res.string.time_just_now)
    }
}
