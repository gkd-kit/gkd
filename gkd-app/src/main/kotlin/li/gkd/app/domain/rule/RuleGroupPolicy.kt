package li.gkd.app.domain.rule

import li.gkd.app.text.UiStrings
import li.gkd.app.data.AppInfo
import li.gkd.app.data.ExcludeData
import li.gkd.app.data.RawSubscription
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.SubsGroupConfig
import li.gkd.db.SubscriptionConfigSnapshot

enum class RuleEnableSource(val label: String) {
    Manual(UiStrings.rule_custom_setting),
    Category(UiStrings.category_settings),
    SubscriptionCategory(UiStrings.category_subscription_default),
    GroupDefault(UiStrings.rule_group_default),
    Invalid(UiStrings.rule_invalid),
}

data class RuleEnableDecision(val enabled: Boolean, val source: RuleEnableSource)

object RuleGroupPolicy {
    fun controlState(
        subscription: RawSubscription,
        group: RawSubscription.RawGroupProps,
        appId: String?,
        configs: SubscriptionConfigSnapshot,
        appInfo: AppInfo?,
        launcherAppId: String,
        systemAppIds: Set<String>,
        blockedApp: Boolean = false,
        configIndex: RuleConfigIndex = RuleConfigIndex(configs),
    ): RuleControlState {
        val groupTarget = group.toRuleGroupTarget(subscription.id, appId)
        val config = configIndex.groupConfig(groupTarget)
        val category = subscription.getCategory(group.name)
        val categoryConfig = configIndex.categoryConfig(subscription.id, category?.key)
        val inApp = group is RawSubscription.RawGlobalGroup && appId != null
        val limitations = RuleLimitationPolicy.resolve(subscription, group, appId, ExcludeData.parse(config?.exclude), appInfo)
        val defaultDecision = explainGroupEnabled(group, null, category, categoryConfig)
        val globalDefault = if (inApp) getGlobalGroupChecked(subscription,
            ExcludeData(emptyMap(), emptySet()), group,
            checkNotNull(appId), launcherAppId, systemAppIds, appInfo)
        else null
        val defaultEnabled = if (inApp) globalDefault == true
        else defaultDecision.enabled
        val canEnable = group.valid && !limitations.fullyBlocked &&
            (!inApp || globalDefault != null)
        val restrictions = buildList {
            if (!group.valid) add(group.errorDesc ?: UiStrings.rule_invalid_cannot_enable)
            if (limitations.fullyBlocked || (group.valid && !canEnable)) {
                addAll(limitations.blockedReasons.ifEmpty { listOf(UiStrings.rule_app_not_applicable) })
            }
            if (configIndex.subscriptionEnabled(subscription.id) == false) add(UiStrings.subscription_disabled)
            if (group is RawSubscription.RawAppGroup &&
                !(appId?.let { configIndex.appEnabled(subscription.id, it) } ?: (appInfo != null))) {
                add(UiStrings.subscription_app_disabled)
            }
            if (inApp && !getGroupEnabled(group, config)) add(UiStrings.global_rule_group_disabled)
        }
        return RuleControlState(
            setting = configIndex.setting(groupTarget.toSwitchTarget()),
            defaultEnabled = defaultEnabled,
            defaultSource = if (inApp) UiStrings.rule_builtin_app_scope else defaultDecision.source.label,
            scope = if (inApp) UiStrings.rule_current_app_scope else UiStrings.rule_group_scope,
            restrictions = restrictions,
            canEnable = canEnable,
            limitations = limitations,
            blockedApp = blockedApp,
        )
    }

    fun explainGroupEnabled(
        group: RawSubscription.RawGroupProps,
        subsConfig: SubsGroupConfig?,
        category: RawSubscription.RawCategory? = null,
        categoryConfig: SubsCategoryConfig? = null,
    ): RuleEnableDecision = RuleEnableDecision(
        enabled = getGroupEnabled(group, subsConfig, category, categoryConfig),
        source = when {
            !group.valid -> RuleEnableSource.Invalid
            subsConfig?.enable != null -> RuleEnableSource.Manual
            group is RawSubscription.RawGlobalGroup -> RuleEnableSource.GroupDefault
            categoryConfig?.enable != null -> RuleEnableSource.Category
            categoryConfig == null && category?.enable != null -> RuleEnableSource.SubscriptionCategory
            else -> RuleEnableSource.GroupDefault
        },
    )

    fun getCategoryEnabled(
        category: RawSubscription.RawCategory?,
        categoryConfig: SubsCategoryConfig?,
    ): Boolean? = if (categoryConfig != null) {
        // 已保存的 null 表示使用各规则组默认值，而不是类别默认值。
        categoryConfig.enable
    } else {
        category?.enable
    }

    fun getGroupEnabled(
        group: RawSubscription.RawGroupProps,
        subsConfig: SubsGroupConfig?,
        category: RawSubscription.RawCategory? = null,
        categoryConfig: SubsCategoryConfig? = null,
    ): Boolean = group.valid && when (group) {
        is RawSubscription.RawAppGroup -> subsConfig?.enable
            ?: getCategoryEnabled(category, categoryConfig)
            ?: group.enable
            ?: true

        is RawSubscription.RawGlobalGroup -> subsConfig?.enable ?: group.enable ?: true
    }

    fun getGlobalGroupChecked(
        subscription: RawSubscription,
        excludeData: ExcludeData,
        group: RawSubscription.RawGlobalGroup,
        appId: String,
        launcherAppId: String,
        systemAppIds: Set<String>,
        appInfo: AppInfo? = null,
    ): Boolean? {
        val rules = group.rules.ifEmpty { listOf(null) }
        val groupExcluded = appId in subscription.globalGroupAppGroupNameDisableMap[group.key].orEmpty()
        val allowed = rules.filter { RuleScopePolicy.globalRuleAllowed(group, it, appId, appInfo, groupExcluded) }
        if (allowed.isEmpty()) {
            return null
        }
        excludeData.appIds[appId]?.let { return !it }
        return allowed.any { RuleScopePolicy.globalDefault(group, it, appId, launcherAppId, systemAppIds) }
    }

}
