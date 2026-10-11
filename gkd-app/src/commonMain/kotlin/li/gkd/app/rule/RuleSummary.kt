package li.gkd.app.rule

import li.gkd.app.resources.*
import li.gkd.app.state.Loadable
import li.gkd.app.subscription.RawSubscription

data class RuleSummary(
    val globalRules: List<GlobalRule> = emptyList(),
    val globalGroups: List<ResolvedGlobalGroup> = emptyList(),
    val appIdToGlobalGroupCount: Map<String, Int> = emptyMap(),
    val appIdToRules: Map<String, List<AppRule>> = emptyMap(),
    val appIdToGroups: Map<String, List<RawSubscription.RawAppGroup>> = emptyMap(),
    val appIdToAllGroups: Map<String, List<ResolvedAppGroup>> = emptyMap(),
) {
    val appSize = appIdToRules.keys.size
    val appGroupSize = appIdToGroups.values.sumOf { it.size }

    fun statusText(actionCount: Long): String {
        val rules = listOfNotNull(
            if (globalGroups.isNotEmpty()) Res.string.subscription_global_count.getSync(globalGroups.size) else null,
            if (appGroupSize > 0) Res.string.subscription_app_rule_counts.getSync(appSize, appGroupSize) else null,
        ).joinToString("/").ifEmpty { Res.string.rules_empty.getSync() }
        return if (actionCount > 0) Res.string.action_count_summary.getSync(rules, actionCount) else rules
    }

    companion object {
        fun statusText(state: Loadable<RuleSummary>, actionCount: Long): String = when (state) {
            Loadable.Loading -> Res.string.config_loading.getSync()
            is Loadable.Failure -> Res.string.data_load_failed.getSync()
            is Loadable.Ready -> state.value.statusText(actionCount)
        }
    }
}
