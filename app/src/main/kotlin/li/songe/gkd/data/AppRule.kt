package li.songe.gkd.data

import li.songe.gkd.util.ResolvedAppGroup

class AppRule(
    rule: RawSubscription.RawAppRule,
    g: ResolvedAppGroup,
    val appInfo: AppInfo?,
) : ResolvedRule(
    rule = rule,
    g = g,
) {
    val group = g.group
    val app = g.app
    val enable = appInfo?.let {
        if ((rule.excludeVersionCodes
                ?: group.excludeVersionCodes)?.contains(appInfo.versionCode) == true
        ) {
            return@let false
        }
        if ((rule.excludeVersionNames
                ?: group.excludeVersionNames)?.contains(appInfo.versionName) == true
        ) {
            return@let false
        }
        (rule.versionCodes ?: group.versionCodes)?.apply {
            return@let contains(appInfo.versionCode)
        }
        (rule.versionNames ?: group.versionNames)?.apply {
            return@let contains(appInfo.versionName)
        }

        null
    } ?: true
    val appId = app.id
    private val activityIds = getFixActivityIds(app.id, rule.activityIds ?: group.activityIds)
    private val excludeActivityIds =
        (getFixActivityIds(
            app.id,
            rule.excludeActivityIds ?: group.excludeActivityIds
        ) + (excludeData.activityIds.filter { e -> e.first == appId }
            .map { e -> e.second })).distinct()

    override val type = "app"
    override fun matchActivity(appId: String, activityId: String?): Boolean {
        if (!enable) return false
        if (appId != app.id) return false
        activityId ?: return true
        if (excludeActivityIds.any { activityId.startsWith(it) }) return false
        return activityIds.isEmpty() || activityIds.any { activityId.startsWith(it) }
    }
}
