package li.gkd.app.domain.rule

import li.gkd.app.data.AppInfo
import li.gkd.app.data.AppRule
import li.gkd.app.data.GlobalRule
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.ResolvedAppGroup
import li.gkd.app.data.ResolvedGlobalGroup
import li.gkd.app.data.subscription.UsedSubsEntry
import li.gkd.db.SubsAppConfig
import li.gkd.db.SubsAppGroupConfig
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.SubsGlobalGroupConfig
import li.gkd.db.SubsGroupConfig

object RuleSummaryBuilder {
    fun build(
        subscriptions: List<UsedSubsEntry>,
        appInfoById: Map<String, AppInfo>,
        appConfigs: List<SubsAppConfig>,
        groupConfigs: List<SubsGroupConfig>,
        categoryConfigs: List<SubsCategoryConfig>,
        launcherAppId: String = "",
    ): RuleSummary {
        val appConfigByKey = appConfigs.associateBy { it.subsId to it.appId }
        val globalConfigByKey = groupConfigs
            .filterIsInstance<SubsGlobalGroupConfig>()
            .associateBy { it.subsId to it.groupKey }
        val appGroupConfigByKey = groupConfigs
            .filterIsInstance<SubsAppGroupConfig>()
            .associateBy { Triple(it.subsId, it.appId, it.groupKey) }
        val categoryConfigByKey = categoryConfigs.associateBy { it.subsId to it.categoryKey }
        val appRules = HashMap<String, MutableList<AppRule>>()
        val appGroups = HashMap<String, List<RawSubscription.RawAppGroup>>()
        val appAllGroups = HashMap<String, List<ResolvedAppGroup>>()
        val globalRules = mutableListOf<GlobalRule>()
        val globalGroups = mutableListOf<ResolvedGlobalGroup>()

        subscriptions.forEach { (subsItem, subscription) ->
            val groupToRules = mutableMapOf<RawSubscription.RawGlobalGroup, List<GlobalRule>>()
            subscription.globalGroups.filter { group ->
                RuleGroupPolicy.getGroupEnabled(group, globalConfigByKey[subsItem.id to group.key])
            }.forEach { group ->
                val resolvedGroup = ResolvedGlobalGroup(
                    group = group,
                    subscription = subscription,
                    subsItem = subsItem,
                    config = globalConfigByKey[subsItem.id to group.key],
                )
                globalGroups.add(resolvedGroup)
                val rules = group.rules.map { rule ->
                    GlobalRule(rule, resolvedGroup, appInfoById)
                }
                groupToRules[group] = rules
                globalRules.addAll(rules)
            }
            groupToRules.values.flatten().forEach { it.bindGroupRules(groupToRules) }

            subscription.apps.filter { app ->
                app.groups.isNotEmpty() &&
                    (appConfigByKey[subsItem.id to app.id]?.enable ?: (app.id in appInfoById))
            }.forEach { app ->
                val enabledGroups = mutableListOf<RawSubscription.RawAppGroup>()
                val appGroupToRules = mutableMapOf<RawSubscription.RawAppGroup, List<AppRule>>()
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
                        enable = RuleGroupPolicy.getGroupEnabled(
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
                    val rules = resolvedGroup.group.rules.map { rule ->
                        AppRule(rule, resolvedGroup, appInfoById[app.id])
                    }.filter { it.enable }
                    appGroupToRules[resolvedGroup.group] = rules
                    if (rules.isNotEmpty()) {
                        appRules.getOrPut(app.id, ::mutableListOf).addAll(rules)
                    }
                }
                if (enabledGroups.isNotEmpty()) {
                    appGroups[app.id] = enabledGroups
                }
                appGroupToRules.values.flatten().forEach { it.bindGroupRules(appGroupToRules) }
            }
        }
        val systemAppIds = appInfoById.values.filter { it.isSystem }.mapTo(hashSetOf()) { it.id }
        val globalCounts = if (globalGroups.isEmpty()) emptyMap() else appInfoById.mapValues { (appId, info) ->
            globalGroups.count { resolved ->
                RuleGroupPolicy.getGlobalGroupChecked(
                    resolved.subscription, resolved.excludeData, resolved.group,
                    appId, launcherAppId, systemAppIds, info,
                ) == true
            }
        }
        return RuleSummary(
            appIdToGlobalGroupCount = globalCounts,
            globalRules = globalRules,
            globalGroups = globalGroups,
            appIdToRules = appRules,
            appIdToGroups = appGroups,
            appIdToAllGroups = appAllGroups,
        )
    }
}
