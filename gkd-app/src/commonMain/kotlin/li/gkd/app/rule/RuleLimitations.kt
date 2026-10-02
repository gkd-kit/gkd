package li.gkd.app.rule

import li.gkd.app.model.AppInfo
import li.gkd.app.model.ExcludeData
import li.gkd.app.subscription.RawSubscription

enum class DefaultRuleScope { Launcher, SystemApp, UnspecifiedApp }

enum class RuleLimitationKind {
    AllowedPagePrefix, ExcludedPagePrefix, ExcludedPageExact, ExcludedApp,
    VersionCode, VersionName, AnyCondition, AllConditions, DefaultScope,
    EnabledApp, DisabledApp,
}

data class RuleLimitationSource(
    val ruleIndex: Int? = null,
    val ruleName: String? = null,
    val appId: String? = null,
)

data class RuleLimitation(
    val source: RuleLimitationSource,
    val kind: RuleLimitationKind,
    val value: String = "",
    val versionCode: RawSubscription.IntegerMatcher? = null,
    val versionName: RawSubscription.StringMatcher? = null,
    val appOverride: Boolean? = null,
    val defaultScope: DefaultRuleScope? = null,
    val excludedByGroupName: Boolean = false,
    val appliesTo: Set<Int> = emptySet(),
    val implicit: Boolean = false,
)

data class RuleLimitations(
    val builtIn: List<RuleLimitation> = emptyList(),
    val personal: List<RuleLimitation> = emptyList(),
    val blockedRules: Int = 0,
    val ruleCount: Int = 0,
    val blockedReasons: List<RuleRestriction> = emptyList(),
) {
    val fullyBlocked: Boolean get() = ruleCount > 0 && blockedRules == ruleCount
    val hasBuiltInProperties: Boolean get() = builtIn.any { !it.implicit }
    val hasPersonalProperties: Boolean get() = personal.isNotEmpty()
}

class RuleLimitationPolicy {
    fun resolve(
        subscription: RawSubscription,
        group: RawSubscription.RawGroupProps,
        appId: String?,
        exclude: ExcludeData,
        info: AppInfo?,
    ): RuleLimitations {
        val entries = mutableListOf<RuleLimitation>()
        val groupSource = RuleLimitationSource()
        fun add(source: RuleLimitationSource, kind: RuleLimitationKind, values: List<String>?) {
            values.orEmpty().forEach { entries.add(RuleLimitation(source, kind, it)) }
        }

        fun appProps(
            source: RuleLimitationSource,
            id: String,
            props: RawSubscription.RawAppRuleProps
        ) {
            add(
                source,
                RuleLimitationKind.AllowedPagePrefix,
                RuleScopePolicy.fixActivities(id, props.activityIds)
            )
            add(
                source,
                RuleLimitationKind.ExcludedPagePrefix,
                RuleScopePolicy.fixActivities(id, props.excludeActivityIds)
            )
            val appOverride = (props as? RawSubscription.RawGlobalApp)?.enable
            props.versionCode?.let {
                entries.add(
                    RuleLimitation(
                        source,
                        RuleLimitationKind.VersionCode,
                        versionCode = it, appOverride = appOverride
                    )
                )
            }
            props.versionName?.let {
                entries.add(
                    RuleLimitation(
                        source,
                        RuleLimitationKind.VersionName,
                        versionName = it, appOverride = appOverride
                    )
                )
            }
        }

        var blocked = 0
        val blockedReasons = linkedSetOf<RuleRestriction>()
        val groupExcluded = group is RawSubscription.RawGlobalGroup && appId != null &&
                appId in subscription.globalGroupAppGroupNameDisableMap[group.key].orEmpty()
        if (groupExcluded) {
            blockedReasons.add(RuleRestriction.ShadowedByAppRule)
        }
        group.rules.forEachIndexed { index, rule ->
            val entryStart = entries.size
            val source = RuleLimitationSource(ruleIndex = index + 1, ruleName = rule.name)
            add(source, RuleLimitationKind.AnyCondition, rule.excludeMatches)
            add(source, RuleLimitationKind.AllConditions, rule.excludeAllMatches)
            when (group) {
                is RawSubscription.RawAppGroup -> {
                    rule as RawSubscription.RawAppRule
                    val id = appId.orEmpty()
                    // A child field replaces the group's field, including an empty list.
                    add(
                        if (rule.activityIds != null) source else groupSource,
                        RuleLimitationKind.AllowedPagePrefix,
                        RuleScopePolicy.fixActivities(id, rule.activityIds ?: group.activityIds)
                    )
                    add(
                        if (rule.excludeActivityIds != null) source else groupSource,
                        RuleLimitationKind.ExcludedPagePrefix,
                        RuleScopePolicy.fixActivities(
                            id,
                            rule.excludeActivityIds ?: group.excludeActivityIds
                        )
                    )
                    (rule.versionCode ?: group.versionCode)?.let {
                        entries.add(
                            RuleLimitation(
                                if (rule.versionCode != null) source else groupSource,
                                RuleLimitationKind.VersionCode,
                                versionCode = it
                            )
                        )
                    }
                    (rule.versionName ?: group.versionName)?.let {
                        entries.add(
                            RuleLimitation(
                                if (rule.versionName != null) source else groupSource,
                                RuleLimitationKind.VersionName,
                                versionName = it
                            )
                        )
                    }
                    if (!RuleScopePolicy.appVersionMatches(group, rule, info)) {
                        blocked++
                        blockedReasons.add(RuleRestriction.VersionMismatch)
                    }
                }

                is RawSubscription.RawGlobalGroup -> {
                    rule as RawSubscription.RawGlobalRule
                    val propsSource = if (rule.apps != null) source else groupSource
                    (rule.apps ?: group.apps).orEmpty().filter { appId == null || it.id == appId }
                        .forEach { app ->
                            val origin = propsSource.copy(appId = app.id)
                            if (app.enable == false) entries.add(
                                RuleLimitation(
                                    origin,
                                    RuleLimitationKind.ExcludedApp,
                                    app.id
                                )
                            )
                            appProps(origin, app.id, app)
                        }
                    if (!(rule.matchLauncher ?: group.matchLauncher ?: false)) entries.add(
                        RuleLimitation(
                            source,
                            RuleLimitationKind.DefaultScope,
                            defaultScope = DefaultRuleScope.Launcher,
                            implicit = rule.matchLauncher == null && group.matchLauncher == null
                        )
                    )
                    if (!(rule.matchSystemApp ?: group.matchSystemApp ?: false)) entries.add(
                        RuleLimitation(
                            source,
                            RuleLimitationKind.DefaultScope,
                            defaultScope = DefaultRuleScope.SystemApp,
                            implicit = rule.matchSystemApp == null && group.matchSystemApp == null
                        )
                    )
                    if (!(rule.matchAnyApp ?: group.matchAnyApp ?: true)) entries.add(
                        RuleLimitation(
                            source,
                            RuleLimitationKind.DefaultScope,
                            defaultScope = DefaultRuleScope.UnspecifiedApp
                        )
                    )
                    if (appId != null && !RuleScopePolicy.globalRuleAllowed(
                            group,
                            rule,
                            appId,
                            info,
                            groupExcluded
                        )
                    ) {
                        blocked++
                        if (!groupExcluded) {
                            blockedReasons.add(
                                if (RuleScopePolicy.globalApp(group, rule, appId)?.enable == false)
                                    RuleRestriction.AppExcluded else RuleRestriction.VersionMismatch
                            )
                        }
                    }
                }
            }
            for (entryIndex in entryStart until entries.size) {
                entries[entryIndex] = entries[entryIndex].copy(appliesTo = setOf(index + 1))
            }
        }
        if (group.rules.isEmpty()) {
            when (group) {
                is RawSubscription.RawAppGroup -> appProps(groupSource, appId.orEmpty(), group)
                is RawSubscription.RawGlobalGroup -> group.apps.orEmpty()
                    .filter { appId == null || it.id == appId }.forEach {
                        if (it.enable == false) entries.add(
                            RuleLimitation(
                                groupSource,
                                RuleLimitationKind.ExcludedApp,
                                it.id
                            )
                        )
                        appProps(groupSource.copy(appId = it.id), it.id, it)
                    }
            }
            if (group is RawSubscription.RawGlobalGroup && appId != null && !groupExcluded &&
                !RuleScopePolicy.globalRuleAllowed(group, null, appId, info, false)
            ) {
                blockedReasons.add(
                    if (RuleScopePolicy.globalApp(group, null, appId)?.enable == false)
                        RuleRestriction.AppExcluded else RuleRestriction.VersionMismatch
                )
            }
        }
        if (group is RawSubscription.RawGlobalGroup) {
            subscription.globalGroupAppGroupNameDisableMap[group.key].orEmpty()
                .filter { appId == null || it == appId }.forEach {
                    entries.add(
                        RuleLimitation(
                            groupSource,
                            RuleLimitationKind.ExcludedApp,
                            it, excludedByGroupName = true
                        )
                    )
                }
        }
        val personal = buildList {
            exclude.activityIds.filter { appId == null || it.first == appId }
                .forEach { (id, activity) ->
                    add(
                        RuleLimitation(
                            RuleLimitationSource(appId = id),
                            if (group is RawSubscription.RawGlobalGroup)
                                RuleLimitationKind.ExcludedPageExact else RuleLimitationKind.ExcludedPagePrefix,
                            activity
                        )
                    )
                }
            // In an app context, this override is already represented by the switch itself.
            if (appId == null) exclude.appIds.forEach { (id, excluded) ->
                add(
                    RuleLimitation(
                        RuleLimitationSource(), if (excluded)
                            RuleLimitationKind.DisabledApp else RuleLimitationKind.EnabledApp, id
                    )
                )
            }
        }
        val effectiveEntries =
            entries.groupBy { it.copy(appliesTo = emptySet(), implicit = false) }.values.map { related ->
                related.first().copy(appliesTo = related.flatMapTo(mutableSetOf()) { it.appliesTo })
            }
        return RuleLimitations(
            effectiveEntries,
            personal,
            blocked,
            group.rules.size,
            blockedReasons.toList()
        )
    }
}
