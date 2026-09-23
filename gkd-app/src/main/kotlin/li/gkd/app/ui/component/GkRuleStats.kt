package li.gkd.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.icon.SportsBasketball

data class GkRuleStatsData(
    val globalGroups: Int? = null,
    val apps: Int? = null,
    val appGroups: Int? = null,
    val enabledOnly: Boolean = false,
    val enabledAppGroups: Int? = null,
    val showZeroCounts: Boolean = false,
) {
    val hasRules: Boolean get() = (globalGroups ?: 0) > 0 || (appGroups ?: 0) > 0

    val description: String
        get() = buildList {
            val prefix = if (enabledOnly) UiStrings.enabled else UiStrings.total_prefix
            globalGroups?.takeIf { it > 0 || showZeroCounts }?.let { add(UiStrings.global_rule_group_stats(prefix, it)) }
            apps?.takeIf { it > 0 || showZeroCounts }?.let { add(UiStrings.app_count(it)) }
            appGroups?.takeIf { it > 0 || showZeroCounts }?.let {
                add(if (enabledAppGroups != null) UiStrings.app_rule_group_enabled_stats(enabledAppGroups, it)
                    else UiStrings.app_rule_group_stats(prefix, it))
            }
        }.joinToString("，")
}

@Composable
fun GkRuleStats(
    stats: GkRuleStatsData,
    modifier: Modifier = Modifier,
    emptyText: String = UiStrings.rules_empty,
) {
    val entries = buildList {
        stats.globalGroups?.takeIf { it > 0 || stats.showZeroCounts }?.let { add(SportsBasketball to it.toString()) }
        stats.apps?.takeIf { it > 0 || stats.showZeroCounts }?.let { add(GkIcons.Android to it.toString()) }
        stats.appGroups?.takeIf { it > 0 || stats.showZeroCounts }?.let { add(GkIcons.FlashOn to (stats.enabledAppGroups?.let { enabled -> "$enabled/$it" } ?: it.toString())) }
    }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    if (!stats.hasRules && !stats.showZeroCounts) {
        Text(
            text = emptyText,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        FlowRow(
            modifier = modifier.clearAndSetSemantics { contentDescription = stats.description },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            entries.forEachIndexed { index, (icon, count) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (index > 0) {
                        Text("·", style = MaterialTheme.typography.bodyMedium, color = color)
                    }
                    GkIcon(icon, modifier = Modifier.size(16.dp), contentDescription = null, tint = color)
                    Text(count, style = MaterialTheme.typography.bodyMedium, color = color)
                }
            }
        }
    }
}
