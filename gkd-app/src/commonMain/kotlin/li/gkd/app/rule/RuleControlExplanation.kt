package li.gkd.app.rule

import li.gkd.app.subscription.RawSubscription
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.SubscriptionConfigSnapshot

enum class RuleControlStepKind {
    Subscription, App, Restrictions, Own, Category, GroupDefault,
    GlobalGroup, GlobalApp, DefaultApps, GlobalDefault,
}

enum class RuleControlNote {
    FollowDefault, ManualDisabled, FollowSubscription, CategorySettings, IgnoreCategory, FollowGroup,
}

data class RuleControlStep(
    val kind: RuleControlStepKind,
    val enabled: Boolean?,
    val stops: Boolean,
    val note: RuleControlNote? = null,
    val restrictions: List<RuleRestriction> = emptyList(),
    val defaultOffReasons: List<GlobalAppDefaultOffReason> = emptyList(),
)

data class RuleControlExplanation(val steps: List<RuleControlStep>, val inApp: Boolean) {
    val decidingIndex: Int = steps.indexOfFirst { it.stops }
    val enabled: Boolean = steps[decidingIndex].enabled == true
    val restricted: Boolean = !enabled && when (steps[decidingIndex].kind) {
        RuleControlStepKind.Subscription, RuleControlStepKind.App, RuleControlStepKind.Restrictions -> true
        RuleControlStepKind.GlobalGroup -> inApp
        else -> false
    }
}

/** Explains configuration only; app whitelists and service availability are separate. */
fun RuleGroupPolicy.explainControl(
    subscription: RawSubscription,
    group: RawSubscription.RawGroupProps,
    appId: String?,
    configuration: SubscriptionConfigSnapshot,
    control: RuleControlState,
): RuleControlExplanation {
    val global = group is RawSubscription.RawGlobalGroup
    val inApp = global && appId != null
    val index = RuleConfigIndex(configuration)
    fun settingNote(setting: RuleSetting) = when (setting) {
        RuleSetting.FollowDefault -> RuleControlNote.FollowDefault
        RuleSetting.Disabled -> RuleControlNote.ManualDisabled
        RuleSetting.Enabled -> null
    }
    val steps = buildList {
        val subscriptionEnabled = index.subscriptionEnabled(subscription.id) != false
        add(RuleControlStep(RuleControlStepKind.Subscription, subscriptionEnabled, !subscriptionEnabled))
        if (!global) {
            val appEnabled = RuleRestriction.SubscriptionAppDisabled !in control.restrictions
            add(RuleControlStep(RuleControlStepKind.App, appEnabled, !appEnabled))
        }
        val config = index.groupConfig(group.toRuleGroupTarget(subscription.id, appId))
        if (inApp) {
            val groupSetting = RuleSetting.from(config?.enable)
            val enabled = groupSetting.value ?: getGroupEnabled(group, null)
            add(RuleControlStep(
                RuleControlStepKind.GlobalGroup, enabled, !enabled,
                if (groupSetting == RuleSetting.FollowDefault) RuleControlNote.FollowSubscription
                else settingNote(groupSetting),
            ))
        }
        val restrictions = buildList {
            addAll(control.restrictions.filterNot {
                it == RuleRestriction.SubscriptionDisabled ||
                        it == RuleRestriction.SubscriptionAppDisabled ||
                        (inApp && it == RuleRestriction.GlobalGroupDisabled)
            })
            if (!global && !control.canEnable) addAll(control.limitations.blockedReasons)
        }.distinct()
        if (restrictions.isNotEmpty() || (!global && !control.canEnable)) {
            add(RuleControlStep(RuleControlStepKind.Restrictions, false, true, restrictions = restrictions))
        }
        add(RuleControlStep(
            if (!global) RuleControlStepKind.Own else if (inApp) RuleControlStepKind.GlobalApp
            else RuleControlStepKind.GlobalGroup,
            control.setting.value, control.hasCustomSetting, settingNote(control.setting),
        ))
        if (group is RawSubscription.RawGlobalGroup) {
            if (inApp) {
                val localDefault = (config as? SubsGlobalGroupConfig)?.matchAnyApp
                add(RuleControlStep(
                    RuleControlStepKind.DefaultApps, localDefault ?: group.matchAnyApp, false,
                    if (localDefault == null) RuleControlNote.FollowSubscription else null,
                ))
            }
            add(RuleControlStep(
                if (inApp) RuleControlStepKind.GlobalDefault else RuleControlStepKind.GroupDefault,
                control.defaultEnabled, true, defaultOffReasons = control.defaultOffReasons,
            ))
        } else {
            val category = subscription.getCategory(group.name)
            val categoryConfig = index.categoryConfig(subscription.id, category?.key)
            val categoryEnabled = getCategoryEnabled(category, categoryConfig)
            add(RuleControlStep(
                RuleControlStepKind.Category, categoryEnabled, categoryEnabled != null,
                when {
                    categoryConfig?.enable == true -> null
                    categoryConfig?.enable == false -> RuleControlNote.CategorySettings
                    categoryConfig != null -> RuleControlNote.IgnoreCategory
                    category?.enable != null -> RuleControlNote.FollowSubscription
                    else -> RuleControlNote.FollowGroup
                },
            ))
            add(RuleControlStep(RuleControlStepKind.GroupDefault, group.enable ?: true, true))
        }
    }
    return RuleControlExplanation(steps, inApp)
}
