package li.gkd.app.domain.rule

import li.gkd.app.data.AppRule
import li.gkd.app.data.GlobalRule
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.ResolvedAppGroup
import li.gkd.app.data.ResolvedGlobalGroup

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
}
