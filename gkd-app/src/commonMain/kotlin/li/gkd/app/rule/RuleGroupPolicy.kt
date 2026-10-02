package li.gkd.app.rule


import li.gkd.app.model.AppInfo
import li.gkd.app.model.ExcludeData
import li.gkd.app.subscription.RawSubscription
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.SubsGroupConfig
import li.gkd.db.SubscriptionConfigSnapshot

enum class RuleEnableSource {
    Manual, Category, SubscriptionCategory, GroupDefault, Invalid, BuiltInAppScope, InstalledApp,
}

data class RuleEnableDecision(val enabled: Boolean, val source: RuleEnableSource)

class RuleGroupPolicy {
    private val limitationsPolicy = RuleLimitationPolicy()
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
        val limitations = limitationsPolicy.resolve(
            subscription,
            group,
            appId,
            ExcludeData.parse(config?.exclude),
            appInfo
        )
        val defaultDecision = explainGroupEnabled(group, null, category, categoryConfig)
        val globalDefault = if (inApp) getGlobalGroupChecked(
            subscription,
            ExcludeData(emptyMap(), emptySet()), group,
            checkNotNull(appId), launcherAppId, systemAppIds, appInfo
        )
        else null
        val defaultEnabled = if (inApp) globalDefault == true
        else defaultDecision.enabled
        val canEnable = group.valid && !limitations.fullyBlocked &&
                (!inApp || globalDefault != null)
        val restrictions = buildList {
            if (!group.valid) add(
                RuleRestriction.Invalid(group.validationError)
            )
            if (limitations.fullyBlocked || (group.valid && !canEnable)) {
                addAll(limitations.blockedReasons.ifEmpty { listOf(RuleRestriction.AppNotApplicable) })
            }
            if (configIndex.subscriptionEnabled(subscription.id) == false) add(
                RuleRestriction.SubscriptionDisabled
            )
            if (group is RawSubscription.RawAppGroup &&
                !(appId?.let { configIndex.appEnabled(subscription.id, it) } ?: (appInfo != null))
            ) {
                add(RuleRestriction.SubscriptionAppDisabled)
            }
            if (inApp && !getGroupEnabled(
                    group,
                    config
                )
            ) add(RuleRestriction.GlobalGroupDisabled)
        }
        return RuleControlState(
            setting = configIndex.setting(groupTarget.toSwitchTarget()),
            defaultEnabled = defaultEnabled,
            defaultSource = if (inApp) RuleEnableSource.BuiltInAppScope else defaultDecision.source,
            scope = if (inApp) RuleControlScope.CurrentApp else RuleControlScope.Group,
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
        val groupExcluded =
            appId in subscription.globalGroupAppGroupNameDisableMap[group.key].orEmpty()
        val allowed = rules.filter {
            RuleScopePolicy.globalRuleAllowed(
                group,
                it,
                appId,
                appInfo,
                groupExcluded
            )
        }
        if (allowed.isEmpty()) {
            return null
        }
        excludeData.appIds[appId]?.let { return !it }
        return allowed.any {
            RuleScopePolicy.globalDefault(
                group,
                it,
                appId,
                launcherAppId,
                systemAppIds
            )
        }
    }

}
