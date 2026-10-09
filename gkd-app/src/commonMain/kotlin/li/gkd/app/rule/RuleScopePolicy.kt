package li.gkd.app.rule

import li.gkd.app.model.AppInfo
import li.gkd.app.model.ExcludeData
import li.gkd.app.subscription.RawSubscription

data class GlobalAppScope(
    val enabled: Boolean,
    val included: List<String>,
    val excluded: List<String>,
    val versionAllowed: Boolean = true,
    val explicitlyIncluded: Boolean = true,
)

enum class GlobalAppDefaultOffReason {
    Launcher, SystemApp, LocalDefault, SubscriptionDefault, SubscriptionApp, SameNameAppGroup,
}

/** Pure applicability rules shared by execution and the settings UI. */
object RuleScopePolicy {
    fun fixActivities(appId: String, values: List<String>?): List<String> =
        values.orEmpty().map { if (it.startsWith('.')) appId + it else it }

    fun versionMatches(props: RawSubscription.RawAppRuleProps, info: AppInfo?): Boolean =
        info == null || (props.versionCode?.match(info.versionCode) != false &&
                props.versionName?.match(info.versionName) != false)

    fun globalScope(app: RawSubscription.RawGlobalApp, info: AppInfo?, explicitlyIncluded: Boolean = true): GlobalAppScope =
        GlobalAppScope(
            app.enable != false,
            fixActivities(app.id, app.activityIds),
            fixActivities(app.id, app.excludeActivityIds),
            versionMatches(app, info),
            explicitlyIncluded,
        )

    fun appVersionMatches(
        group: RawSubscription.RawAppGroup,
        rule: RawSubscription.RawAppRule,
        info: AppInfo?
    ): Boolean =
        info == null || ((rule.versionCode
            ?: group.versionCode)?.match(info.versionCode) != false &&
                (rule.versionName ?: group.versionName)?.match(info.versionName) != false)

    fun globalApp(
        group: RawSubscription.RawGlobalGroup,
        rule: RawSubscription.RawGlobalRule?,
        appId: String,
    ): RawSubscription.RawGlobalApp? {
        val parent = group.apps.orEmpty().lastOrNull { it.id == appId }
        val child = rule?.apps.orEmpty().lastOrNull { it.id == appId }
        if (parent == null && child == null) return null
        return RawSubscription.RawGlobalApp(
            id = appId,
            enable = parent?.enable,
            activityIds = child?.activityIds ?: parent?.activityIds,
            excludeActivityIds = child?.excludeActivityIds ?: parent?.excludeActivityIds,
            versionCode = child?.versionCode ?: parent?.versionCode,
            versionName = child?.versionName ?: parent?.versionName,
        )
    }

    fun globalAppDisabled(group: RawSubscription.RawGlobalGroup, appId: String): Boolean =
        group.apps.orEmpty().lastOrNull { it.id == appId }?.enable == false

    fun globalDefaultOffReason(
        group: RawSubscription.RawGlobalGroup,
        appId: String,
        launcherAppId: String,
        systemAppIds: Set<String>,
        matchAnyApp: Boolean? = null,
        explicitlyIncluded: Boolean = group.apps.orEmpty().any { it.id == appId },
    ): GlobalAppDefaultOffReason? = when {
        explicitlyIncluded -> null
        appId == launcherAppId && group.matchLauncher != true -> GlobalAppDefaultOffReason.Launcher
        appId in systemAppIds && group.matchSystemApp != true -> GlobalAppDefaultOffReason.SystemApp
        matchAnyApp == false -> GlobalAppDefaultOffReason.LocalDefault
        matchAnyApp == null && group.matchAnyApp == false -> GlobalAppDefaultOffReason.SubscriptionDefault
        else -> null
    }

    fun globalRuleRestriction(
        group: RawSubscription.RawGlobalGroup,
        rule: RawSubscription.RawGlobalRule?,
        appId: String,
        info: AppInfo?,
        groupExcluded: Boolean,
        manuallyEnabled: Boolean = false,
    ): RuleRestriction? {
        val app = globalApp(group, rule, appId)
        return globalAppRestriction(
            app?.enable != false, app == null || versionMatches(app, info),
            groupExcluded, manuallyEnabled,
        )
    }

    private fun globalAppRestriction(
        enabled: Boolean,
        versionAllowed: Boolean,
        groupExcluded: Boolean,
        manuallyEnabled: Boolean,
    ): RuleRestriction? = when {
        !manuallyEnabled && groupExcluded -> RuleRestriction.ShadowedByAppRule
        !manuallyEnabled && !enabled -> RuleRestriction.AppExcluded
        !versionAllowed -> RuleRestriction.VersionMismatch
        else -> null
    }

    fun matchGlobalActivity(
        app: GlobalAppScope?,
        defaultEnabled: Boolean,
        appId: String,
        activityId: String?,
        groupExcluded: Boolean,
        exclude: ExcludeData,
    ): Boolean {
        val manuallyEnabled = exclude.appIds[appId] == false
        if (globalAppRestriction(
                app?.enabled != false, app?.versionAllowed != false, groupExcluded, manuallyEnabled,
            ) != null
        ) return false
        if (exclude.appIds[appId] == true) return false
        // Personal global page entries have always been exact activity IDs.
        if (activityId != null && appId to activityId in exclude.activityIds) return false
        if (activityId != null && app?.excluded.orEmpty().any(activityId::startsWith)) return false
        if (exclude.appIds[appId] == false) return true
        if (app != null) {
            return (app.explicitlyIncluded || defaultEnabled) &&
                    (activityId == null || app.included.isEmpty() || app.included.any(activityId::startsWith))
        }
        return defaultEnabled
    }
}
