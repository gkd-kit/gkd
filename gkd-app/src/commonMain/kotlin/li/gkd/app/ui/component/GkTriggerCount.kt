package li.gkd.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import li.gkd.app.resources.*
import li.gkd.app.time.format
import li.gkd.app.ui.text.formatTimeAgo
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkTriggerCount(
    count: Long,
    modifier: Modifier = Modifier,
    lastTriggerTime: Long? = null,
    showTooltip: Boolean = true,
    showTooltipOnClick: Boolean = false,
    showExactTime: Boolean = false,
) {
    if (count == 0L) return
    val text = count.toString()
    val timeDescription = lastTriggerTime?.let {
        stringResource(
            Res.string.trigger_last_time,
            if (showExactTime) it.format("yyyy-MM-dd HH:mm:ss") else formatTimeAgo(it),
        )
    }
    val description =
        listOfNotNull(
                stringResource(Res.string.trigger_count_value, text),
                timeDescription,
            )
            .joinToString("，")
    GkTooltipIconButtonBox(
        if (showTooltip)
            timeDescription ?: stringResource(Res.string.notification_variable_triggers)
        else null,
        showOnClick = showTooltipOnClick,
    ) {
        Row(
            modifier.clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GkIcon(
                imageVector = GkIcons.Bolt,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
