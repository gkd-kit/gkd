package li.songe.gkd.data

import li.songe.gkd.service.launcherAppId
import li.songe.gkd.util.systemAppsFlow

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
    val matchSystemApp = rule.matchSystemApp ?: group.matchSystemApp ?: false
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
        // 规则自带禁用
        if (excludeAppIds.contains(appId)) {
            return false
        }
        // 用户自定义禁用
        if (excludeData.excludeAppIds.contains(appId)) {
            return false
        }
        if (excludeData.includeAppIds.contains(appId)) {
            activityId ?: return true
            val app = apps[appId] ?: return true
            return !app.excludeActivityIds.any { e -> e.startsWith(activityId) }
        } else if (activityId != null && excludeData.activityIds.contains(appId to activityId)) {
            return false
        }
        if (!matchLauncher && appId == launcherAppId) {
            return false
        }
        if (!matchSystemApp && systemAppsFlow.value.contains(appId)) {
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
