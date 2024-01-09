package li.songe.gkd.data

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
    override fun matchActivity(appId: String, activityId: String?): Boolean {
        if (appId != app.id) return false
        activityId ?: return true
        if (excludeActivityIds.any { activityId.startsWith(it) }) return false
        return activityIds.isEmpty() || activityIds.any { activityId.startsWith(it) }
    }
}
