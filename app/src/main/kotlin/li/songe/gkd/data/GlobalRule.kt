package li.songe.gkd.data

import li.songe.gkd.service.launcherAppId

data class GlobalApp(
    val id: String,
    val enable: Boolean,
    val activityIds: List<String>,
    val excludeActivityIds: List<String>,
)

class GlobalRule(
    subsItem: SubsItem,
    rule: RawSubscription.RawGlobalRule,
    group: RawSubscription.RawGlobalGroup,
    rawSubs: RawSubscription,
    exclude: String,
) : ResolvedRule(
    rule = rule,
    group = group,
    subsItem = subsItem,
    rawSubs = rawSubs,
    exclude = exclude,
) {

    val matchAnyApp = rule.matchAnyApp ?: group.matchAnyApp ?: true
    val matchLauncher = rule.matchLauncher ?: group.matchLauncher ?: false
    val apps = mutableMapOf<String, GlobalApp>().apply {
        (rule.apps ?: group.apps ?: emptyList()).forEach { a ->
            this[a.id] = GlobalApp(
                id = a.id,
                enable = a.enable ?: true,
                activityIds = getFixActivityIds(a.id, a.activityIds),
                excludeActivityIds = getFixActivityIds(a.id, a.excludeActivityIds)
            )
        }
    }

    override val type = "global"

    private val excludeAppIds = apps.filter { e -> !e.value.enable }.keys
    override fun matchActivity(appId: String, activityId: String?): Boolean {
        if (!matchLauncher && appId == launcherAppId) return false
        if (!super.matchActivity(appId, activityId)) return false
        if (excludeAppIds.contains(appId)) {
            return false
        }
        val app = apps[appId] ?: return matchAnyApp
        activityId ?: return true
        if (app.excludeActivityIds.any { e -> e.startsWith(activityId) }) {
            return false
        }
        return app.activityIds.isEmpty() || app.activityIds.any { e -> e.startsWith(activityId) }
    }

}
