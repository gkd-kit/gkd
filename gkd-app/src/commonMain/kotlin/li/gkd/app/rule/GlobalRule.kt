package li.gkd.app.rule

import li.gkd.app.model.AppInfo
import li.gkd.app.subscription.RawSubscription

class GlobalRule(
    rawRule: RawSubscription.RawGlobalRule,
    g: ResolvedGlobalGroup,
    appInfoCache: Map<String, AppInfo>,
    runtime: RuleRuntime,
) : ResolvedRule(
    rule = rawRule,
    g = g,
    runtime = runtime,
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

    override fun matchActivity(appId: String, activityId: String?): Boolean {
        val environment = runtime.environment()
        return RuleScopePolicy.matchGlobalActivity(
            apps[appId],
            matchAnyApp && (matchLauncher || appId != environment.launcherAppId) &&
                    (matchSystemApp || appId !in environment.systemAppIds),
            appId, activityId, appId in groupExcludeAppIds, excludeData,
        )
    }
}
