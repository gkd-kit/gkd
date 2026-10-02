package li.gkd.app.rule

import li.gkd.app.model.AppInfo
import li.gkd.app.subscription.RawSubscription

class AppRule(
    rule: RawSubscription.RawAppRule,
    g: ResolvedAppGroup,
    appInfo: AppInfo?,
    runtime: RuleRuntime,
) : ResolvedRule(
    rule = rule,
    g = g,
    runtime = runtime,
) {
    val group = g.group
    val app = g.app
    val enable = RuleScopePolicy.appVersionMatches(group, rule, appInfo)
    val appId = app.id
    private val activityIds =
        RuleScopePolicy.fixActivities(app.id, rule.activityIds ?: group.activityIds)
    private val excludeActivityIds =
        (RuleScopePolicy.fixActivities(
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
