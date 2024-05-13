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
            // https://github.com/gkd-kit/gkd/issues/619
            appInfoCache.isEmpty() || appInfoCache.containsKey(a.id) // 过滤掉未安装应用
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
    private val enableApps = apps.filter { e -> e.value.enable }

    /**
     * 内置禁用>用户配置>规则自带
     * 范围越精确优先级越高
     */
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
            val app = enableApps[appId] ?: return true
            // 规则自带页面的禁用
            return !app.excludeActivityIds.any { e -> e.startsWith(activityId) }
        }

        // 范围比较
        val app = enableApps[appId]
        if (app != null) { // 规则自定义启用
            activityId ?: return true
            return app.activityIds.isEmpty() || app.activityIds.any { e -> e.startsWith(activityId) }
        } else {
            if (!matchLauncher && appId == launcherAppId) {
                return false
            }
            if (!matchSystemApp && systemAppsFlow.value.contains(appId)) {
                return false
            }
            return matchAnyApp
        }
    }

}
