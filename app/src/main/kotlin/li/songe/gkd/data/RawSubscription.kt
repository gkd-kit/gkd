package li.songe.gkd.data

import android.graphics.Rect
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import li.songe.gkd.a11y.typeInfo
import li.songe.gkd.util.LOCAL_SUBS_IDS
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.ScreenUtils
import li.songe.gkd.util.appInfoMapFlow
import li.songe.gkd.util.distinctByIfAny
import li.songe.gkd.util.filterIfNotAll
import li.songe.gkd.util.json
import li.songe.gkd.util.toJson5String
import li.songe.gkd.util.toast
import li.songe.json5.Json5
import li.songe.selector.Selector
import net.objecthunter.exp4j.Expression
import net.objecthunter.exp4j.ExpressionBuilder
import java.util.Objects


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
        return Objects.hash(id, name, version)
    }

    val isEmpty: Boolean
        get() = globalGroups.isEmpty() && apps.all { it.groups.isEmpty() } && categories.isEmpty()

    val isLocal: Boolean
        get() = LOCAL_SUBS_IDS.contains(id)

    val hasRule get() = globalGroups.isNotEmpty() || apps.any { it.groups.isNotEmpty() }

    fun getSafeCategory(key: Int): RawCategory {
        return categories.find { it.key == key } ?: RawCategory(
            key = key,
            name = key.toString(),
            enable = false,
            desc = null
        )
    }

    private val categoryAppsMap: Map<Int, List<RawApp>> by lazy {
        val map = mutableMapOf<Int, MutableList<RawApp>>()
        val usedGroups = mutableListOf<RawAppGroup>()
        categories.forEach { c ->
            apps.forEach { a ->
                val subGroups = a.groups.filter { g ->
                    g.name.startsWith(c.name) && !usedGroups.any { g2 -> g2 === g }
                }
                if (subGroups.isNotEmpty()) {
                    usedGroups.addAll(subGroups)
                    val list = map[c.key]
                    val b = a.copy(groups = subGroups)
                    if (list == null) {
                        map[c.key] = mutableListOf(b)
                    } else {
                        list.add(b)
                    }
                }
            }
        }
        map
    }

    private val categoryGroupsMap: Map<Int, List<RawAppGroup>> by lazy {
        categoryAppsMap.mapValues { (key, apps) ->
            apps.flatMap { it.groups }
        }
    }

    fun getCategoryApps(categoryKey: Int): List<RawApp> {
        return categoryAppsMap[categoryKey] ?: emptyList()
    }

    fun getCategory(groupName: String): RawCategory? {
        return categories.find { c -> groupName.startsWith(c.name) }
    }

    fun getAppGroups(appId: String): List<RawAppGroup> {
        return apps.find { a -> a.id == appId }?.groups ?: emptyList()
    }

    fun getApp(appId: String): RawApp {
        return apps.find { a -> a.id == appId } ?: RawApp(
            id = appId,
            name = appInfoMapFlow.value[appId]?.name,
            groups = emptyList()
        )
    }

    fun getAppByGroup(group: RawAppGroup): RawApp {
        return apps.find { a -> a.groups.any { g -> g === group } }
            ?: throw IllegalStateException("App not found for group ${group.name}")
    }

    fun getCategoryCompatDesc(categoryKey: Int): String? {
        val c = getSafeCategory(categoryKey)
        if (!c.desc.isNullOrBlank()) return c.desc
        val groupSize = categoryGroupsMap[categoryKey]?.size ?: 0
        val appSize = categoryAppsMap[categoryKey]?.size ?: 0
        if (groupSize > 0) return "${appSize}应用/${groupSize}规则"
        return null
    }

    val appGroups by lazy {
        apps.flatMap { a -> a.groups }
    }

    val groupsSize by lazy {
        appGroups.size + globalGroups.size
    }

    val globalGroupAppGroupNameDisableMap by lazy {
        globalGroups.mapNotNull { g ->
            val n = g.disableIfAppGroupMatch
            if (n != null) {
                val gName = n.ifEmpty { g.name }
                g.key to apps.filter { a ->
                    a.groups.any { ag ->
                        ag.ignoreGlobalGroupMatch != true && ag.name.startsWith(gName)
                    }
                }.map { it.id }.toHashSet()
            } else {
                null
            }
        }.toMap()
    }

    fun getGlobalGroupInnerDisabled(globalGroup: RawGlobalGroup, appId: String): Boolean {
        globalGroup.appIdEnable[appId]?.let {
            if (!it) return true
        }
        globalGroupAppGroupNameDisableMap[globalGroup.key]?.let {
            if (it.contains(appId)) {
                return true
            }
        }
        return false
    }

    val numText by lazy {
        val appsSize = apps.size
        val appGroupsSize = appGroups.size
        val globalGroupSize = globalGroups.size
        if (appGroupsSize + globalGroupSize > 0) {
            if (globalGroupSize > 0) {
                "${globalGroupSize}全局" + if (appGroupsSize > 0) {
                    "/"
                } else {
                    ""
                }
            } else {
                ""
            } + if (appGroupsSize > 0) {
                "${appsSize}应用/${appGroupsSize}规则"
            } else {
                ""
            }
        } else {
            "暂无规则"
        }
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
        fun calc(rect: Rect): Pair<Float, Float>? {
            if (!isValid) return null
            xArr.forEach { setVariables(it, rect) }
            yArr.forEach { setVariables(it, rect) }
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
                e.printStackTrace()
                LogUtils.d("Position.calc", e)
                toast(e.message ?: e.stackTraceToString())
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
        val errorDesc: String?
        val allExampleUrls: List<String>
        val cacheMap: MutableMap<String, Selector?>
        val cacheStr: String
        val cacheJsonObject: JsonObject

        val groupType: Int
            get() = when (this) {
                is RawAppGroup -> SubsConfig.AppGroupType
                is RawGlobalGroup -> SubsConfig.GlobalGroupType
            }
    }

    sealed interface RawAppRuleProps {
        val activityIds: List<String>?
        val excludeActivityIds: List<String>?

        val versionCode: IntegerMatcher?
        val versionName: StringMatcher?
    }

    sealed interface RawGlobalRuleProps {
        val matchAnyApp: Boolean?
        val matchSystemApp: Boolean?
        val matchLauncher: Boolean?
        val apps: List<RawGlobalApp>?
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
        override val matchAnyApp: Boolean?,
        override val matchSystemApp: Boolean?,
        override val matchLauncher: Boolean?,
        val disableIfAppGroupMatch: String?,
        override val rules: List<RawGlobalRule>,
        override val apps: List<RawGlobalApp>?,
    ) : RawGroupProps, RawGlobalRuleProps {
        val appIdEnable: Map<String, Boolean> by lazy {
            if (rules.all { r -> r.apps.isNullOrEmpty() }) {
                apps?.associate { a -> a.id to (a.enable ?: true) } ?: emptyMap()
            } else {
                val allIds = mutableSetOf<String>()
                apps?.forEach { a ->
                    allIds.add(a.id)
                }
                rules.forEach { r ->
                    r.apps?.forEach { a ->
                        allIds.add(a.id)
                    }
                }
                val dataMap = mutableMapOf<String, Boolean>()
                allIds.forEach forEachId@{ id ->
                    var temp: Boolean? = null
                    rules.forEach { r ->
                        val v = (r.apps ?: apps)?.find { it.id == id }?.enable ?: return@forEachId
                        if (temp == null) {
                            temp = v
                        } else if (temp != v) {
                            return@forEachId
                        }
                    }
                    if (temp != null) {
                        dataMap[id] = temp
                    }
                }
                dataMap
            }
        }

        override val cacheMap by lazy { HashMap<String, Selector?>() }
        override val errorDesc by lazy { getErrorDesc() }
        override val valid by lazy { errorDesc == null }
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
        override val matchAnyApp: Boolean?,
        override val matchSystemApp: Boolean?,
        override val matchLauncher: Boolean?,
        override val apps: List<RawGlobalApp>?
    ) : RawRuleProps, RawGlobalRuleProps

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
        override val errorDesc by lazy { getErrorDesc() }
        override val valid by lazy { errorDesc == null }
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

        private fun RawGroupProps.getErrorDesc(): String? {
            val allSelectorStrings = rules.flatMap { r ->
                r.getAllSelectorStrings()
            }
            allSelectorStrings.forEach { source ->
                try {
                    val selector = Selector.parse(source)
                    selector.checkType(typeInfo)
                    cacheMap[source] = selector
                } catch (e: Exception) {
                    LogUtils.d("非法选择器", source, e.toString())
                    return "非法选择器\n$source\n${e.message}"
                }
            }
            rules.forEach { r ->
                if (r.position?.isValid == false) {
                    return "非法位置:${r.position}"
                }
            }
            return null
        }

        private val expVars = arrayOf(
            "left",
            "top",
            "right",
            "bottom",
            "width",
            "height",
            "random"
        )

        private fun setVariables(exp: Expression?, rect: Rect) {
            if (exp == null) return
            exp.setVariable("left", rect.left.toDouble())
            exp.setVariable("top", rect.top.toDouble())
            exp.setVariable("right", rect.right.toDouble())
            exp.setVariable("bottom", rect.bottom.toDouble())
            exp.setVariable("width", rect.width().toDouble())
            exp.setVariable("height", rect.height().toDouble())
            exp.setVariable("random", Math.random())
            exp.setVariable("screenWidth", ScreenUtils.getScreenWidth().toDouble())
            exp.setVariable("screenHeight", ScreenUtils.getScreenHeight().toDouble())
        }

        private fun getExpression(value: String?): Expression? {
            return if (value != null) {
                try {
                    ExpressionBuilder(value).variables(*expVars).build().apply {
                        expVars.forEach { v ->
                            // 预填充作 validate
                            setVariable(v, 0.0)
                        }
                    }.let { e ->
                        if (e.validate().isValid) {
                            e
                        } else {
                            null
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            } else {
                null
            }
        }

        private fun getPosition(jsonObject: JsonObject?, useSelf: Boolean = false): Position? {
            return when (val element = if (useSelf) jsonObject else jsonObject?.get("position")) {
                is JsonObject -> Position(
                    left = element["left"]?.jsonPrimitive?.content,
                    bottom = element["bottom"]?.jsonPrimitive?.content,
                    top = element["top"]?.jsonPrimitive?.content,
                    right = element["right"]?.jsonPrimitive?.content,
                    x = element["x"]?.jsonPrimitive?.content,
                    y = element["y"]?.jsonPrimitive?.content,
                )

                else -> null
            }
        }

        private fun getSwipeArg(
            jsonObject: JsonObject?
        ): SwipeArg? = when (val element = jsonObject?.get("swipeArg")) {
            is JsonObject -> {
                SwipeArg(
                    start = getPosition(element["start"]?.jsonObject, true)
                        ?: error("swipe start position is required"),
                    end = getPosition(element["end"]?.jsonObject, true),
                    duration = element["duration"]?.jsonPrimitive?.long
                        ?: error("swipe duration is required"),
                )
            }

            else -> null
        }

        private fun getStringIArray(jsonObject: JsonObject?, name: String): List<String>? {
            return when (val element = jsonObject?.get(name)) {
                JsonNull, null -> null
                is JsonObject -> error("Element ${this::class} can not be object")
                is JsonArray -> element.map {
                    when (it) {
                        is JsonObject, is JsonArray, JsonNull -> error("Element ${this::class} is not a int")
                        is JsonPrimitive -> it.content
                    }
                }

                is JsonPrimitive -> listOf(element.content)
            }
        }

        @Suppress("SameParameterValue")
        private fun getIntMatcher(jsonObject: JsonObject?, key: String): IntegerMatcher? {
            return when (val element = jsonObject?.get(key)) {
                JsonNull, null -> null
                is JsonObject -> IntegerMatcher(
                    minimum = getInt(element, "minimum"),
                    maximum = getInt(element, "maximum"),
                    include = getIntIArray(element, "include"),
                    exclude = getIntIArray(element, "exclude"),
                )

                else -> error("Element $element is not a IntMatcher")
            }
        }

        @Suppress("SameParameterValue")
        private fun getStringMatcher(jsonObject: JsonObject?, key: String): StringMatcher? {
            return when (val element = jsonObject?.get(key)) {
                JsonNull, null -> null
                is JsonObject -> StringMatcher(
                    pattern = getString(element, "pattern"),
                    include = getStringIArray(element, "include"),
                    exclude = getStringIArray(element, "exclude"),
                )

                else -> error("Element $element is not a StringMatcher")
            }
        }

        private fun getIntIArray(jsonObject: JsonObject?, key: String): List<Int>? {
            return when (val element = jsonObject?.get(key)) {
                JsonNull, null -> null
                is JsonArray -> element.map {
                    when (it) {
                        is JsonObject, is JsonArray, JsonNull -> error("Element $it is not a int")
                        is JsonPrimitive -> it.int
                    }
                }

                is JsonPrimitive -> listOf(element.int)
                else -> error("Element $element is not a Array")
            }
        }

        private fun getString(jsonObject: JsonObject?, key: String): String? =
            when (val p = jsonObject?.get(key)) {
                JsonNull, null -> null
                is JsonPrimitive -> {
                    if (p.isString) {
                        p.content
                    } else {
                        null
                    }
                }

                else -> null
            }

        private fun getLong(jsonObject: JsonObject?, key: String): Long? =
            when (val p = jsonObject?.get(key)) {
                JsonNull, null -> null
                is JsonPrimitive -> {
                    p.long
                }

                else -> null
            }

        private fun getInt(jsonObject: JsonObject?, key: String): Int? =
            when (val p = jsonObject?.get(key)) {
                JsonNull, null -> null
                is JsonPrimitive -> {
                    p.int
                }

                else -> null
            }

        private fun getBoolean(jsonObject: JsonObject?, key: String): Boolean? =
            when (val p = jsonObject?.get(key)) {
                JsonNull, null -> null
                is JsonPrimitive -> {
                    p.boolean
                }

                else -> null
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
                JsonNull -> error("miss current rule")
                is JsonObject -> rulesRawJson
                is JsonPrimitive, is JsonArray -> JsonObject(mapOf("matches" to rulesRawJson))
            }
            return RawAppRule(
                activityIds = getStringIArray(jsonObject, "activityIds"),
                excludeActivityIds = getStringIArray(jsonObject, "excludeActivityIds"),
                matches = getStringIArray(jsonObject, "matches"),
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


        private fun jsonToGroupRaw(groupRawJson: JsonElement): RawAppGroup {
            val jsonObject = when (groupRawJson) {
                JsonNull -> error("group must not be null")
                is JsonObject -> groupRawJson
                is JsonPrimitive, is JsonArray -> JsonObject(mapOf("rules" to groupRawJson))
            }
            return RawAppGroup(
                activityIds = getStringIArray(jsonObject, "activityIds"),
                excludeActivityIds = getStringIArray(jsonObject, "excludeActivityIds"),
                actionCd = getLong(jsonObject, "actionCd") ?: getLong(jsonObject, "cd"),
                actionDelay = getLong(jsonObject, "actionDelay") ?: getLong(jsonObject, "delay"),
                name = getString(jsonObject, "name") ?: error("miss group name"),
                desc = getString(jsonObject, "desc"),
                enable = getBoolean(jsonObject, "enable"),
                key = getInt(jsonObject, "key") ?: error("miss group key"),
                rules = when (val rulesJson = jsonObject["rules"]) {
                    null, JsonNull -> emptyList()
                    is JsonPrimitive, is JsonObject -> JsonArray(listOf(rulesJson))
                    is JsonArray -> rulesJson
                }.map {
                    jsonToRuleRaw(it)
                }.distinctNotNullBy { it.key },
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

        private fun jsonToAppRaw(jsonObject: JsonObject, appIndex: Int? = null): RawApp {
            return RawApp(
                id = getString(jsonObject, "id") ?: error(
                    if (appIndex != null) {
                        "miss subscription.apps[$appIndex].id"
                    } else {
                        "miss id"
                    }
                ),
                name = getString(jsonObject, "name"),
                groups = (when (val groupsJson = jsonObject["groups"]) {
                    null, JsonNull -> emptyList()
                    is JsonPrimitive, is JsonObject -> JsonArray(listOf(groupsJson))
                    is JsonArray -> groupsJson
                }).map { jsonElement ->
                    jsonToGroupRaw(jsonElement)
                }.distinctByIfAny { it.key },
            )
        }


        private fun jsonToGlobalApp(jsonObject: JsonObject, index: Int): RawGlobalApp {
            return RawGlobalApp(
                id = getString(jsonObject, "id") ?: error("miss apps[$index].id"),
                enable = getBoolean(jsonObject, "enable"),
                activityIds = getStringIArray(jsonObject, "activityIds"),
                excludeActivityIds = getStringIArray(jsonObject, "excludeActivityIds"),
                versionCode = getCompatVersionCode(jsonObject),
                versionName = getCompatVersionName(jsonObject),
            )
        }

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
                matchAnyApp = getBoolean(jsonObject, "matchAnyApp"),
                matchSystemApp = getBoolean(jsonObject, "matchSystemApp"),
                matchLauncher = getBoolean(jsonObject, "matchLauncher"),
                apps = jsonObject["apps"]?.jsonArray?.mapIndexed { index, jsonElement ->
                    jsonToGlobalApp(
                        jsonElement.jsonObject, index
                    )
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

        private fun jsonToGlobalGroup(jsonObject: JsonObject, groupIndex: Int): RawGlobalGroup {
            return RawGlobalGroup(
                key = getInt(jsonObject, "key") ?: error("miss group[$groupIndex].key"),
                name = getString(jsonObject, "name") ?: error("miss group[$groupIndex].name"),
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
                apps = jsonObject["apps"]?.jsonArray?.mapIndexed { index, jsonElement ->
                    jsonToGlobalApp(
                        jsonElement.jsonObject, index
                    )
                }?.distinctByIfAny { it.id },
                rules = (jsonObject["rules"]?.jsonArray?.map { jsonElement ->
                    jsonToGlobalRule(jsonElement.jsonObject)
                } ?: emptyList()).distinctNotNullBy { it.key },
                order = getInt(jsonObject, "order"),
                scopeKeys = getIntIArray(jsonObject, "scopeKeys"),
                forcedTime = getLong(jsonObject, "forcedTime"),
                priorityTime = getLong(jsonObject, "priorityTime"),
                priorityActionMaximum = getInt(jsonObject, "priorityActionMaximum"),
                disableIfAppGroupMatch = getString(jsonObject, "disableIfAppGroupMatch"),
            )
        }

        private fun jsonToSubscriptionRaw(rootJson: JsonObject): RawSubscription {
            return RawSubscription(
                id = getLong(rootJson, "id") ?: error("miss subscription.id"),
                name = getString(rootJson, "name") ?: error("miss subscription.name"),
                version = getInt(rootJson, "version") ?: error("miss subscription.version"),
                author = getString(rootJson, "author"),
                updateUrl = getString(rootJson, "updateUrl"),
                supportUri = getString(rootJson, "supportUri"),
                checkUpdateUrl = getString(rootJson, "checkUpdateUrl"),
                apps = (rootJson["apps"]?.jsonArray?.mapIndexed { index, jsonElement ->
                    jsonToAppRaw(
                        jsonElement.jsonObject,
                        index
                    )
                } ?: emptyList()).filterIfNotAll { it.groups.isNotEmpty() }
                    .distinctByIfAny { it.id },
                categories = (rootJson["categories"]?.jsonArray?.mapIndexed { index, jsonElement ->
                    RawCategory(
                        key = getInt(jsonElement.jsonObject, "key")
                            ?: error("miss categories[$index].key"),
                        name = getString(jsonElement.jsonObject, "name")
                            ?: error("miss categories[$index].name"),
                        enable = getBoolean(jsonElement.jsonObject, "enable"),
                        desc = getString(jsonElement.jsonObject, "desc")
                    )
                } ?: emptyList()).filterIfNotAll { it.name.isNotEmpty() }
                    .distinctByIfAny { it.key },
                globalGroups = (rootJson["globalGroups"]?.jsonArray?.mapIndexed { index, jsonElement ->
                    jsonToGlobalGroup(jsonElement.jsonObject, index)
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

        fun parse(source: String, json5: Boolean = true): RawSubscription {
            val element = if (json5) {
                Json5.parseToJson5Element(source)
            } else {
                json.parseToJsonElement(source)
            }
            return jsonToSubscriptionRaw(element.jsonObject)
        }

        fun parseApp(jsonObject: JsonObject): RawApp {
            return jsonToAppRaw(jsonObject)
        }

        fun parseAppGroup(jsonObject: JsonObject): RawAppGroup {
            return jsonToGroupRaw(jsonObject)
        }

        fun parseGlobalGroup(jsonObject: JsonObject): RawGlobalGroup {
            return jsonToGlobalGroup(jsonObject, 0)
        }
    }
}
