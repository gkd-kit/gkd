package li.gkd.app.rule

import li.gkd.app.model.AppInfo
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsAppConfig
import li.gkd.db.SubsCategoryConfig
import li.gkd.db.SubsGroupConfig

object RuleSummaryBuilder {
    fun build(
        policy: RuleGroupPolicy,
        runtime: RuleRuntime,
        subscriptions: List<UsedSubsEntry>, appInfoById: Map<String, AppInfo>,
        appConfigs: List<SubsAppConfig>, groupConfigs: List<SubsGroupConfig>,
        categoryConfigs: List<SubsCategoryConfig>, launcherAppId: String = "",
    ): RuleSummary {
        val groups = RuleGroupSummaryBuilder.build(
            policy,
            subscriptions, appInfoById, appConfigs, groupConfigs, categoryConfigs, launcherAppId
        )
        return build(groups, appInfoById, runtime)
    }

    fun build(
        groups: RuleGroupSummary,
        appInfoById: Map<String, AppInfo>,
        runtime: RuleRuntime,
    ): RuleSummary {
        val globalRules =
            groups.globalGroups.groupBy { it.subscription }.values.flatMap { entries ->
                val byGroup = entries.associate { resolved ->
                    resolved.group to resolved.group.rules.map {
                        GlobalRule(
                            it,
                            resolved,
                            appInfoById, runtime
                        )
                    }
                }
                byGroup.values.flatten().onEach { it.bindGroupRules(byGroup) }
            }
        val appRules = groups.appIdToAllGroups.mapValues { (id, entries) ->
            entries.filter { it.enable }.groupBy { it.subscription }.values.flatMap { resolved ->
                val byGroup = resolved.associate { group ->
                    group.group to group.group.rules.map {
                        AppRule(
                            it,
                            group,
                            appInfoById[id], runtime
                        )
                    }.filter { it.enable }
                }
                byGroup.values.flatten().onEach { it.bindGroupRules(byGroup) }
            }
        }.filterValues { it.isNotEmpty() }
        return RuleSummary(
            globalRules, groups.globalGroups, groups.appIdToGlobalGroupCount,
            appRules, groups.appIdToGroups, groups.appIdToAllGroups
        )
    }
}
