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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.resources.*
import li.gkd.app.ui.icon.SportsBasketball
import org.jetbrains.compose.resources.stringResource

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
        @Composable
        get() = buildList {
            val prefix =
                if (enabledOnly) stringResource(Res.string.enabled) else stringResource(Res.string.total_prefix)
            globalGroups?.takeIf { it > 0 || showZeroCounts }
                ?.let { add(stringResource(Res.string.global_rule_group_stats, prefix, it)) }
            apps?.takeIf { it > 0 || showZeroCounts }
                ?.let { add(stringResource(Res.string.app_count, it)) }
            appGroups?.takeIf { it > 0 || showZeroCounts }?.let {
                add(
                    if (enabledAppGroups != null) stringResource(
                        Res.string.app_rule_group_enabled_stats,
                        enabledAppGroups,
                        it
                    )
                    else stringResource(Res.string.app_rule_group_stats, prefix, it)
                )
            }
        }.joinToString("，")
}

@Composable
fun GkRuleStats(
    stats: GkRuleStatsData,
    modifier: Modifier = Modifier,
    emptyText: String = stringResource(Res.string.rules_empty),
    triggerCount: Long = 0,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    showTooltips: Boolean = false,
) {
    val entries = buildList {
        stats.globalGroups?.takeIf { it > 0 || stats.showZeroCounts }
            ?.let { add(Triple(SportsBasketball, it.toString(), Res.string.global_rules)) }
        stats.apps?.takeIf { it > 0 || stats.showZeroCounts }
            ?.let { add(Triple(GkIcons.Android, it.toString(), Res.string.apps_title)) }
        stats.appGroups?.takeIf { it > 0 || stats.showZeroCounts }?.let {
            add(Triple(GkIcons.Rule, stats.enabledAppGroups?.let { enabled -> "$enabled/$it" }
                ?: it.toString(), Res.string.app_rules))
        }
    }
    val description = listOfNotNull(
        stats.description.takeIf { it.isNotEmpty() },
        if (triggerCount > 0) stringResource(Res.string.trigger_count_value, triggerCount.toString()) else null,
    ).joinToString("，")
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    if (!stats.hasRules && !stats.showZeroCounts && triggerCount == 0L) {
        if (emptyText.isNotEmpty()) Text(
            text = emptyText,
            modifier = modifier,
            style = textStyle,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        FlowRow(
            modifier = modifier.clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            entries.forEachIndexed { index, (icon, count, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (index > 0) {
                        Text("·", style = textStyle, color = color)
                    }
                    GkTooltipIconButtonBox(if (showTooltips) stringResource(label) else null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            GkIcon(
                                icon,
                                modifier = Modifier.size(16.dp),
                                contentDescription = null,
                                tint = color
                            )
                            Text(count, style = textStyle, color = color)
                        }
                    }
                }
            }
            if (triggerCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (entries.isNotEmpty()) {
                        Text("·", style = textStyle, color = color)
                    }
                    GkTriggerCount(triggerCount)
                }
            }
        }
    }
}
