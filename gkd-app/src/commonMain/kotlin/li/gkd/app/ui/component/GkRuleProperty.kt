package li.gkd.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.model.ExcludeData
import li.gkd.app.resources.Res
import li.gkd.app.resources.app_count
import li.gkd.app.resources.not_configured
import li.gkd.app.resources.page_count
import li.gkd.app.resources.rule_app_or_version_mismatch
import li.gkd.app.resources.rule_current_restrictions
import li.gkd.app.resources.rule_partially_inapplicable_prefix
import li.gkd.app.resources.rule_personal_switch_setting
import li.gkd.app.resources.rule_scope_and_exclusions
import li.gkd.app.resources.rule_temporarily_unavailable
import li.gkd.app.rule.RuleControlState
import li.gkd.app.ui.icon.ResetSettings
import li.gkd.app.ui.subscription.label
import org.jetbrains.compose.resources.stringResource

// Display labels live here; classification never depends on their language.
enum class RuleProperty {
    CustomSetting, Personal, Restricted;

    val label: String
        @Composable get() = when (this) {
            CustomSetting -> stringResource(Res.string.rule_personal_switch_setting)
            Personal -> stringResource(Res.string.rule_scope_and_exclusions)
            Restricted -> stringResource(Res.string.rule_current_restrictions)
        }

    val icon: ImageVector
        get() = when (this) {
            CustomSetting -> ResetSettings
            Personal -> Icons.Outlined.FilterAltOff
            Restricted -> GkIcons.WarningAmber
        }

}

@Composable
fun GkRulePropertyIcon(
    property: RuleProperty,
    modifier: Modifier = Modifier.size(16.dp),
    contentDescription: String? = property.label,
    tint: Color = LocalContentColor.current,
) {
    GkIcon(property.icon, contentDescription = contentDescription, modifier = modifier, tint = tint)
}

@Composable
fun GkRuleSupportingContent(
    state: RuleControlState,
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        GkRulePropertyIndicators(state)
    }
}

@Composable
fun GkRulePropertyIndicators(state: RuleControlState) {
    // Keep the personal property's slot stable without reserving space for warnings.
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        if (state.limitations.hasPersonalProperties) {
            GkRulePropertyIcon(
                RuleProperty.Personal,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

object RulePropertyText {

    @Composable
    fun restrictionSummary(state: RuleControlState): String? = buildList {
        addAll(state.restrictions.map { it.label })
        if (state.limitations.blockedRules > 0 && !state.limitations.fullyBlocked) {
            add(
                stringResource(Res.string.rule_partially_inapplicable_prefix) + state.limitations.blockedReasons.map { it.label }
                    .joinToString("\n")
                    .ifEmpty { stringResource(Res.string.rule_app_or_version_mismatch) })
        }
        if (!state.canEnable && isEmpty()) add(stringResource(Res.string.rule_temporarily_unavailable))
    }.distinct().joinToString("\n").takeIf { it.isNotEmpty() }

    @Composable
    fun personalSummary(exclude: ExcludeData, appId: String?): String? {
        val pages = exclude.activityIds.count { appId == null || it.first == appId }
        val apps = if (appId == null) exclude.appIds.size else 0
        return buildList {
            if (apps > 0) add(stringResource(Res.string.app_count, apps))
            if (pages > 0) add(stringResource(Res.string.page_count, pages))
        }.joinToString(" · ").takeIf { it.isNotEmpty() }
            ?: if (appId == null) stringResource(Res.string.not_configured) else null
    }
}
