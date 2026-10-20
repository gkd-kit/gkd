package li.gkd.app.subscription

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import li.gkd.app.subscription.SubscriptionJson.json
import li.gkd.app.subscription.SubscriptionJson.toJson5String
import li.gkd.db.RuleGroupType
import li.gkd.db.SubscriptionIds
import li.gkd.selector.Selector
import li.gkd.selector.SelectorCompileResult
import li.gkd.selector.SelectorTypeResult
import li.gkd.selector.createDefaultSelectorTypeModel
import li.songe.json5.Json5
import li.songe.json5.Json5ParseException
import kotlin.random.Random

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

    val hasRule get() = globalGroups.isNotEmpty() || apps.any { it.groups.isNotEmpty() }

    private val categoryAppsMap: Map<Int?, List<RawApp>> by lazy {
        val map = mutableMapOf<Int?, MutableList<RawApp>>()
        apps.forEach { app ->
            app.groups.groupBy { getCategory(it.name)?.key }.forEach { (key, groups) ->
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
        return apps.find { a -> a.id == appId } ?: RawApp(
            id = appId,
            name = fallbackName,
            groups = emptyList()
        )
    }

    val appGroups by lazy {
        apps.flatMap { a -> a.groups }
    }

    val globalGroupAppGroupMatches by lazy {
        globalGroups.associate { group ->
            val prefix = group.disableIfAppGroupMatch?.ifEmpty { group.name }
            group.key to if (prefix == null) emptyMap() else apps.mapNotNull { app ->
                val matches = app.groups.filter {
                    it.ignoreGlobalGroupMatch != true && it.name.startsWith(prefix)
                }
                matches.takeIf { it.isNotEmpty() }?.let { app.id to it }
            }.toMap()
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
        private val patternRegex by lazy {
            pattern?.let { p ->
                runCatching { Regex(p) }.getOrNull()
            }
        }

        fun match(value: String?): Boolean {
            if (value == null) return false
            if (exclude?.contains(value) == true) return false
            if (include?.contains(value) == false) return false
            if (patternRegex?.matches(value) == false) return false
            return true
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
        private val leftExp by lazy { getExpression(left) }
        private val topExp by lazy { getExpression(top) }
        private val rightExp by lazy { getExpression(right) }
        private val bottomExp by lazy { getExpression(bottom) }
        private val xExp by lazy { getExpression(x) }
        private val yExp by lazy { getExpression(y) }

        private val xArr by lazy { arrayOf(leftExp, rightExp, xExp) }
        private val yArr by lazy { arrayOf(topExp, bottomExp, yExp) }

        val isValid by lazy {
            xArr.any { it != null } && yArr.any { it != null }
        }

        /**
         * return (x, y)
         */
        fun calc(
            rect: RuleBounds,
            screenWidth: Int,
            screenHeight: Int,
            onError: (Exception) -> Unit = {}
        ): Pair<Float, Float>? {
            if (!isValid) return null
            xArr.forEach { setVariables(it, rect, screenWidth, screenHeight) }
            yArr.forEach { setVariables(it, rect, screenWidth, screenHeight) }
            try {
                val x0 = xArr.find { it != null }!!.evaluate().toFloat()
                val y0 = yArr.find { it != null }!!.evaluate().toFloat()
                val x = when {
                    leftExp != null -> rect.left + x0
                    rightExp != null -> rect.right - x0
                    xExp != null -> x0
                    else -> null
                }
                val y = when {
                    topExp != null -> rect.top + y0
                    bottomExp != null -> rect.bottom - y0
                    yExp != null -> y0
                    else -> null
                }
                if (x != null && y != null) {
                    return x to y
                }
            } catch (e: Exception) {
                // 可能存在 1/0 导致错误
                onError(e)
            }
            return null
        }
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

        val valid: Boolean
        val validationError: RuleValidationError?
        val allExampleUrls: List<String>
        val cacheMap: MutableMap<String, Selector?>
        val cacheStr: String
        val cacheJsonObject: JsonObject

        val groupType: Int
            get() = when (this) {
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
        override val cacheMap by lazy { HashMap<String, Selector?>() }
        override val validationError by lazy { validateRules() }
        override val valid get() = validationError == null
        override val allExampleUrls by lazy {
            ((exampleUrls ?: emptyList()) + rules.flatMap { r ->
                r.exampleUrls ?: emptyList()
            }).distinct()
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
        val apps: List<RawGlobalRuleApp>?
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
        override val cacheMap by lazy { HashMap<String, Selector?>() }
        override val validationError by lazy { validateRules() }
        override val valid get() = validationError == null
        override val allExampleUrls by lazy {
            ((exampleUrls ?: emptyList()) + rules.flatMap { r ->
                r.exampleUrls ?: emptyList()
            }).distinct()
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

    companion object {
        private fun RawGroupProps.validateRules(): RuleValidationError? {
            val allSelectorStrings = rules.flatMap { r ->
                r.getAllSelectorStrings()
            }
            allSelectorStrings.forEach { source ->
                val selector = when (val result = Selector.compile(source)) {
                    is SelectorCompileResult.Success -> result.value
                    is SelectorCompileResult.Failure -> {
                        return RuleValidationError.SelectorError(
                            source,
                            result.error.message
                        )
                    }
                }
                when (val result = selector.validateType(createDefaultSelectorTypeModel())) {
                    is SelectorTypeResult.Success -> cacheMap[source] = result.value
                    is SelectorTypeResult.Failure -> {
                        return RuleValidationError.SelectorError(
                            source,
                            result.error.message
                        )
                    }
                }
            }
            rules.forEach { r ->
                if (r.position?.isValid == false) {
                    return RuleValidationError.InvalidPosition(r.position.toString())
                }
            }
            return null
        }

        private val preFillExpVars = arrayOf(
            "left",
            "top",
            "right",
            "bottom",
            "width",
            "height",
            "random",
            "screenWidth",
            "screenHeight",
        )

        private fun setVariables(
            exp: RuleExpression?,
            rect: RuleBounds,
            screenWidth: Int,
            screenHeight: Int
        ) {
            if (exp == null) return
            exp.setVariable("left", rect.left.toDouble())
            exp.setVariable("top", rect.top.toDouble())
            exp.setVariable("right", rect.right.toDouble())
            exp.setVariable("bottom", rect.bottom.toDouble())
            exp.setVariable("width", rect.width().toDouble())
            exp.setVariable("height", rect.height().toDouble())
            exp.setVariable("random", Random.nextDouble())
            exp.setVariable("screenWidth", screenWidth.toDouble())
            exp.setVariable("screenHeight", screenHeight.toDouble())
        }

        private fun getExpression(value: String?): RuleExpression? =
            value?.let { compileRuleExpression(it, preFillExpVars.toSet()) }

        private class Parser {
            private val path = mutableListOf<Any>()

            private inline fun <T> at(segment: Any, block: () -> T): T {
                path.add(segment)
                try {
                    return block()
                } finally {
                    path.removeAt(path.lastIndex)
                }
            }

            private fun fail(detail: String): Nothing {
                val fieldPath = buildString {
                    append('$')
                    path.forEach { segment ->
                        if (segment is Int) append("[$segment]") else append(".$segment")
                    }
                }
                throw SubscriptionParseException(fieldPath, detail)
            }

            private fun requireObject(element: JsonElement): JsonObject =
                element as? JsonObject ?: fail("Expected an object")

            private fun <T : Any> required(jsonObject: JsonObject, key: String, value: T?, type: String): T =
                value ?: at(key) {
                    if (jsonObject[key] == null || jsonObject[key] == JsonNull) {
                        fail("Required field is missing")
                    }
                    fail("Expected $type")
                }

            private fun <T> getArray(jsonObject: JsonObject, key: String, read: (JsonElement) -> T): List<T>? = at(key) {
                val element = jsonObject[key] ?: return@at null
                val array = element as? JsonArray ?: fail("Expected an array")
                array.mapIndexed { index, value -> at(index) { read(value) } }
            }

            private fun <T> getFlexibleList(jsonObject: JsonObject, key: String, read: (JsonElement) -> T): List<T> = at(key) {
                when (val element = jsonObject[key]) {
                    null, JsonNull -> emptyList()
                    is JsonArray -> element.mapIndexed { index, value -> at(index) { read(value) } }
                    else -> listOf(read(element))
                }
            }

            private fun getPosition(jsonObject: JsonObject): Position? = at("position") {
                val element = jsonObject["position"] as? JsonObject ?: return@at null
                readPosition(element)
            }

            private fun readPosition(element: JsonObject): Position = Position(
                left = getPositionValue(element, "left"),
                bottom = getPositionValue(element, "bottom"),
                top = getPositionValue(element, "top"),
                right = getPositionValue(element, "right"),
                x = getPositionValue(element, "x"),
                y = getPositionValue(element, "y"),
            )

            private fun getPositionValue(jsonObject: JsonObject, key: String): String? = at(key) {
                val value = jsonObject[key] ?: return@at null
                (value as? JsonPrimitive)?.content ?: fail("Expected a primitive position expression")
            }

            private fun getSwipeArg(jsonObject: JsonObject): SwipeArg? = at("swipeArg") {
                val element = jsonObject["swipeArg"] as? JsonObject ?: return@at null
                SwipeArg(
                    start = at("start") {
                        val start = element["start"] ?: fail("Required field is missing")
                        readPosition(requireObject(start))
                    },
                    end = at("end") {
                        element["end"]?.let { readPosition(requireObject(it)) }
                    },
                    duration = required(element, "duration", getLong(element, "duration"), "an integer"),
                )
            }

            private fun getStringIArray(jsonObject: JsonObject, key: String): List<String>? =
                at(key) { readStringIArray(jsonObject[key]) }

            private fun readStringIArray(element: JsonElement?): List<String>? = when (element) {
                JsonNull, null -> null
                is JsonObject -> fail("Expected a primitive or an array of primitives")
                is JsonArray -> element.mapIndexed { index, value ->
                    at(index) {
                        if (value == JsonNull || value !is JsonPrimitive) fail("Expected a non-null primitive")
                        value.content
                    }
                }
                is JsonPrimitive -> listOf(element.content)
            }

            private fun getIntMatcher(jsonObject: JsonObject, key: String): IntegerMatcher? = at(key) {
                when (val element = jsonObject[key]) {
                    JsonNull, null -> null
                    is JsonObject -> IntegerMatcher(
                        minimum = getInt(element, "minimum"),
                        maximum = getInt(element, "maximum"),
                        include = getIntIArray(element, "include"),
                        exclude = getIntIArray(element, "exclude"),
                    )
                    else -> fail("Expected an integer matcher object")
                }
            }

            private fun getStringMatcher(jsonObject: JsonObject, key: String): StringMatcher? = at(key) {
                when (val element = jsonObject[key]) {
                    JsonNull, null -> null
                    is JsonObject -> StringMatcher(
                        pattern = getString(element, "pattern"),
                        include = getStringIArray(element, "include"),
                        exclude = getStringIArray(element, "exclude"),
                    )
                    else -> fail("Expected a string matcher object")
                }
            }

            private fun getIntIArray(jsonObject: JsonObject, key: String): List<Int>? = at(key) {
                when (val element = jsonObject[key]) {
                    JsonNull, null -> null
                    is JsonArray -> element.mapIndexed { index, value -> at(index) { readInt(value) } }
                    is JsonPrimitive -> listOf(readInt(element))
                    else -> fail("Expected an integer or an array of integers")
                }
            }

            private fun readInt(element: JsonElement): Int =
                (element as? JsonPrimitive)?.intOrNull ?: fail("Expected an integer")

            private fun getString(jsonObject: JsonObject, key: String): String? {
                val value = jsonObject[key] as? JsonPrimitive
                return value?.takeIf { it.isString }?.content
            }

            private fun getLong(jsonObject: JsonObject, key: String): Long? = at(key) {
                when (val value = jsonObject[key]) {
                    JsonNull, null -> null
                    is JsonPrimitive -> value.longOrNull ?: fail("Expected an integer")
                    else -> null
                }
            }

            private fun getInt(jsonObject: JsonObject, key: String): Int? = at(key) {
                when (val value = jsonObject[key]) {
                    JsonNull, null -> null
                    is JsonPrimitive -> readInt(value)
                    else -> null
                }
            }

            private fun getBoolean(jsonObject: JsonObject, key: String): Boolean? = at(key) {
                when (val value = jsonObject[key]) {
                    JsonNull, null -> null
                    is JsonPrimitive -> value.booleanOrNull ?: fail("Expected a boolean")
                    else -> null
                }
            }

            private fun getCompatVersionCode(jsonObject: JsonObject): IntegerMatcher? {
                getIntMatcher(jsonObject, "versionCode")?.let { return it }
                // compat old value
                val a = getIntIArray(jsonObject, "versionCodes")
                val b = getIntIArray(jsonObject, "excludeVersionCodes")
                if (a != null || b != null) {
                    return IntegerMatcher(
                        minimum = null,
                        maximum = null,
                        include = a,
                        exclude = b
                    )
                }
                return null
            }

            private fun getCompatVersionName(jsonObject: JsonObject): StringMatcher? {
                getStringMatcher(jsonObject, "versionName")?.let { return it }
                // compat old value
                val a = getStringIArray(jsonObject, "versionNames")
                val b = getStringIArray(jsonObject, "excludeVersionNames")
                if (a != null || b != null) {
                    return StringMatcher(
                        pattern = null,
                        include = a,
                        exclude = b
                    )
                }
                return null
            }

            private fun jsonToRuleRaw(rulesRawJson: JsonElement): RawAppRule {
                val jsonObject = when (rulesRawJson) {
                    JsonNull -> fail("Rule must not be null")
                    is JsonObject -> rulesRawJson
                    is JsonPrimitive, is JsonArray -> JsonObject(emptyMap())
                }
                return RawAppRule(
                    activityIds = getStringIArray(jsonObject, "activityIds"),
                    excludeActivityIds = getStringIArray(jsonObject, "excludeActivityIds"),
                    matches = if (rulesRawJson is JsonObject) getStringIArray(jsonObject, "matches")
                        else readStringIArray(rulesRawJson),
                    excludeMatches = getStringIArray(jsonObject, "excludeMatches"),
                    excludeAllMatches = getStringIArray(jsonObject, "excludeAllMatches"),
                    anyMatches = getStringIArray(jsonObject, "anyMatches"),
                    key = getInt(jsonObject, "key"),
                    name = getString(jsonObject, "name"),
                    actionCd = getLong(jsonObject, "actionCd") ?: getLong(jsonObject, "cd"),
                    actionDelay = getLong(jsonObject, "actionDelay") ?: getLong(jsonObject, "delay"),
                    preKeys = getIntIArray(jsonObject, "preKeys"),
                    action = getString(jsonObject, "action"),
                    fastQuery = getBoolean(jsonObject, "fastQuery"),
                    matchRoot = getBoolean(jsonObject, "matchRoot"),
                    actionMaximum = getInt(jsonObject, "actionMaximum"),
                    matchDelay = getLong(jsonObject, "matchDelay"),
                    matchTime = getLong(jsonObject, "matchTime"),
                    resetMatch = getString(jsonObject, "resetMatch"),
                    snapshotUrls = getStringIArray(jsonObject, "snapshotUrls"),
                    excludeSnapshotUrls = getStringIArray(jsonObject, "excludeSnapshotUrls"),
                    exampleUrls = getStringIArray(jsonObject, "exampleUrls"),
                    actionMaximumKey = getInt(jsonObject, "actionMaximumKey"),
                    actionCdKey = getInt(jsonObject, "actionCdKey"),
                    order = getInt(jsonObject, "order"),
                    versionCode = getCompatVersionCode(jsonObject),
                    versionName = getCompatVersionName(jsonObject),
                    position = getPosition(jsonObject),
                    swipeArg = getSwipeArg(jsonObject),
                    forcedTime = getLong(jsonObject, "forcedTime"),
                    priorityTime = getLong(jsonObject, "priorityTime"),
                    priorityActionMaximum = getInt(jsonObject, "priorityActionMaximum"),
                )
            }


            fun jsonToGroupRaw(groupRawJson: JsonElement): RawAppGroup {
                val jsonObject = requireObject(groupRawJson)
                return RawAppGroup(
                    activityIds = getStringIArray(jsonObject, "activityIds"),
                    excludeActivityIds = getStringIArray(jsonObject, "excludeActivityIds"),
                    actionCd = getLong(jsonObject, "actionCd") ?: getLong(jsonObject, "cd"),
                    actionDelay = getLong(jsonObject, "actionDelay") ?: getLong(jsonObject, "delay"),
                    name = required(jsonObject, "name", getString(jsonObject, "name"), "a string"),
                    desc = getString(jsonObject, "desc"),
                    enable = getBoolean(jsonObject, "enable"),
                    key = required(jsonObject, "key", getInt(jsonObject, "key"), "an integer"),
                    rules = getFlexibleList(jsonObject, "rules", ::jsonToRuleRaw).distinctNotNullBy { it.key },
                    fastQuery = getBoolean(jsonObject, "fastQuery"),
                    matchRoot = getBoolean(jsonObject, "matchRoot"),
                    actionMaximum = getInt(jsonObject, "actionMaximum"),
                    matchDelay = getLong(jsonObject, "matchDelay"),
                    matchTime = getLong(jsonObject, "matchTime"),
                    resetMatch = getString(jsonObject, "resetMatch"),
                    snapshotUrls = getStringIArray(jsonObject, "snapshotUrls"),
                    excludeSnapshotUrls = getStringIArray(jsonObject, "excludeSnapshotUrls"),
                    exampleUrls = getStringIArray(jsonObject, "exampleUrls"),
                    actionMaximumKey = getInt(jsonObject, "actionMaximumKey"),
                    actionCdKey = getInt(jsonObject, "actionCdKey"),
                    order = getInt(jsonObject, "order"),
                    forcedTime = getLong(jsonObject, "forcedTime"),
                    scopeKeys = getIntIArray(jsonObject, "scopeKeys"),
                    versionCode = getCompatVersionCode(jsonObject),
                    versionName = getCompatVersionName(jsonObject),
                    priorityTime = getLong(jsonObject, "priorityTime"),
                    priorityActionMaximum = getInt(jsonObject, "priorityActionMaximum"),
                    ignoreGlobalGroupMatch = getBoolean(jsonObject, "ignoreGlobalGroupMatch"),
                )
            }

            fun jsonToAppRaw(jsonObject: JsonObject): RawApp {
                return RawApp(
                    id = required(jsonObject, "id", getString(jsonObject, "id"), "a string"),
                    name = getString(jsonObject, "name"),
                    groups = getFlexibleList(jsonObject, "groups", ::jsonToGroupRaw).distinctByIfAny { it.key },
                )
            }


            private fun jsonToGlobalApp(jsonObject: JsonObject): RawGlobalApp {
                return RawGlobalApp(
                    id = required(jsonObject, "id", getString(jsonObject, "id"), "a string"),
                    enable = getBoolean(jsonObject, "enable"),
                    activityIds = getStringIArray(jsonObject, "activityIds"),
                    excludeActivityIds = getStringIArray(jsonObject, "excludeActivityIds"),
                    versionCode = getCompatVersionCode(jsonObject),
                    versionName = getCompatVersionName(jsonObject),
                )
            }

            private fun jsonToGlobalRuleApp(jsonObject: JsonObject): RawGlobalRuleApp =
                RawGlobalRuleApp(
                    id = required(jsonObject, "id", getString(jsonObject, "id"), "a string"),
                    activityIds = getStringIArray(jsonObject, "activityIds"),
                    excludeActivityIds = getStringIArray(jsonObject, "excludeActivityIds"),
                    versionCode = getCompatVersionCode(jsonObject),
                    versionName = getCompatVersionName(jsonObject),
                )

            private fun jsonToGlobalRule(jsonObject: JsonObject): RawGlobalRule {
                return RawGlobalRule(
                    key = getInt(jsonObject, "key"),
                    name = getString(jsonObject, "name"),
                    actionCd = getLong(jsonObject, "actionCd"),
                    actionDelay = getLong(jsonObject, "actionDelay"),
                    fastQuery = getBoolean(jsonObject, "fastQuery"),
                    matchRoot = getBoolean(jsonObject, "matchRoot"),
                    actionMaximum = getInt(jsonObject, "actionMaximum"),
                    matchDelay = getLong(jsonObject, "matchDelay"),
                    matchTime = getLong(jsonObject, "matchTime"),
                    resetMatch = getString(jsonObject, "resetMatch"),
                    snapshotUrls = getStringIArray(jsonObject, "snapshotUrls"),
                    excludeSnapshotUrls = getStringIArray(jsonObject, "excludeSnapshotUrls"),
                    exampleUrls = getStringIArray(jsonObject, "exampleUrls"),
                    actionMaximumKey = getInt(jsonObject, "actionMaximumKey"),
                    actionCdKey = getInt(jsonObject, "actionCdKey"),
                    apps = getArray(jsonObject, "apps") { jsonElement ->
                        jsonToGlobalRuleApp(requireObject(jsonElement))
                    }?.distinctByIfAny { it.id },
                    action = getString(jsonObject, "action"),
                    preKeys = getIntIArray(jsonObject, "preKeys"),
                    excludeMatches = getStringIArray(jsonObject, "excludeMatches"),
                    excludeAllMatches = getStringIArray(jsonObject, "excludeAllMatches"),
                    matches = getStringIArray(jsonObject, "matches"),
                    anyMatches = getStringIArray(jsonObject, "anyMatches"),
                    order = getInt(jsonObject, "order"),
                    forcedTime = getLong(jsonObject, "forcedTime"),
                    position = getPosition(jsonObject),
                    swipeArg = getSwipeArg(jsonObject),
                    priorityTime = getLong(jsonObject, "priorityTime"),
                    priorityActionMaximum = getInt(jsonObject, "priorityActionMaximum"),
                )
            }

            fun jsonToGlobalGroup(jsonObject: JsonObject): RawGlobalGroup {
                return RawGlobalGroup(
                    key = required(jsonObject, "key", getInt(jsonObject, "key"), "an integer"),
                    name = required(jsonObject, "name", getString(jsonObject, "name"), "a string"),
                    desc = getString(jsonObject, "desc"),
                    enable = getBoolean(jsonObject, "enable"),
                    actionCd = getLong(jsonObject, "actionCd"),
                    actionDelay = getLong(jsonObject, "actionDelay"),
                    fastQuery = getBoolean(jsonObject, "fastQuery"),
                    matchRoot = getBoolean(jsonObject, "matchRoot"),
                    actionMaximum = getInt(jsonObject, "actionMaximum"),
                    matchDelay = getLong(jsonObject, "matchDelay"),
                    matchTime = getLong(jsonObject, "matchTime"),
                    resetMatch = getString(jsonObject, "resetMatch"),
                    snapshotUrls = getStringIArray(jsonObject, "snapshotUrls"),
                    excludeSnapshotUrls = getStringIArray(jsonObject, "excludeSnapshotUrls"),
                    exampleUrls = getStringIArray(jsonObject, "exampleUrls"),
                    actionMaximumKey = getInt(jsonObject, "actionMaximumKey"),
                    actionCdKey = getInt(jsonObject, "actionCdKey"),
                    matchSystemApp = getBoolean(jsonObject, "matchSystemApp"),
                    matchAnyApp = getBoolean(jsonObject, "matchAnyApp"),
                    matchLauncher = getBoolean(jsonObject, "matchLauncher"),
                    apps = getArray(jsonObject, "apps") { jsonElement ->
                        jsonToGlobalApp(requireObject(jsonElement))
                    }?.distinctByIfAny { it.id },
                    rules = (getArray(jsonObject, "rules") { jsonElement ->
                        jsonToGlobalRule(requireObject(jsonElement))
                    } ?: emptyList()).distinctNotNullBy { it.key },
                    order = getInt(jsonObject, "order"),
                    scopeKeys = getIntIArray(jsonObject, "scopeKeys"),
                    forcedTime = getLong(jsonObject, "forcedTime"),
                    priorityTime = getLong(jsonObject, "priorityTime"),
                    priorityActionMaximum = getInt(jsonObject, "priorityActionMaximum"),
                    disableIfAppGroupMatch = getString(jsonObject, "disableIfAppGroupMatch"),
                )
            }

            fun jsonToSubscriptionRaw(element: JsonElement): RawSubscription {
                val rootJson = requireObject(element)
                return RawSubscription(
                    id = required(rootJson, "id", getLong(rootJson, "id"), "an integer"),
                    name = required(rootJson, "name", getString(rootJson, "name"), "a string"),
                    version = required(rootJson, "version", getInt(rootJson, "version"), "an integer"),
                    author = getString(rootJson, "author"),
                    updateUrl = getString(rootJson, "updateUrl"),
                    supportUri = getString(rootJson, "supportUri"),
                    checkUpdateUrl = getString(rootJson, "checkUpdateUrl"),
                    apps = (getArray(rootJson, "apps") { jsonElement ->
                        jsonToAppRaw(requireObject(jsonElement))
                    } ?: emptyList()).filterIfNotAll { it.groups.isNotEmpty() }
                        .distinctByIfAny { it.id },
                    categories = (getArray(rootJson, "categories") { jsonElement ->
                        val category = requireObject(jsonElement)
                        RawCategory(
                            key = required(category, "key", getInt(category, "key"), "an integer"),
                            name = required(category, "name", getString(category, "name"), "a string"),
                            enable = getBoolean(category, "enable"),
                            desc = getString(category, "desc")
                        )
                    } ?: emptyList()).filterIfNotAll { it.name.isNotEmpty() }
                        .distinctByIfAny { it.key },
                    globalGroups = (getArray(rootJson, "globalGroups") { jsonElement ->
                        jsonToGlobalGroup(requireObject(jsonElement))
                    } ?: emptyList()).distinctByIfAny { it.key }
                )
            }

            private fun <T> List<T>.distinctNotNullBy(selector: (T) -> Any?): List<T> {
                val set = HashSet<Any>()
                val list = ArrayList<T>()
                forEach { e ->
                    val key = selector(e)
                    if (key == null || set.add(key)) {
                        list.add(e)
                    }
                }
                return list
            }
        }

        fun parse(source: String, json5: Boolean = true): RawSubscription {
            val element = try {
                if (json5) Json5.parseToJsonElement(source) else json.parseToJsonElement(source)
            } catch (e: Json5ParseException) {
                throw SubscriptionParseException("$", e.diagnostic.message, e)
            } catch (e: SerializationException) {
                throw SubscriptionParseException("$", e.message ?: "Invalid JSON", e)
            }
            return Parser().jsonToSubscriptionRaw(element)
        }

        fun parseApp(jsonObject: JsonObject): RawApp {
            return Parser().jsonToAppRaw(jsonObject)
        }

        fun parseAppGroup(jsonObject: JsonObject): RawAppGroup {
            return Parser().jsonToGroupRaw(jsonObject)
        }

        fun parseGlobalGroup(jsonObject: JsonObject): RawGlobalGroup {
            return Parser().jsonToGlobalGroup(jsonObject)
        }
    }
}

private inline fun <T> Iterable<T>.filterIfNotAll(predicate: (T) -> Boolean): List<T> =
    if (count() > 0 && !all(predicate)) filter(predicate) else this as? List<T> ?: toList()

private inline fun <T, K> Iterable<T>.distinctByIfAny(selector: (T) -> K): List<T> =
    if (count() > 1 && any { a -> any { b -> a !== b && selector(a) == selector(b) } }) distinctBy(
        selector
    )
    else this as? List<T> ?: toList()
