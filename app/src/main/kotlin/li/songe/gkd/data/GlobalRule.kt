package li.songe.gkd.data

import li.songe.gkd.service.TopActivity
import li.songe.selector.Selector

data class GlobalApp(
    val id: String,
    val enable: Boolean,
    val activityIds: List<String>,
    val excludeActivityIds: List<String>,
)

class GlobalRule(
    matches: List<Selector>,
    excludeMatches: List<Selector>,
    actionDelay: Long,
    quickFind: Boolean,
    matchDelay: Long,
    matchTime: Long?,
    resetMatch: String?,
    key: Int?,
    preKeys: Set<Int>,
    index: Int,
    subsItem: SubsItem,
    rule: RawSubscription.RawGlobalRule,
    group: RawSubscription.RawGlobalGroup,
    rawSubs: RawSubscription,
    val apps: Map<String, GlobalApp>,
    val matchAnyApp: Boolean,
) : ResolvedRule(
    matches = matches,
    excludeMatches = excludeMatches,
    actionDelay = actionDelay,
    quickFind = quickFind,
    matchDelay = matchDelay,
    matchTime = matchTime,
    resetMatch = resetMatch,
    key = key,
    preKeys = preKeys,
    index = index,
    rule = rule,
    group = group,
    subsItem = subsItem,
    rawSubs = rawSubs,
) {

    override val type = "global"

    private val excludeAppIds = apps.filter { e -> !e.value.enable }.keys
    override fun matchActivity(topActivity: TopActivity?): Boolean {
        topActivity ?: return false
        if (excludeAppIds.contains(topActivity.appId)) {
            return false
        }
        val app = apps[topActivity.appId] ?: return matchAnyApp
        topActivity.activityId ?: return true
        if (app.excludeActivityIds.any { e -> e.startsWith(topActivity.activityId) }) {
            return false
        }
        return app.activityIds.isEmpty() || app.activityIds.any { e -> e.startsWith(topActivity.activityId) }
    }

}
