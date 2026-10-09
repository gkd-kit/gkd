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
    private val matchAnyApp = g.matchAnyApp
    private val groupAppIds = group.apps.orEmpty().mapTo(mutableSetOf()) { it.id }
    private val apps = (groupAppIds + rawRule.apps.orEmpty().map { it.id })
        // Only installed apps can supply runtime activity events (issue #619).
        .filter { appInfoCache.isEmpty() || it in appInfoCache }
        .associateWith { id ->
            RuleScopePolicy.globalScope(
                checkNotNull(RuleScopePolicy.globalApp(group, rawRule, id)),
                appInfoCache[id], id in groupAppIds,
            )
        }

    override val type = "global"

    override fun matchActivity(appId: String, activityId: String?): Boolean {
        val environment = runtime.environment()
        return RuleScopePolicy.matchGlobalActivity(
            apps[appId],
            RuleScopePolicy.globalDefaultOffReason(
                group, appId, environment.launcherAppId, environment.systemAppIds, matchAnyApp,
                explicitlyIncluded = apps[appId]?.explicitlyIncluded == true,
            ) == null,
            appId, activityId, appId in groupExcludeAppIds, excludeData,
        )
    }
}
