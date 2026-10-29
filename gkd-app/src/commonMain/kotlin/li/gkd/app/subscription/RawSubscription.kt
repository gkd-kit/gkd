package li.gkd.app.subscription

import java.util.regex.PatternSyntaxException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import li.gkd.app.model.ScreenSize
import li.gkd.app.subscription.SubscriptionJson.json
import li.gkd.app.subscription.SubscriptionJson.toJson5String
import li.gkd.db.RuleGroupType
import li.gkd.db.SubscriptionIds

@Serializable
data class RawSubscription(
    val id: Long,
    val name: String,
    val version: Int,
    val author: String? = null,
    val updateUrl: String? = null,
    val supportUri: String? = null,
    val checkUpdateUrl: String? = null,
    val globalGroups: List<RawGlobalGroup> = emptyList(),
    val categories: List<RawCategory> = emptyList(),
    val apps: List<RawApp> = emptyList(),
) {
    fun edit(block: SubscriptionEditor.() -> Unit): RawSubscription =
        SubscriptionEditor(this).apply(block).build()

    fun editResult(
        block: SubscriptionEditor.() -> SubscriptionEditResult<*>,
    ): SubscriptionEditResult<RawSubscription> {
        val editor = SubscriptionEditor(this)
        return editor.block().map { editor.build() }
    }

    // 重写 equals 和 hashCode 便于 compose 重组比较
    override fun equals(other: Any?): Boolean {
        return other === this
    }

    override fun hashCode(): Int {
        return listOf(id, name, version).hashCode()
    }

    val isEmpty: Boolean
        get() = globalGroups.isEmpty() && apps.all { it.groups.isEmpty() } && categories.isEmpty()

    val isLocal: Boolean
        get() = SubscriptionIds.isLocal(id)

    val hasRule
        get() = globalGroups.isNotEmpty() || apps.any { it.groups.isNotEmpty() }

    private val categoryAppsMap: Map<Int?, List<RawApp>> by lazy {
        val map = mutableMapOf<Int?, MutableList<RawApp>>()
        apps.forEach { app ->
            app.groups
                .groupBy { getCategory(it.name)?.key }
                .forEach { (key, groups) ->
                    map.getOrPut(key) { mutableListOf() }.add(app.copy(groups = groups))
                }
        }
        map
    }

    fun getCategoryApps(categoryKey: Int?): List<RawApp> {
        return categoryAppsMap[categoryKey] ?: emptyList()
    }

    fun getCategory(groupName: String): RawCategory? {
        return categories.find { c -> groupName.startsWith(c.name) }
    }

    fun getAppGroups(appId: String): List<RawAppGroup> {
        return apps.find { a -> a.id == appId }?.groups ?: emptyList()
    }

    fun getApp(appId: String, fallbackName: String? = null): RawApp {
        return apps.find { a -> a.id == appId }
            ?: RawApp(
                id = appId,
                name = fallbackName,
                groups = emptyList(),
            )
    }

    val appGroups by lazy {
        apps.flatMap { a -> a.groups }
    }

    val globalGroupAppGroupMatches by lazy {
        globalGroups.associate { group ->
            val prefix = group.disableIfAppGroupMatch?.ifEmpty { group.name }
            group.key to
                if (prefix == null) emptyMap()
                else
                    apps
                        .mapNotNull { app ->
                            val matches =
                                app.groups.filter {
                                    it.ignoreGlobalGroupMatch != true && it.name.startsWith(prefix)
                                }
                            matches.takeIf { it.isNotEmpty() }?.let { app.id to it }
                        }
                        .toMap()
        }
    }

    val globalGroupAppGroupNameDisableMap by lazy {
        globalGroupAppGroupMatches.mapValues { (_, apps) -> apps.keys }
    }

    @Serializable
    data class StringMatcher(
        val pattern: String?,
        val include: List<String>?,
        val exclude: List<String>?,
    ) {
        private val patternCompilation: Result<Regex>? by lazy {
            pattern?.let { p ->
                try {
                    Result.success(Regex(p))
                } catch (error: PatternSyntaxException) {
                    Result.failure(error)
                }
            }
        }

        val patternError: String?
            get() = patternCompilation?.exceptionOrNull()?.let { it.message ?: it.toString() }

        fun match(value: String?): Boolean = match(value, ignoreInvalidPattern = false)

        /**
         * Saving a preference may ignore a syntax error, but still respects the other conditions.
         */
        fun matchValidConditions(value: String?): Boolean =
            match(value, ignoreInvalidPattern = true)

        private fun match(value: String?, ignoreInvalidPattern: Boolean): Boolean {
            if (value == null) return false
            if (exclude?.contains(value) == true) return false
            if (include?.contains(value) == false) return false
            val compiled = patternCompilation ?: return true
            return compiled.getOrNull()?.matches(value) ?: ignoreInvalidPattern
        }
    }

    @Serializable
    data class IntegerMatcher(
        val minimum: Int?,
        val maximum: Int?,
        val include: List<Int>?,
        val exclude: List<Int>?,
    ) {
        fun match(value: Int?): Boolean {
            if (value == null) return false
            if (exclude?.contains(value) == true) return false
            if (include?.contains(value) == false) return false
            if (minimum != null && value < minimum) return false
            if (maximum != null && value > maximum) return false
            return true
        }
    }

    @Serializable
    data class RawApp(
        val id: String,
        val name: String?,
        val groups: List<RawAppGroup> = emptyList(),
    )

    @Serializable
    data class RawCategory(
        val key: Int,
        val name: String,
        val enable: Boolean?,
        val desc: String?,
    )

    @Serializable
    data class Position(
        val left: String?,
        val top: String?,
        val right: String?,
        val bottom: String?,
        val x: String?,
        val y: String?,
    ) {
        private val compilation by lazy { RulePositionCompilation(this) }

        val validationError: PositionError?
            get() = compilation.error

        val isValid
            get() = validationError == null

        /** Missing configuration is handled by the caller; invalid or failed coordinates throw. */
        fun calc(
            rect: RuleBounds,
            screenSize: ScreenSize,
            fieldPrefix: String = "position",
            captureInputs: Boolean = false,
        ): Pair<Float, Float> = compilation.calculate(rect, screenSize, fieldPrefix, captureInputs)
    }

    sealed interface RawCommonProps {
        val actionCd: Long?
        val actionDelay: Long?
        val fastQuery: Boolean?
        val matchRoot: Boolean?
        val matchDelay: Long?
        val matchTime: Long?
        val actionMaximum: Int?
        val resetMatch: String?
        val actionCdKey: Int?
        val actionMaximumKey: Int?
        val order: Int?
        val forcedTime: Long?
        val snapshotUrls: List<String>?
        val excludeSnapshotUrls: List<String>?
        val exampleUrls: List<String>?
        val priorityTime: Long?
        val priorityActionMaximum: Int?
    }

    @Serializable
    data class SwipeArg(
        val start: Position,
        val end: Position?,
        val duration: Long,
    )

    interface LocationProps {
        // click
        val position: Position?

        // swipe
        val swipeArg: SwipeArg?
    }

    sealed interface RawRuleProps : RawCommonProps, LocationProps {
        val name: String?
        val key: Int?
        val preKeys: List<Int>?
        val action: String?
        val matches: List<String>?
        val anyMatches: List<String>?
        val excludeMatches: List<String>?
        val excludeAllMatches: List<String>?

        fun getAllSelectorStrings(): List<String> {
            return listOfNotNull(matches, excludeMatches, anyMatches, excludeAllMatches).flatten()
        }
    }

    sealed interface RawGroupProps : RawCommonProps {
        val name: String
        val key: Int
        val desc: String?
        val enable: Boolean?
        val scopeKeys: List<Int>?
        val rules: List<RawRuleProps>

        val compilation: RuleGroupCompilation
        val valid: Boolean
            get() = validationError == null

        val validationError: RuleValidationError?
            get() = compilation.error

        val allExampleUrls: List<String>
        val cacheStr: String
        val cacheJsonObject: JsonObject

        val groupType: Int
            get() =
                when (this) {
                    is RawAppGroup -> RuleGroupType.App
                    is RawGlobalGroup -> RuleGroupType.Global
                }
    }

    sealed interface RawAppRuleProps {
        val activityIds: List<String>?
        val excludeActivityIds: List<String>?

        val versionCode: IntegerMatcher?
        val versionName: StringMatcher?
    }

    @Serializable
    data class RawGlobalApp(
        val id: String,
        val enable: Boolean?,
        override val activityIds: List<String>?,
        override val excludeActivityIds: List<String>?,
        override val versionCode: IntegerMatcher?,
        override val versionName: StringMatcher?,
    ) : RawAppRuleProps

    @Serializable
    data class RawGlobalRuleApp(
        val id: String,
        override val activityIds: List<String>?,
        override val excludeActivityIds: List<String>?,
        override val versionCode: IntegerMatcher?,
        override val versionName: StringMatcher?,
    ) : RawAppRuleProps

    @Serializable
    data class RawGlobalGroup(
        override val key: Int,
        override val name: String,
        override val desc: String?,
        override val enable: Boolean?,
        override val scopeKeys: List<Int>?,
        override val actionCd: Long?,
        override val actionDelay: Long?,
        override val fastQuery: Boolean?,
        override val matchRoot: Boolean?,
        override val matchDelay: Long?,
        override val matchTime: Long?,
        override val actionMaximum: Int?,
        override val resetMatch: String?,
        override val actionCdKey: Int?,
        override val actionMaximumKey: Int?,
        override val priorityTime: Long?,
        override val priorityActionMaximum: Int?,
        override val order: Int?,
        override val forcedTime: Long?,
        override val snapshotUrls: List<String>?,
        override val excludeSnapshotUrls: List<String>?,
        override val exampleUrls: List<String>?,
        val matchAnyApp: Boolean?,
        val matchSystemApp: Boolean?,
        val matchLauncher: Boolean?,
        val disableIfAppGroupMatch: String?,
        override val rules: List<RawGlobalRule>,
        val apps: List<RawGlobalApp>?,
    ) : RawGroupProps {
        override val compilation by lazy { RuleGroupCompilation.compile(this) }
        override val allExampleUrls by lazy {
            ((exampleUrls ?: emptyList()) +
                    rules.flatMap { r ->
                        r.exampleUrls ?: emptyList()
                    })
                .distinct()
        }
        override val cacheStr by lazy { toJson5String(this) }
        override val cacheJsonObject by lazy { json.encodeToJsonElement(this).jsonObject }
    }

    @Serializable
    data class RawGlobalRule(
        override val key: Int?,
        override val name: String?,
        override val actionCd: Long?,
        override val actionDelay: Long?,
        override val fastQuery: Boolean?,
        override val matchRoot: Boolean?,
        override val matchDelay: Long?,
        override val matchTime: Long?,
        override val actionMaximum: Int?,
        override val resetMatch: String?,
        override val actionCdKey: Int?,
        override val actionMaximumKey: Int?,
        override val priorityTime: Long?,
        override val priorityActionMaximum: Int?,
        override val order: Int?,
        override val forcedTime: Long?,
        override val snapshotUrls: List<String>?,
        override val excludeSnapshotUrls: List<String>?,
        override val exampleUrls: List<String>?,
        override val preKeys: List<Int>?,
        override val action: String?,
        override val position: Position?,
        override val swipeArg: SwipeArg?,
        override val matches: List<String>?,
        override val excludeMatches: List<String>?,
        override val excludeAllMatches: List<String>?,
        override val anyMatches: List<String>?,
        val apps: List<RawGlobalRuleApp>?,
    ) : RawRuleProps

    @Serializable
    data class RawAppGroup(
        override val key: Int,
        override val name: String,
        override val desc: String?,
        override val enable: Boolean?,
        override val scopeKeys: List<Int>?,
        override val actionCdKey: Int?,
        override val actionMaximumKey: Int?,
        override val actionCd: Long?,
        override val actionDelay: Long?,
        override val fastQuery: Boolean?,
        override val matchRoot: Boolean?,
        override val actionMaximum: Int?,
        override val priorityTime: Long?,
        override val priorityActionMaximum: Int?,
        override val order: Int?,
        override val forcedTime: Long?,
        override val matchDelay: Long?,
        override val matchTime: Long?,
        override val resetMatch: String?,
        override val snapshotUrls: List<String>?,
        override val excludeSnapshotUrls: List<String>?,
        override val exampleUrls: List<String>?,
        override val activityIds: List<String>?,
        override val excludeActivityIds: List<String>?,
        override val rules: List<RawAppRule>,
        override val versionCode: IntegerMatcher?,
        override val versionName: StringMatcher?,
        val ignoreGlobalGroupMatch: Boolean?,
    ) : RawGroupProps, RawAppRuleProps {
        override val compilation by lazy { RuleGroupCompilation.compile(this) }
        override val allExampleUrls by lazy {
            ((exampleUrls ?: emptyList()) +
                    rules.flatMap { r ->
                        r.exampleUrls ?: emptyList()
                    })
                .distinct()
        }
        override val cacheStr by lazy { toJson5String(this) }
        override val cacheJsonObject by lazy { json.encodeToJsonElement(this).jsonObject }
    }

    @Serializable
    data class RawAppRule(
        override val key: Int?,
        override val name: String?,
        override val preKeys: List<Int>?,
        override val action: String?,
        override val position: Position?,
        override val swipeArg: SwipeArg?,
        override val matches: List<String>?,
        override val excludeMatches: List<String>?,
        override val excludeAllMatches: List<String>?,
        override val anyMatches: List<String>?,
        override val actionCdKey: Int?,
        override val actionMaximumKey: Int?,
        override val actionCd: Long?,
        override val actionDelay: Long?,
        override val fastQuery: Boolean?,
        override val matchRoot: Boolean?,
        override val actionMaximum: Int?,
        override val priorityTime: Long?,
        override val priorityActionMaximum: Int?,
        override val order: Int?,
        override val forcedTime: Long?,
        override val matchDelay: Long?,
        override val matchTime: Long?,
        override val resetMatch: String?,
        override val snapshotUrls: List<String>?,
        override val excludeSnapshotUrls: List<String>?,
        override val exampleUrls: List<String>?,
        override val activityIds: List<String>?,
        override val excludeActivityIds: List<String>?,
        override val versionCode: IntegerMatcher?,
        override val versionName: StringMatcher?,
    ) : RawRuleProps, RawAppRuleProps
}
