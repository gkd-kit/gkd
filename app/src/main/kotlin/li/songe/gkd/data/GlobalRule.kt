package li.songe.gkd.data

import li.songe.gkd.service.TopActivity

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
    override fun matchActivity(topActivity: TopActivity): Boolean {
        if (!super.matchActivity(topActivity)) return false
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
