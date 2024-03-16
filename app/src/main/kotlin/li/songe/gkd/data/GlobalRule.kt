package li.songe.gkd.data

import kotlinx.collections.immutable.ImmutableMap
import li.songe.gkd.service.launcherAppId
import li.songe.gkd.util.ResolvedGlobalGroup
import li.songe.gkd.util.systemAppsFlow

data class GlobalApp(
    val id: String,
    val enable: Boolean,
    val activityIds: List<String>,
    val excludeActivityIds: List<String>,
)

class GlobalRule(
    rule: RawSubscription.RawGlobalRule,
    g: ResolvedGlobalGroup,
    appInfoCache: ImmutableMap<String, AppInfo>,
) : ResolvedRule(
    rule = rule,
    g = g,
) {
    val group = g.group
    private val matchAnyApp = rule.matchAnyApp ?: group.matchAnyApp ?: true
    private val matchLauncher = rule.matchLauncher ?: group.matchLauncher ?: false
    private val matchSystemApp = rule.matchSystemApp ?: group.matchSystemApp ?: false
    val apps = mutableMapOf<String, GlobalApp>().apply {
        (rule.apps ?: group.apps ?: emptyList()).filter { a ->
            appInfoCache.containsKey(a.id) // 过滤掉未安装应用
        }.forEach { a ->
            val enable = a.enable ?: appInfoCache[a.id]?.let { appInfo ->
                if (a.excludeVersionCodes?.contains(appInfo.versionCode) == true) {
                    return@let false
                }
                if (a.excludeVersionNames?.contains(appInfo.versionName) == true) {
                    return@let false
                }
                a.versionCodes?.apply {
                    return@let contains(appInfo.versionCode)
                }
                a.versionNames?.apply {
                    return@let contains(appInfo.versionName)
                }
                null
            } ?: true
            this[a.id] = GlobalApp(
                id = a.id,
                enable = enable,
                activityIds = getFixActivityIds(a.id, a.activityIds),
                excludeActivityIds = getFixActivityIds(a.id, a.excludeActivityIds),
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
        if (activityId != null && excludeData.activityIds.contains(appId to activityId)) {
            return false
        }
        if (excludeData.includeAppIds.contains(appId)) {
            activityId ?: return true
            val app = apps[appId] ?: return true
            // 规则自带页面的禁用
            return !app.excludeActivityIds.any { e -> e.startsWith(activityId) }
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
