package li.gkd.app.domain.rule

import li.gkd.app.data.AppInfo
import li.gkd.app.data.ExcludeData
import li.gkd.app.data.RawSubscription

data class GlobalAppScope(val enabled: Boolean, val included: List<String>, val excluded: List<String>)

/** Pure applicability rules shared by execution and the settings UI. */
object RuleScopePolicy {
    fun fixActivities(appId: String, values: List<String>?): List<String> =
        values.orEmpty().map { if (it.startsWith('.')) appId + it else it }

    fun versionMatches(props: RawSubscription.RawAppRuleProps, info: AppInfo?): Boolean =
        info == null || (props.versionCode?.match(info.versionCode) != false &&
            props.versionName?.match(info.versionName) != false)

    fun globalAppEnabled(app: RawSubscription.RawGlobalApp, info: AppInfo?): Boolean =
        app.enable ?: versionMatches(app, info)

    fun globalScope(app: RawSubscription.RawGlobalApp, info: AppInfo?): GlobalAppScope = GlobalAppScope(
        globalAppEnabled(app, info), fixActivities(app.id, app.activityIds), fixActivities(app.id, app.excludeActivityIds),
    )

    fun appVersionMatches(group: RawSubscription.RawAppGroup, rule: RawSubscription.RawAppRule, info: AppInfo?): Boolean =
        info == null || ((rule.versionCode ?: group.versionCode)?.match(info.versionCode) != false &&
            (rule.versionName ?: group.versionName)?.match(info.versionName) != false)

    fun globalApp(
        group: RawSubscription.RawGlobalGroup,
        rule: RawSubscription.RawGlobalRule?,
        appId: String,
    ): RawSubscription.RawGlobalApp? = (rule?.apps ?: group.apps).orEmpty().lastOrNull { it.id == appId }

    fun globalDefault(
        group: RawSubscription.RawGlobalGroup,
        rule: RawSubscription.RawGlobalRule?,
        appId: String,
        launcherAppId: String,
        systemAppIds: Set<String>,
    ): Boolean {
        if (globalApp(group, rule, appId) != null) return true
        if (appId == launcherAppId && !(rule?.matchLauncher ?: group.matchLauncher ?: false)) return false
        if (appId in systemAppIds && !(rule?.matchSystemApp ?: group.matchSystemApp ?: false)) return false
        return rule?.matchAnyApp ?: group.matchAnyApp ?: true
    }

    fun globalRuleAllowed(
        group: RawSubscription.RawGlobalGroup,
        rule: RawSubscription.RawGlobalRule?,
        appId: String,
        info: AppInfo?,
        groupExcluded: Boolean,
    ): Boolean = !groupExcluded && globalApp(group, rule, appId)?.let { globalAppEnabled(it, info) } != false

    fun matchGlobalActivity(
        app: GlobalAppScope?,
        defaultEnabled: Boolean,
        appId: String,
        activityId: String?,
        groupExcluded: Boolean,
        exclude: ExcludeData,
    ): Boolean {
        if (groupExcluded || app?.enabled == false) return false
        if (exclude.appIds[appId] == true) return false
        // Personal global page entries have always been exact activity IDs.
        if (activityId != null && appId to activityId in exclude.activityIds) return false
        if (activityId != null && app?.excluded.orEmpty().any(activityId::startsWith)) return false
        if (exclude.appIds[appId] == false) return true
        if (app != null) {
            return activityId == null || app.included.isEmpty() || app.included.any(activityId::startsWith)
        }
        return defaultEnabled
    }
}
