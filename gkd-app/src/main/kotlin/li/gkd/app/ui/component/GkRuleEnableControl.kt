package li.gkd.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.text.UiStrings
import li.gkd.app.a11y.launcherAppId
import li.gkd.app.data.AppInfo
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.app.domain.rule.RuleControlState
import li.gkd.app.domain.rule.RuleConfigIndex
import li.gkd.app.domain.rule.RuleGroupPolicy
import li.gkd.app.domain.rule.RuleSetting
import li.gkd.app.domain.rule.RuleSwitchTarget
import li.gkd.app.store.AppStore.blockMatchAppListFlow
import li.gkd.db.SubscriptionConfigSnapshot

data class RuleControlEnvironment(
    val apps: Map<String, AppInfo>,
    val systemApps: Set<String>,
    val blockedApps: Set<String>,
) {
    fun resolve(subscription: RawSubscription, group: RawSubscription.RawGroupProps,
                appId: String?, configs: SubscriptionConfigSnapshot,
                configIndex: RuleConfigIndex = RuleConfigIndex(configs)): RuleControlState =
        RuleGroupPolicy.controlState(subscription, group, appId, configs, apps[appId],
            launcherAppId, systemApps, appId in blockedApps, configIndex)

    fun app(subsId: Long, appId: String, configs: SubscriptionConfigSnapshot,
            configIndex: RuleConfigIndex = RuleConfigIndex(configs)): RuleControlState = RuleControlState(
        setting = configIndex.setting(RuleSwitchTarget.App(subsId, appId)),
        defaultEnabled = appId in apps,
        defaultSource = UiStrings.installed_apps_default_enabled,
        scope = UiStrings.subscription_app_switch_scope,
        blockedApp = appId in blockedApps,
        restrictions = buildList {
            if (configIndex.subscriptionEnabled(subsId) == false) add(UiStrings.subscription_disabled)
        },
    )
}

@Composable
fun rememberRuleControlEnvironment(): RuleControlEnvironment {
    val apps by AppInfoRepository.appInfoMapFlow.collectAsStateWithLifecycle()
    val systems by AppInfoRepository.systemAppsFlow.collectAsStateWithLifecycle()
    val blocked by blockMatchAppListFlow.collectAsStateWithLifecycle()
    return remember(apps, systems, blocked) { RuleControlEnvironment(apps, systems, blocked.toSet()) }
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
            onClickLabel = UiStrings.rule_unavailable_reason_view,
        ) { showReason = true }
    }
    // M3 keeps its track centered at its native size inside this expanded touch target.
    // Its own interaction source draws press feedback on the thumb, not the whole target.
    Switch(
        modifier = modifier.then(reasonAction).semantics {
            contentDescription = UiStrings.rule_switch
            stateDescription = buildString {
                append(state.label)
                if (!state.canEnable) {
                    append(UiStrings.rule_unavailable_reason_action_suffix)
                } else if (state.configuredEnabled && state.restrictions.isNotEmpty()) {
                    append(UiStrings.rule_temporarily_unavailable_prefix)
                    append(state.restrictions.joinToString("，"))
                }
            }
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
                    GkRulePropertyIcon(RuleProperty.CustomSetting,
                        modifier = Modifier.size(SwitchDefaults.IconSize), contentDescription = null)
                }
            }
        },
    )
    if (showReason) {
        GkAlertDialog(
            onDismissRequest = { showReason = false },
            title = { Text(if (state.canEnable) UiStrings.rule_status else UiStrings.rule_unavailable) },
            text = { Text(RulePropertyText.restrictionSummary(state) ?: UiStrings.rule_now_available) },
            confirmButton = {
                TextButton(onClick = { showReason = false }) { Text(UiStrings.action_got_it) }
            },
            dismissButton = {
                if (state.configuredEnabled) {
                    TextButton(onClick = {
                        showReason = false
                        onSettingChange(RuleSetting.Disabled)
                    }) { Text(UiStrings.rule_close) }
                }
            },
        )
    }
}
