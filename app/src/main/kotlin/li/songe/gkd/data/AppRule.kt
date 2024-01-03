package li.songe.gkd.data

import li.songe.gkd.service.TopActivity

class AppRule(
    rule: RawSubscription.RawAppRule,
    subsItem: SubsItem,
    group: RawSubscription.RawAppGroup,
    rawSubs: RawSubscription,
    exclude: String,
    val app: RawSubscription.RawApp,
) : ResolvedRule(
    rule = rule,
    group = group,
    subsItem = subsItem,
    rawSubs = rawSubs,
    exclude = exclude,
) {
    val appId = app.id
    val activityIds = getFixActivityIds(app.id, rule.activityIds ?: group.activityIds)
    val excludeActivityIds =
        getFixActivityIds(
            app.id,
            rule.excludeActivityIds ?: group.excludeActivityIds
        ) + (excludeData.activityMap[app.id] ?: emptyList())

    override val type = "app"
    override fun matchActivity(topActivity: TopActivity): Boolean {
        topActivity.activityId ?: return true
        if (excludeActivityIds.any { topActivity.activityId.startsWith(it) }) return false
        return activityIds.isEmpty() || activityIds.any { topActivity.activityId.startsWith(it) }
    }
}
