package li.songe.gkd.data

import li.songe.gkd.service.TopActivity

class AppRule(
    rule: RawSubscription.RawAppRule,
    subsItem: SubsItem,
    group: RawSubscription.RawAppGroup,
    rawSubs: RawSubscription,
    val app: RawSubscription.RawApp,
) : ResolvedRule(
    rule = rule,
    group = group,
    subsItem = subsItem,
    rawSubs = rawSubs,
) {
    val appId = app.id
    val activityIds = getFixActivityIds(app.id, rule.activityIds ?: group.activityIds)
    val excludeActivityIds =
        getFixActivityIds(app.id, rule.excludeActivityIds ?: group.excludeActivityIds)

    override val type = "app"
    override fun matchActivity(topActivity: TopActivity?): Boolean {
        topActivity ?: return false
        topActivity.activityId ?: return true
        if (excludeActivityIds.any { topActivity.activityId.startsWith(it) }) return false
        return activityIds.isEmpty() || activityIds.any { topActivity.activityId.startsWith(it) }
    }
}
