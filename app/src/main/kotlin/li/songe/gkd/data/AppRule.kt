package li.songe.gkd.data

import li.songe.gkd.service.TopActivity
import li.songe.selector.Selector

class AppRule(
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
    rule: RawSubscription.RawAppRule,
    subsItem: SubsItem,
    group: RawSubscription.RawAppGroup,
    rawSubs: RawSubscription,
    val appId: String,
    val activityIds: List<String>,
    val excludeActivityIds: List<String>,
    val app: RawSubscription.RawApp,
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
    override fun matchActivity(topActivity: TopActivity?): Boolean {
        topActivity ?: return false
        topActivity.activityId ?: return true
        if (excludeActivityIds.any { topActivity.activityId.startsWith(it) }) return false
        return activityIds.isEmpty() || activityIds.any { topActivity.activityId.startsWith(it) }
    }
}
