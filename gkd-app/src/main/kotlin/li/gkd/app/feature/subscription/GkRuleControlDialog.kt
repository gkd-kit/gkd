package li.gkd.app.feature.subscription

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import li.gkd.app.data.RawSubscription
import li.gkd.app.domain.rule.RuleControlState
import li.gkd.app.domain.rule.RuleGroupPolicy
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.component.GkFullscreenDialog
import li.gkd.app.ui.component.GkGroupNameText
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.icon.ToggleMid
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.db.SubscriptionConfigSnapshot

@Composable
fun GkRuleControlDialog(
    subscription: RawSubscription,
    group: RawSubscription.RawAppGroup,
    appId: String?,
    configuration: SubscriptionConfigSnapshot,
    control: RuleControlState,
    appEnabled: Boolean,
    onDismissRequest: () -> Unit,
) {
    val category = subscription.getCategory(group.name)
    val categoryConfig = configuration.categoryConfigs.find {
        it.subsId == subscription.id && it.categoryKey == category?.key
    }
    val categoryEnabled = RuleGroupPolicy.getCategoryEnabled(category, categoryConfig)
    val subscriptionEnabled = configuration.subsItems.find { it.id == subscription.id }?.enable != false
    val appName = subscription.apps.find { it.id == appId }?.name ?: appId.orEmpty()
    val otherRestrictions = buildList {
        addAll(control.restrictions.filterNot {
            it == UiStrings.subscription_disabled || it == UiStrings.subscription_app_disabled
        })
        if (!control.canEnable) addAll(control.limitations.blockedReasons)
    }.distinct()
    val hasOtherRestrictions = otherRestrictions.isNotEmpty() || !control.canEnable
    // This view explains rule configuration, independently of app whitelists and service state.
    val ruleEnabled = subscriptionEnabled && appEnabled && control.configuredEnabled && !hasOtherRestrictions
    val categorySource = when {
        categoryConfig?.enable != null -> UiStrings.category_settings
        categoryConfig != null -> UiStrings.category_use_group_default_description
        category?.enable != null -> UiStrings.category_subscription_default
        else -> UiStrings.category_use_group_default
    }
    val steps = buildList {
        add(ControlStep(UiStrings.rule_control_subscription, switchLabel(subscriptionEnabled),
            subscription.name, stops = !subscriptionEnabled, blocked = !subscriptionEnabled,
            icon = switchIcon(subscriptionEnabled)))
        add(ControlStep(UiStrings.rule_control_app, switchLabel(appEnabled),
            appName, stops = !appEnabled, blocked = !appEnabled,
            icon = switchIcon(appEnabled), appId = appId))
        if (hasOtherRestrictions) {
            add(ControlStep(UiStrings.rule_current_restrictions, UiStrings.rule_control_restricted,
                otherRestrictions.joinToString("\n").ifEmpty { UiStrings.rule_control_restricted }, stops = true, blocked = true, icon = GkIcons.WarningAmber))
        }
        add(ControlStep(UiStrings.rule_control_own, control.setting.label,
            if (control.hasCustomSetting) UiStrings.rule_control_used else UiStrings.setting_follow_default,
            stops = control.hasCustomSetting,
            blocked = control.hasCustomSetting && !control.configuredEnabled, icon = switchIcon(control.setting.value)))
        add(ControlStep(UiStrings.rule_control_category,
            categoryEnabled?.let(::switchLabel) ?: UiStrings.category_use_group_default,
            categorySource,
            stops = categoryEnabled != null, blocked = categoryEnabled == false, icon = switchIcon(categoryEnabled)))
        add(ControlStep(UiStrings.rule_group_default, switchLabel(group.enable ?: true),
            UiStrings.rule_group_default,
            stops = true, blocked = group.enable == false, icon = switchIcon(group.enable ?: true)))
    }
    val decidingIndex = steps.indexOfFirst { it.stops }
    val result = when {
        ruleEnabled -> UiStrings.rule_control_allowed
        !subscriptionEnabled || !appEnabled || hasOtherRestrictions -> UiStrings.rule_control_restricted
        else -> UiStrings.rule_control_disabled
    }
    val reason = when {
        !subscriptionEnabled -> UiStrings.subscription_disabled
        !appEnabled -> UiStrings.subscription_app_disabled
        hasOtherRestrictions -> otherRestrictions.firstOrNull() ?: UiStrings.rule_control_restricted
        control.hasCustomSetting -> UiStrings.rule_control_own
        else -> control.defaultSource
    }
    GkFullscreenDialog(onDismissRequest) {
        Scaffold(
            topBar = {
                Column {
                    GkTopAppBar(
                        title = { Text(UiStrings.rule_control_title, maxLines = 1) },
                        actions = {
                            GkIconButton(GkIcons.Close, onClick = onDismissRequest,
                                contentDescription = UiStrings.dialog_close)
                        },
                    )
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                        GkGroupNameText(text = group.name, isGlobal = false,
                            categoryName = category?.name, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GkIcon(if (ruleEnabled) GkIcons.ToggleOn else GkIcons.ToggleOff,
                                modifier = Modifier.size(20.dp), contentDescription = null,
                                tint = if (ruleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            Text(result, modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (ruleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                        Text(reason, modifier = Modifier.padding(start = 28.dp, top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
                steps.forEachIndexed { index, step ->
                    ControlTimelineStep(
                        step = step,
                        active = index <= decidingIndex,
                        decisive = index == decidingIndex,
                        first = index == 0,
                        last = index == steps.lastIndex,
                        skippedReason = if (!subscriptionEnabled || !appEnabled || hasOtherRestrictions) {
                            UiStrings.rule_control_upstream_blocked
                        } else UiStrings.rule_control_already_decided,
                    )
                }
            }
        }
    }
}

private data class ControlStep(
    val title: String,
    val value: String,
    val reason: String,
    val stops: Boolean,
    val blocked: Boolean,
    val icon: ImageVector,
    val appId: String? = null,
)

private fun switchLabel(enabled: Boolean) = if (enabled) UiStrings.action_turn_on else UiStrings.action_close

private fun switchIcon(enabled: Boolean?): ImageVector = when (enabled) {
    true -> GkIcons.ToggleOn
    false -> GkIcons.ToggleOff
    null -> ToggleMid
}

@Composable
private fun ControlTimelineStep(
    step: ControlStep,
    active: Boolean,
    decisive: Boolean,
    first: Boolean,
    last: Boolean,
    skippedReason: String,
) {
    val color = when {
        !active -> MaterialTheme.colorScheme.outline
        decisive && step.blocked -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Canvas(Modifier.width(32.dp).fillMaxHeight()) {
            val x = 12.dp.toPx()
            val y = 24.dp.toPx()
            val radius = if (decisive) 6.dp.toPx() else 4.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
            if (!first) drawLine(if (active) color else lineColor,
                Offset(x, 0f), Offset(x, y - radius), 2.dp.toPx(), pathEffect = if (active) null else dash)
            if (!last) drawLine(if (active && !decisive) color else lineColor,
                Offset(x, y + radius), Offset(x, size.height), 2.dp.toPx(),
                pathEffect = if (active && !decisive) null else dash)
            if (active) drawCircle(color, radius, Offset(x, y))
            else drawCircle(color, radius, Offset(x, y), style = Stroke(1.5.dp.toPx()))
        }
        Surface(
            color = when {
                decisive && step.blocked -> MaterialTheme.colorScheme.errorContainer
                decisive -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.weight(1f).padding(bottom = 4.dp).semantics(mergeDescendants = true) {
                stateDescription = listOfNotNull(step.value,
                    if (decisive) UiStrings.rule_control_deciding_step else if (!active) skippedReason else null).joinToString("，")
            },
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(step.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    GkIcon(step.icon, Modifier.padding(start = 12.dp).size(24.dp),
                        tint = color, contentDescription = null)
                }
                if (step.appId != null) {
                    GkAppNameText(appId = step.appId, fallbackName = step.reason,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!active) {
                        Text(skippedReason,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text(if (!active) skippedReason else step.reason,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
