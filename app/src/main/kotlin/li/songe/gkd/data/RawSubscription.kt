package li.songe.gkd.data

import android.graphics.Rect
import com.blankj.utilcode.util.LogUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import li.songe.gkd.service.checkSelector
import li.songe.gkd.util.json
import li.songe.gkd.util.json5ToJson
import li.songe.gkd.util.toast
import li.songe.selector.Selector
import net.objecthunter.exp4j.Expression
import net.objecthunter.exp4j.ExpressionBuilder


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

    val categoryToGroupsMap by lazy {
        val allAppGroups = apps.flatMap { a -> a.groups.map { g -> g to a } }
        allAppGroups.groupBy { g ->
            categories.find { c -> g.first.name.startsWith(c.name) }
        }
    }

    val categoryToAppMap by lazy {
        val map = mutableMapOf<RawCategory, MutableList<RawApp>>()
        categories.forEach { c ->
            apps.forEach { a ->
                if (a.groups.any { g -> g.name.startsWith(c.name) }) {
                    val list = map[c]
                    if (list == null) {
                        map[c] = mutableListOf(a)
                    } else {
                        list.add(a)
                    }
                }
            }
        }
        map
    }

    val groupToCategoryMap by lazy {
        val map = mutableMapOf<RawAppGroup, RawCategory>()
        categoryToGroupsMap.forEach { (key, value) ->
            value.forEach { (g) ->
                if (key != null) {
                    map[g] = key
                }
            }
        }
        map
    }

    private val appGroups by lazy {
        apps.flatMap { a -> a.groups }
    }

    val groupsSize by lazy {
        appGroups.size + globalGroups.size
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
                "${appsSize}应用/${appGroupsSize}规则组"
            } else {
                ""
            }
        } else {
            "暂无规则"
        }
    }


    @Serializable
    data class RawApp(
        val id: String,
        val name: String?,
        val groups: List<RawAppGroup> = emptyList(),
    )


    @Serializable
    data class RawCategory(val key: Int, val name: String, val enable: Boolean?)


    @Serializable
    data class Position(
        val left: String?, val top: String?, val right: String?, val bottom: String?
    ) {
        private val leftExp by lazy { getExpression(left) }
        private val topExp by lazy { getExpression(top) }
        private val rightExp by lazy { getExpression(right) }
        private val bottomExp by lazy { getExpression(bottom) }

        val isValid by lazy {
            ((leftExp != null && (topExp != null || bottomExp != null)) || (rightExp != null && (topExp != null || bottomExp != null)))
        }

        /**
         * return (x, y)
         */
        fun calc(rect: Rect): Pair<Float, Float>? {
            if (!isValid) return null
            arrayOf(
                leftExp, topExp, rightExp, bottomExp
            ).forEach { exp ->
                if (exp != null) {
                    setVariables(exp, rect)
                }
            }
            try {
                if (leftExp != null) {
                    if (topExp != null) {
                        return (rect.left + leftExp!!.evaluate()
                            .toFloat()) to (rect.top + topExp!!.evaluate().toFloat())
                    }
                    if (bottomExp != null) {
                        return (rect.left + leftExp!!.evaluate()
                            .toFloat()) to (rect.bottom - bottomExp!!.evaluate().toFloat())
                    }
                } else if (rightExp != null) {
                    if (topExp != null) {
                        return (rect.right - rightExp!!.evaluate()
                            .toFloat()) to (rect.top + topExp!!.evaluate().toFloat())
                    }
                    if (bottomExp != null) {
                        return (rect.right - rightExp!!.evaluate()
                            .toFloat()) to (rect.bottom - bottomExp!!.evaluate().toFloat())
                    }
                }
            } catch (e: Exception) {
                // 可能存在 1/0 导致错误
                e.printStackTrace()
                LogUtils.d(e)
                toast(e.message ?: e.stackTraceToString())
            }
            return null
        }
    }


    sealed interface RawCommonProps {
        val actionCd: Long?
        val actionDelay: Long?
        val quickFind: Boolean?
        val matchDelay: Long?
        val matchTime: Long?
        val actionMaximum: Int?
        val resetMatch: String?
        val actionCdKey: Int?
        val actionMaximumKey: Int?
        val order: Int?
        val forcedTime: Long?
        val snapshotUrls: List<String>?
        val exampleUrls: List<String>?
    }

    sealed interface RawRuleProps : RawCommonProps {
        val name: String?
        val key: Int?
        val preKeys: List<Int>?
        val action: String?
        val position: Position?
        val matches: List<String>?
        val excludeMatches: List<String>?
        val anyMatches: List<String>?
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
    }

    sealed interface RawAppRuleProps {
        val activityIds: List<String>?
        val excludeActivityIds: List<String>?

        val versionNames: List<String>?
        val excludeVersionNames: List<String>?
        val versionCodes: List<Long>?
        val excludeVersionCodes: List<Long>?
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
        override val versionNames: List<String>?,
        override val excludeVersionNames: List<String>?,
        override val versionCodes: List<Long>?,
        override val excludeVersionCodes: List<Long>?,
    ) : RawAppRuleProps


    @Serializable
    data class RawGlobalGroup(
        override val name: String,
        override val key: Int,
        override val desc: String?,
        override val enable: Boolean?,
        override val scopeKeys: List<Int>?,
        override val actionCd: Long?,
        override val actionDelay: Long?,
        override val quickFind: Boolean?,
        override val matchDelay: Long?,
        override val matchTime: Long?,
        override val actionMaximum: Int?,
        override val resetMatch: String?,
        override val actionCdKey: Int?,
        override val actionMaximumKey: Int?,
        override val order: Int?,
        override val forcedTime: Long?,
        override val snapshotUrls: List<String>?,
        override val exampleUrls: List<String>?,
        override val matchAnyApp: Boolean?,
        override val matchSystemApp: Boolean?,
        override val matchLauncher: Boolean?,
        override val apps: List<RawGlobalApp>?,
        override val rules: List<RawGlobalRule>,
    ) : RawGroupProps, RawGlobalRuleProps {

        val appIdEnable by lazy {
            (apps ?: emptyList()).associate { a -> a.id to (a.enable ?: true) }
        }

        override val errorDesc by lazy { getErrorDesc() }

        override val valid by lazy { errorDesc == null }

        override val allExampleUrls by lazy {
            ((exampleUrls ?: emptyList()) + rules.flatMap { r ->
                r.exampleUrls ?: emptyList()
            }).distinct()
        }
    }


    @Serializable
    data class RawGlobalRule(
        override val actionCd: Long?,
        override val actionDelay: Long?,
        override val quickFind: Boolean?,
        override val matchDelay: Long?,
        override val matchTime: Long?,
        override val actionMaximum: Int?,
        override val resetMatch: String?,
        override val actionCdKey: Int?,
        override val actionMaximumKey: Int?,
        override val order: Int?,
        override val forcedTime: Long?,
        override val snapshotUrls: List<String>?,
        override val exampleUrls: List<String>?,
        override val name: String?,
        override val key: Int?,
        override val preKeys: List<Int>?,
        override val action: String?,
        override val position: Position?,
        override val matches: List<String>?,
        override val excludeMatches: List<String>?,
        override val anyMatches: List<String>?,
        override val matchAnyApp: Boolean?,
        override val matchSystemApp: Boolean?,
        override val matchLauncher: Boolean?,
        override val apps: List<RawGlobalApp>?
    ) : RawRuleProps, RawGlobalRuleProps

    @Serializable
    data class RawAppGroup(
        override val name: String,
        override val key: Int,
        override val desc: String?,
        override val enable: Boolean?,
        override val scopeKeys: List<Int>?,
        override val actionCdKey: Int?,
        override val actionMaximumKey: Int?,
        override val actionCd: Long?,
        override val actionDelay: Long?,
        override val quickFind: Boolean?,
        override val actionMaximum: Int?,
        override val order: Int?,
        override val forcedTime: Long?,
        override val matchDelay: Long?,
        override val matchTime: Long?,
        override val resetMatch: String?,
        override val snapshotUrls: List<String>?,
        override val exampleUrls: List<String>?,
        override val activityIds: List<String>?,
        override val excludeActivityIds: List<String>?,
        override val rules: List<RawAppRule>,
        override val versionNames: List<String>?,
        override val excludeVersionNames: List<String>?,
        override val versionCodes: List<Long>?,
        override val excludeVersionCodes: List<Long>?,
    ) : RawGroupProps, RawAppRuleProps {

        override val errorDesc by lazy { getErrorDesc() }

        override val valid by lazy { errorDesc == null }

        override val allExampleUrls by lazy {
            ((exampleUrls ?: emptyList()) + rules.flatMap { r ->
                r.exampleUrls ?: emptyList()
            }).distinct()
        }
    }

    @Serializable
    data class RawAppRule(
        override val name: String?,
        override val key: Int?,
        override val preKeys: List<Int>?,
        override val action: String?,
        override val position: Position?,
        override val matches: List<String>?,
        override val excludeMatches: List<String>?,
        override val anyMatches: List<String>?,

        override val actionCdKey: Int?,
        override val actionMaximumKey: Int?,
        override val actionCd: Long?,
        override val actionDelay: Long?,
        override val quickFind: Boolean?,
        override val actionMaximum: Int?,
        override val order: Int?,
        override val forcedTime: Long?,
        override val matchDelay: Long?,
        override val matchTime: Long?,
        override val resetMatch: String?,
        override val snapshotUrls: List<String>?,
        override val exampleUrls: List<String>?,

        override val activityIds: List<String>?,
        override val excludeActivityIds: List<String>?,

        override val versionNames: List<String>?,
        override val excludeVersionNames: List<String>?,
        override val versionCodes: List<Long>?,
        override val excludeVersionCodes: List<Long>?,
    ) : RawRuleProps, RawAppRuleProps

    companion object {

        private fun RawGroupProps.getErrorDesc(): String? {
            val allSelectorStrings = rules.map { r ->
                listOfNotNull(r.matches, r.excludeMatches, r.anyMatches).flatten()
            }.flatten()

            val allSelector = allSelectorStrings.map { s ->
                try {
                    Selector.parse(s)
                } catch (e: Exception) {
                    LogUtils.d("非法选择器", e.toString())
                    null
                }
            }

            allSelector.forEachIndexed { i, s ->
                if (s == null) {
                    return "非法选择器:${allSelectorStrings[i]}"
                }
            }
            allSelector.forEach { s ->
                s?.checkSelector()?.let { return it }
            }
            rules.forEach { r ->
                if (r.position?.isValid == false) {
                    return "非法位置:${r.position}"
                }
            }
            return null
        }

        private val expVars = arrayOf(
            "left", "top", "right", "bottom", "width", "height"
        )

        private fun setVariables(exp: Expression, rect: Rect) {
            exp.setVariable("left", rect.left.toDouble())
            exp.setVariable("top", rect.top.toDouble())
            exp.setVariable("right", rect.right.toDouble())
            exp.setVariable("bottom", rect.bottom.toDouble())
            exp.setVariable("width", rect.width().toDouble())
            exp.setVariable("height", rect.height().toDouble())
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

        private fun getPosition(jsonObject: JsonObject? = null): Position? {
            return when (val element = jsonObject?.get("position")) {
                JsonNull, null -> null
                is JsonObject -> {
                    Position(
                        left = element["left"]?.jsonPrimitive?.content,
                        bottom = element["bottom"]?.jsonPrimitive?.content,
                        top = element["top"]?.jsonPrimitive?.content,
                        right = element["right"]?.jsonPrimitive?.content,
                    )
                }

                else -> null
            }
        }

        private fun getStringIArray(
            jsonObject: JsonObject? = null, name: String
        ): List<String>? {
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

        private fun getIntIArray(jsonObject: JsonObject? = null, name: String): List<Int>? {
            return when (val element = jsonObject?.get(name)) {
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

        private fun getLongIArray(jsonObject: JsonObject? = null, name: String): List<Long>? {
            return when (val element = jsonObject?.get(name)) {
                JsonNull, null -> null
                is JsonArray -> element.map {
                    when (it) {
                        is JsonObject, is JsonArray, JsonNull -> error("Element $it is not a int")
                        is JsonPrimitive -> it.long
                    }
                }

                is JsonPrimitive -> listOf(element.long)
                else -> error("Element $element is not a Array")
            }
        }

        private fun getString(jsonObject: JsonObject? = null, key: String): String? =
            when (val p = jsonObject?.get(key)) {
                JsonNull, null -> null
                is JsonPrimitive -> {
                    if (p.isString) {
                        p.content
                    } else {
                        error("Element $p is not a string")
                    }
                }

                else -> error("Element $p is not a string")
            }

        private fun getLong(jsonObject: JsonObject? = null, key: String): Long? =
            when (val p = jsonObject?.get(key)) {
                JsonNull, null -> null
                is JsonPrimitive -> {
                    p.long
                }

                else -> error("Element $p is not a long")
            }

        private fun getInt(jsonObject: JsonObject? = null, key: String): Int? =
            when (val p = jsonObject?.get(key)) {
                JsonNull, null -> null
                is JsonPrimitive -> {
                    p.int
                }

                else -> error("Element $p is not a int")
            }

        private fun getBoolean(jsonObject: JsonObject? = null, key: String): Boolean? =
            when (val p = jsonObject?.get(key)) {
                JsonNull, null -> null
                is JsonPrimitive -> {
                    p.boolean
                }

                else -> error("Element $p is not a boolean")
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
                anyMatches = getStringIArray(jsonObject, "anyMatches"),
                key = getInt(jsonObject, "key"),
                name = getString(jsonObject, "name"),
                actionCd = getLong(jsonObject, "actionCd") ?: getLong(jsonObject, "cd"),
                actionDelay = getLong(jsonObject, "actionDelay") ?: getLong(jsonObject, "delay"),
                preKeys = getIntIArray(jsonObject, "preKeys"),
                action = getString(jsonObject, "action"),
                quickFind = getBoolean(jsonObject, "quickFind"),
                actionMaximum = getInt(jsonObject, "actionMaximum"),
                matchDelay = getLong(jsonObject, "matchDelay"),
                matchTime = getLong(jsonObject, "matchTime"),
                resetMatch = getString(jsonObject, "resetMatch"),
                snapshotUrls = getStringIArray(jsonObject, "snapshotUrls"),
                exampleUrls = getStringIArray(jsonObject, "exampleUrls"),
                actionMaximumKey = getInt(jsonObject, "actionMaximumKey"),
                actionCdKey = getInt(jsonObject, "actionCdKey"),
                order = getInt(jsonObject, "order"),
                versionCodes = getLongIArray(jsonObject, "versionCodes"),
                excludeVersionCodes = getLongIArray(jsonObject, "excludeVersionCodes"),
                versionNames = getStringIArray(jsonObject, "versionNames"),
                excludeVersionNames = getStringIArray(jsonObject, "excludeVersionNames"),
                position = getPosition(jsonObject),
                forcedTime = getLong(jsonObject, "forcedTime"),
            )
        }


        private fun jsonToGroupRaw(groupRawJson: JsonElement, groupIndex: Int): RawAppGroup {
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
                key = getInt(jsonObject, "key") ?: groupIndex,
                rules = when (val rulesJson = jsonObject["rules"]) {
                    null, JsonNull -> emptyList()
                    is JsonPrimitive, is JsonObject -> JsonArray(listOf(rulesJson))
                    is JsonArray -> rulesJson
                }.map {
                    jsonToRuleRaw(it)
                },
                quickFind = getBoolean(jsonObject, "quickFind"),
                actionMaximum = getInt(jsonObject, "actionMaximum"),
                matchDelay = getLong(jsonObject, "matchDelay"),
                matchTime = getLong(jsonObject, "matchTime"),
                resetMatch = getString(jsonObject, "resetMatch"),
                snapshotUrls = getStringIArray(jsonObject, "snapshotUrls"),
                exampleUrls = getStringIArray(jsonObject, "exampleUrls"),
                actionMaximumKey = getInt(jsonObject, "actionMaximumKey"),
                actionCdKey = getInt(jsonObject, "actionCdKey"),
                order = getInt(jsonObject, "order"),
                forcedTime = getLong(jsonObject, "forcedTime"),
                scopeKeys = getIntIArray(jsonObject, "scopeKeys"),
                versionCodes = getLongIArray(jsonObject, "versionCodes"),
                excludeVersionCodes = getLongIArray(jsonObject, "excludeVersionCodes"),
                versionNames = getStringIArray(jsonObject, "versionNames"),
                excludeVersionNames = getStringIArray(jsonObject, "excludeVersionNames"),
            )
        }

        private fun jsonToAppRaw(jsonObject: JsonObject, appIndex: Int): RawApp {
            return RawApp(
                id = getString(jsonObject, "id") ?: error("miss subscription.apps[$appIndex].id"),
                name = getString(jsonObject, "name"),
                groups = (when (val groupsJson = jsonObject["groups"]) {
                    null, JsonNull -> emptyList()
                    is JsonPrimitive, is JsonObject -> JsonArray(listOf(groupsJson))
                    is JsonArray -> groupsJson
                }).mapIndexed { index, jsonElement ->
                    jsonToGroupRaw(jsonElement, index)
                },
            )
        }


        private fun jsonToGlobalApp(jsonObject: JsonObject, index: Int): RawGlobalApp {
            return RawGlobalApp(
                id = getString(jsonObject, "id") ?: error("miss apps[$index].id"),
                enable = getBoolean(jsonObject, "enable"),
                activityIds = getStringIArray(jsonObject, "activityIds"),
                excludeActivityIds = getStringIArray(jsonObject, "excludeActivityIds"),
                versionCodes = getLongIArray(jsonObject, "versionCodes"),
                excludeVersionCodes = getLongIArray(jsonObject, "excludeVersionCodes"),
                versionNames = getStringIArray(jsonObject, "versionNames"),
                excludeVersionNames = getStringIArray(jsonObject, "excludeVersionNames"),
            )
        }

        private fun jsonToGlobalRule(jsonObject: JsonObject): RawGlobalRule {
            return RawGlobalRule(
                key = getInt(jsonObject, "key"),
                name = getString(jsonObject, "name"),
                actionCd = getLong(jsonObject, "actionCd"),
                actionDelay = getLong(jsonObject, "actionDelay"),
                quickFind = getBoolean(jsonObject, "quickFind"),
                actionMaximum = getInt(jsonObject, "actionMaximum"),
                matchDelay = getLong(jsonObject, "matchDelay"),
                matchTime = getLong(jsonObject, "matchTime"),
                resetMatch = getString(jsonObject, "resetMatch"),
                snapshotUrls = getStringIArray(jsonObject, "snapshotUrls"),
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
                },
                action = getString(jsonObject, "action"),
                preKeys = getIntIArray(jsonObject, "preKeys"),
                excludeMatches = getStringIArray(jsonObject, "excludeMatches"),
                matches = getStringIArray(jsonObject, "matches"),
                anyMatches = getStringIArray(jsonObject, "anyMatches"),
                order = getInt(jsonObject, "order"),
                forcedTime = getLong(jsonObject, "forcedTime"),
                position = getPosition(jsonObject),
            )
        }

        private fun jsonToGlobalGroups(jsonObject: JsonObject, groupIndex: Int): RawGlobalGroup {
            return RawGlobalGroup(
                key = getInt(jsonObject, "key") ?: error("miss group[$groupIndex].key"),
                name = getString(jsonObject, "name") ?: error("miss group[$groupIndex].name"),
                desc = getString(jsonObject, "desc"),
                enable = getBoolean(jsonObject, "enable"),
                actionCd = getLong(jsonObject, "actionCd"),
                actionDelay = getLong(jsonObject, "actionDelay"),
                quickFind = getBoolean(jsonObject, "quickFind"),
                actionMaximum = getInt(jsonObject, "actionMaximum"),
                matchDelay = getLong(jsonObject, "matchDelay"),
                matchTime = getLong(jsonObject, "matchTime"),
                resetMatch = getString(jsonObject, "resetMatch"),
                snapshotUrls = getStringIArray(jsonObject, "snapshotUrls"),
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
                } ?: emptyList(),
                rules = jsonObject["rules"]?.jsonArray?.map { jsonElement ->
                    jsonToGlobalRule(jsonElement.jsonObject)
                } ?: emptyList(),
                order = getInt(jsonObject, "order"),
                scopeKeys = getIntIArray(jsonObject, "scopeKeys"),
                forcedTime = getLong(jsonObject, "forcedTime"),
            )
        }

        private fun jsonToSubscriptionRaw(rootJson: JsonObject): RawSubscription {
            return RawSubscription(id = getLong(rootJson, "id") ?: error("miss subscription.id"),
                name = getString(rootJson, "name") ?: error("miss subscription.name"),
                version = getInt(rootJson, "version") ?: error("miss subscription.version"),
                author = getString(rootJson, "author"),
                updateUrl = getString(rootJson, "updateUrl"),
                supportUri = getString(rootJson, "supportUri"),
                checkUpdateUrl = getString(rootJson, "checkUpdateUrl"),
                apps = (rootJson["apps"]?.jsonArray?.mapIndexed { index, jsonElement ->
                    jsonToAppRaw(
                        jsonElement.jsonObject, index
                    )
                } ?: emptyList()),
                categories = (rootJson["categories"]?.jsonArray?.mapIndexed { index, jsonElement ->
                    RawCategory(
                        key = getInt(jsonElement.jsonObject, "key")
                            ?: error("miss categories[$index].key"),
                        name = getString(jsonElement.jsonObject, "name")
                            ?: error("miss categories[$index].name"),
                        enable = getBoolean(jsonElement.jsonObject, "enable"),
                    )
                } ?: emptyList()),
                globalGroups = (rootJson["globalGroups"]?.jsonArray?.mapIndexed { index, jsonElement ->
                    jsonToGlobalGroups(jsonElement.jsonObject, index)
                } ?: emptyList()))
        }

        private fun <T> List<T>.findDuplicatedItem(predicate: (T) -> Any?): T? {
            forEach { v ->
                val key = predicate(v)
                if (key != null && any { v2 -> v2 !== v && predicate(v2) == key }) {
                    return v
                }
            }
            return null
        }

        fun parse(source: String, json5: Boolean = true): RawSubscription {
            val text = if (json5) json5ToJson(source) else source
            val subscription = jsonToSubscriptionRaw(json.parseToJsonElement(text).jsonObject)
            subscription.categories.findDuplicatedItem { v -> v.key }?.let { v ->
                error("id=${subscription.id}, duplicated category: key=${v.key}")
            }
            subscription.globalGroups.findDuplicatedItem { v -> v.key }?.let { v ->
                error("id=${subscription.id}, duplicated global group: key=${v.key}")
            }
            subscription.globalGroups.forEach { g ->
                g.rules.findDuplicatedItem { v -> v.key }?.let { v ->
                    error("id=${subscription.id}, duplicated global rule: key=${v.key}, groupKey=${g.key}")
                }
            }
            subscription.apps.findDuplicatedItem { v -> v.id }?.let { v ->
                error("id=${subscription.id}, duplicated app: ${v.id}")
            }
            subscription.apps.forEach { a ->
                a.groups.findDuplicatedItem { v -> v.key }?.let { v ->
                    error("id=${subscription.id}, duplicated app group: key=${v.key}, appId=${a.id}")
                }
                a.groups.forEach { g ->
                    g.rules.findDuplicatedItem { v -> v.key }?.let { v ->
                        error("id=${subscription.id}, duplicated app rule: key=${v.key}, groupKey=${g.key}, appId=${a.id}")
                    }
                }
            }
            return subscription
        }

        fun parseApp(jsonObject: JsonObject): RawApp {
            val a = jsonToAppRaw(jsonObject, 0)
            a.groups.findDuplicatedItem { v -> v.key }?.let { v ->
                error("duplicated app group: key=${v.key}")
            }
            a.groups.forEach { g ->
                g.rules.findDuplicatedItem { v -> v.key }?.let { v ->
                    error("duplicated app rule: key=${v.key}, groupKey=${g.key}")
                }
            }
            return a
        }

        fun parseRawApp(source: String, json5: Boolean = true): RawApp {
            val text = if (json5) json5ToJson(source) else source
            return parseApp(json.parseToJsonElement(text).jsonObject)
        }

        fun parseGroup(jsonObject: JsonObject): RawAppGroup {
            val g = jsonToGroupRaw(jsonObject, 0)
            g.rules.findDuplicatedItem { v -> v.key }?.let { v ->
                error("duplicated app rule: key=${v.key}")
            }
            return g
        }

        fun parseRawGroup(source: String, json5: Boolean = true): RawAppGroup {
            val text = if (json5) json5ToJson(source) else source
            return parseGroup(json.parseToJsonElement(text).jsonObject)
        }

        fun parseRawGlobalGroup(source: String, json5: Boolean = true): RawGlobalGroup {
            val text = if (json5) json5ToJson(source) else source
            val g = jsonToGlobalGroups(json.parseToJsonElement(text).jsonObject, 0)
            g.rules.findDuplicatedItem { v -> v.key }?.let { v ->
                error("duplicated global rule: key=${v.key}")
            }
            return g
        }
    }
}
