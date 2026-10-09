package li.gkd.app.rule


import li.gkd.app.model.AppInfo
import li.gkd.app.model.ExcludeData
import li.gkd.app.subscription.RawSubscription
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.SubsGroupConfig
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.SubscriptionConfigSnapshot

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
        val matchAnyApp = (config as? SubsGlobalGroupConfig)?.matchAnyApp
        val category = subscription.getCategory(group.name)
        val categoryConfig = configIndex.categoryConfig(subscription.id, category?.key)
        val inApp = group is RawSubscription.RawGlobalGroup && appId != null
        val limitations = limitationsPolicy.resolve(
            subscription,
            group,
            appId,
            ExcludeData.parse(config?.exclude),
            appInfo,
            matchAnyApp,
        )
        val defaultEnabledForGroup = getGroupEnabled(group, null, category, categoryConfig)
        val globalDecision = if (group is RawSubscription.RawGlobalGroup && appId != null) {
            globalAppDecision(subscription, group, appId, launcherAppId, systemAppIds, appInfo, matchAnyApp)
        } else null
        val defaultEnabled = globalDecision?.defaultEnabled ?: defaultEnabledForGroup
        val canEnable = group.valid && (globalDecision?.versionAllowed ?: !limitations.fullyBlocked)
        val restrictions = buildList {
            if (!group.valid) add(
                RuleRestriction.Invalid(group.validationError)
            )
            if (group.valid && !canEnable) {
                // App defaults can be overridden; explain the remaining hard restriction.
                if (inApp) add(RuleRestriction.VersionMismatch)
                else addAll(limitations.blockedReasons.ifEmpty { listOf(RuleRestriction.AppNotApplicable) })
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
            scope = if (inApp) RuleControlScope.CurrentApp else RuleControlScope.Group,
            restrictions = restrictions,
            canEnable = canEnable,
            limitations = limitations,
            blockedApp = blockedApp,
            defaultOffReasons = globalDecision?.offReasons.orEmpty().takeIf { canEnable }.orEmpty(),
        )
    }

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
        matchAnyApp: Boolean? = null,
    ): Boolean? {
        val decision = globalAppDecision(
            subscription, group, appId, launcherAppId, systemAppIds, appInfo, matchAnyApp,
        )
        if (!decision.versionAllowed) return null
        return excludeData.appIds[appId]?.not() ?: decision.defaultEnabled
    }

    private fun globalAppDecision(
        subscription: RawSubscription,
        group: RawSubscription.RawGlobalGroup,
        appId: String,
        launcherAppId: String,
        systemAppIds: Set<String>,
        appInfo: AppInfo?,
        matchAnyApp: Boolean?,
    ): GlobalAppDecision {
        val versionAllowed = group.rules.ifEmpty { listOf(null) }.any {
            RuleScopePolicy.globalRuleRestriction(group, it, appId, appInfo, false, true) == null
        }
        val offReasons = if (!versionAllowed) emptyList() else buildList {
            if (RuleScopePolicy.globalAppDisabled(group, appId)) add(GlobalAppDefaultOffReason.SubscriptionApp)
            if (appId in subscription.globalGroupAppGroupNameDisableMap[group.key].orEmpty()) {
                add(GlobalAppDefaultOffReason.SameNameAppGroup)
            }
            if (isEmpty()) {
                RuleScopePolicy.globalDefaultOffReason(
                    group, appId, launcherAppId, systemAppIds, matchAnyApp,
                )?.let { add(it) }
            }
        }
        return GlobalAppDecision(versionAllowed, offReasons)
    }
}

private data class GlobalAppDecision(
    val versionAllowed: Boolean,
    val offReasons: List<GlobalAppDefaultOffReason>,
) {
    val defaultEnabled: Boolean get() = versionAllowed && offReasons.isEmpty()
}
