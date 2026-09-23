package li.gkd.app.data

import li.gkd.app.a11y.launcherAppId
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.app.domain.rule.RuleScopePolicy

class GlobalRule(
    private val rawRule: RawSubscription.RawGlobalRule,
    g: ResolvedGlobalGroup,
    private val appInfoCache: Map<String, AppInfo>,
) : ResolvedRule(
    rule = rawRule,
    g = g,
) {
    val groupExcludeAppIds = g.groupExcludeAppIds
    val group = g.group
    private val matchAnyApp = rawRule.matchAnyApp ?: group.matchAnyApp ?: true
    private val matchLauncher = rawRule.matchLauncher ?: group.matchLauncher ?: false
    private val matchSystemApp = rawRule.matchSystemApp ?: group.matchSystemApp ?: false
    private val apps = (rawRule.apps ?: group.apps).orEmpty()
        // Only installed apps can supply runtime activity events (issue #619).
        .filter { appInfoCache.isEmpty() || it.id in appInfoCache }
        .associate { it.id to RuleScopePolicy.globalScope(it, appInfoCache[it.id]) }

    override val type = "global"

    override fun matchActivity(appId: String, activityId: String?): Boolean = RuleScopePolicy.matchGlobalActivity(
        apps[appId], matchAnyApp && (matchLauncher || appId != launcherAppId) &&
            (matchSystemApp || appId !in AppInfoRepository.systemAppsFlow.value),
        appId, activityId, appId in groupExcludeAppIds, excludeData,
    )
}
