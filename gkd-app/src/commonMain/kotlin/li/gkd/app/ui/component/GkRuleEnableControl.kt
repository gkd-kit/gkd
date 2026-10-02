package li.gkd.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import li.gkd.app.model.AppInfo
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_close
import li.gkd.app.resources.action_got_it
import li.gkd.app.resources.action_turn_on
import li.gkd.app.resources.rule_close
import li.gkd.app.resources.rule_now_available
import li.gkd.app.resources.rule_status
import li.gkd.app.resources.rule_switch
import li.gkd.app.resources.rule_temporarily_unavailable_prefix
import li.gkd.app.resources.rule_unavailable
import li.gkd.app.resources.rule_unavailable_reason_action_suffix
import li.gkd.app.resources.rule_unavailable_reason_view
import li.gkd.app.resources.setting_follow_default_value
import li.gkd.app.rule.RuleConfigIndex
import li.gkd.app.rule.RuleControlScope
import li.gkd.app.rule.RuleControlState
import li.gkd.app.rule.RuleEnableSource
import li.gkd.app.rule.RuleGroupConfigService
import li.gkd.app.rule.RuleRestriction
import li.gkd.app.rule.RuleSetting
import li.gkd.app.rule.RuleSwitchTarget
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.ui.subscription.label
import li.gkd.db.SubscriptionConfigSnapshot
import org.jetbrains.compose.resources.stringResource

data class RuleControlEnvironment(
    val launcherAppId: String,
    val apps: Map<String, AppInfo>,
    val systemApps: Set<String>,
    val blockedApps: Set<String>,
) {
    fun resolve(
        subscription: RawSubscription, group: RawSubscription.RawGroupProps,
        appId: String?, configs: SubscriptionConfigSnapshot,
        configIndex: RuleConfigIndex = RuleConfigIndex(configs)
    ): RuleControlState =
        RuleGroupConfigService.policy.controlState(
            subscription, group, appId, configs, apps[appId],
            launcherAppId, systemApps, appId in blockedApps, configIndex
        )

    fun app(
        subsId: Long, appId: String, configs: SubscriptionConfigSnapshot,
        configIndex: RuleConfigIndex = RuleConfigIndex(configs)
    ): RuleControlState = RuleControlState(
        setting = configIndex.setting(RuleSwitchTarget.App(subsId, appId)),
        defaultEnabled = appId in apps,
        defaultSource = RuleEnableSource.InstalledApp,
        scope = RuleControlScope.SubscriptionApp,
        blockedApp = appId in blockedApps,
        restrictions = buildList {
            if (configIndex.subscriptionEnabled(subsId) == false) add(RuleRestriction.SubscriptionDisabled)
        },
    )
}

@Composable
fun GkRuleEnableControl(
    state: RuleControlState,
    onSettingChange: (RuleSetting) -> Unit,
    modifier: Modifier = Modifier,
    identity: RuleSwitchTarget? = null,
    showCustomSettingIcon: Boolean = true,
) = key(identity) {
    // Lazy layouts can reuse M3's thumb node with its previous animation state.
    // Reset only when its business target changes, never when checked changes.
    var showReason by remember { mutableStateOf(false) }
    val reasonAction = if (state.canEnable) {
        Modifier
    } else {
        Modifier.minimumInteractiveComponentSize().clickable(
            interactionSource = null,
            indication = null,
            role = Role.Button,
            onClickLabel = stringResource(Res.string.rule_unavailable_reason_view),
        ) { showReason = true }
    }
    // M3 keeps its track centered at its native size inside this expanded touch target.
    // Its own interaction source draws press feedback on the thumb, not the whole target.
    val switchLabel = stringResource(Res.string.rule_switch)
    val configuredLabel = if (state.setting == RuleSetting.FollowDefault) {
        stringResource(
            Res.string.setting_follow_default_value,
            stringResource(if (state.defaultEnabled) Res.string.action_turn_on else Res.string.action_close)
        )
    } else state.setting.label
    val description = configuredLabel + when {
        !state.canEnable -> stringResource(Res.string.rule_unavailable_reason_action_suffix)
        state.configuredEnabled && state.restrictions.isNotEmpty() ->
            stringResource(Res.string.rule_temporarily_unavailable_prefix) + state.restrictions.map { it.label }.joinToString(
                "，"
            )

        else -> ""
    }
    Switch(
        modifier = modifier.then(reasonAction).semantics {
            contentDescription = switchLabel
            stateDescription = description
        },
        checked = state.configuredEnabled,
        onCheckedChange = if (state.canEnable) {
            { onSettingChange(RuleSetting.from(it)) }
        } else null,
        enabled = state.canEnable,
        thumbContent = {
            // Keep the thumb size consistent even when this surface hides its icon.
            Box(Modifier.size(SwitchDefaults.IconSize)) {
                if (showCustomSettingIcon && state.hasCustomSetting) {
                    GkRulePropertyIcon(
                        RuleProperty.CustomSetting,
                        modifier = Modifier.size(SwitchDefaults.IconSize), contentDescription = null
                    )
                }
            }
        },
    )
    if (showReason) {
        GkAlertDialog(
            onDismissRequest = { showReason = false },
            title = {
                Text(
                    if (state.canEnable) stringResource(Res.string.rule_status) else stringResource(
                        Res.string.rule_unavailable
                    )
                )
            },
            text = {
                Text(
                    RulePropertyText.restrictionSummary(state)
                        ?: stringResource(Res.string.rule_now_available)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showReason = false
                }) { Text(stringResource(Res.string.action_got_it)) }
            },
            dismissButton = {
                if (state.configuredEnabled) {
                    TextButton(onClick = {
                        showReason = false
                        onSettingChange(RuleSetting.Disabled)
                    }) { Text(stringResource(Res.string.rule_close)) }
                }
            },
        )
    }
}
