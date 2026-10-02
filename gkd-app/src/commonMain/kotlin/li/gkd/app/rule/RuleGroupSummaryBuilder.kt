package li.gkd.app.rule

import li.gkd.app.model.AppInfo
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsAppConfig
import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.SubsGroupConfig

object RuleGroupSummaryBuilder {
    fun build(
        policy: RuleGroupPolicy,
        subscriptions: List<UsedSubsEntry>,
        appInfoById: Map<String, AppInfo>,
        appConfigs: List<SubsAppConfig>,
        groupConfigs: List<SubsGroupConfig>,
        categoryConfigs: List<SubsCategoryConfig>,
        launcherAppId: String = "",
    ): RuleGroupSummary {
        val appConfigByKey = appConfigs.associateBy { it.subsId to it.appId }
        val globalConfigByKey = groupConfigs
            .filterIsInstance<SubsGlobalGroupConfig>()
            .associateBy { it.subsId to it.groupKey }
        val appGroupConfigByKey = groupConfigs
            .filterIsInstance<SubsAppGroupConfig>()
            .associateBy { Triple(it.subsId, it.appId, it.groupKey) }
        val categoryConfigByKey = categoryConfigs.associateBy { it.subsId to it.categoryKey }
        val appsWithRules = mutableSetOf<String>()
        val appGroups = HashMap<String, List<RawSubscription.RawAppGroup>>()
        val appAllGroups = HashMap<String, List<ResolvedAppGroup>>()
        val globalGroups = mutableListOf<ResolvedGlobalGroup>()

        subscriptions.forEach { (subsItem, subscription) ->
            subscription.globalGroups.filter { group ->
                policy.getGroupEnabled(group, globalConfigByKey[subsItem.id to group.key])
            }.forEach { group ->
                val resolvedGroup = ResolvedGlobalGroup(
                    group = group,
                    subscription = subscription,
                    subsItem = subsItem,
                    config = globalConfigByKey[subsItem.id to group.key],
                )
                globalGroups.add(resolvedGroup)
            }

            subscription.apps.filter { app ->
                app.groups.isNotEmpty() &&
                        (appConfigByKey[subsItem.id to app.id]?.enable ?: (app.id in appInfoById))
            }.forEach { app ->
                val enabledGroups = mutableListOf<RawSubscription.RawAppGroup>()
                val resolvedGroups = app.groups.map { group ->
                    val config = appGroupConfigByKey[Triple(subsItem.id, app.id, group.key)]
                    val category = subscription.getCategory(group.name)
                    val categoryConfig = category?.let {
                        categoryConfigByKey[subsItem.id to it.key]
                    }
                    ResolvedAppGroup(
                        group = group,
                        subscription = subscription,
                        subsItem = subsItem,
                        config = config,
                        app = app,
                        enable = policy.getGroupEnabled(
                            group,
                            config,
                            category,
                            categoryConfig,
                        ) && group.valid,
                    )
                }
                appAllGroups[app.id] = appAllGroups[app.id].orEmpty() + resolvedGroups
                resolvedGroups.filter { it.enable }.forEach { resolvedGroup ->
                    enabledGroups.add(resolvedGroup.group)
                    if (resolvedGroup.group.rules.any {
                            RuleScopePolicy.appVersionMatches(
                                resolvedGroup.group,
                                it,
                                appInfoById[app.id]
                            )
                        }) appsWithRules.add(app.id)
                }

                if (enabledGroups.isNotEmpty()) {
                    appGroups[app.id] = appGroups[app.id].orEmpty() + enabledGroups
                }
            }
        }
        val systemAppIds = appInfoById.values.filter { it.isSystem }.mapTo(hashSetOf()) { it.id }
        val globalCounts =
            if (globalGroups.isEmpty()) emptyMap() else appInfoById.mapValues { (appId, info) ->
                globalGroups.count { resolved ->
                    policy.getGlobalGroupChecked(
                        resolved.subscription, resolved.excludeData, resolved.group,
                        appId, launcherAppId, systemAppIds, info,
                    ) == true
                }
            }
        return RuleGroupSummary(
            appIdToGlobalGroupCount = globalCounts,
            globalGroups = globalGroups,
            appsWithRules = appsWithRules,
            appIdToGroups = appGroups,
            appIdToAllGroups = appAllGroups,
        )
    }
}

data class RuleGroupSummary(
    val globalGroups: List<ResolvedGlobalGroup>,
    val appIdToGlobalGroupCount: Map<String, Int>,
    val appIdToGroups: Map<String, List<RawSubscription.RawAppGroup>>,
    val appIdToAllGroups: Map<String, List<ResolvedAppGroup>>,
    val appsWithRules: Set<String>,
) {
    val appSize get() = appsWithRules.size
    val appGroupSize get() = appIdToGroups.values.sumOf { it.size }
}
