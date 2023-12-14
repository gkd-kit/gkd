package li.songe.gkd.data

import blue.endless.jankson.Jankson
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import li.songe.gkd.util.json
import li.songe.selector.Selector


@Serializable
data class SubscriptionRaw(
    val id: Long,
    val name: String,
    val version: Int,
    val author: String? = null,
    val updateUrl: String? = null,
    val supportUri: String? = null,
    val checkUpdateUrl: String? = null,
    val apps: List<AppRaw> = emptyList(),
    val categories: List<Category> = emptyList(),
) {

    @IgnoredOnParcel
    val categoriesGroups by lazy {
        val allAppGroups =
            apps.flatMap { a -> a.groups.map { g -> g to a } }
        allAppGroups.groupBy { g ->
            categories.find { c -> g.first.name.startsWith(c.name) }
        }
    }

    @IgnoredOnParcel
    val groupToCategoryMap by lazy {
        val map = mutableMapOf<GroupRaw, Category>()
        categoriesGroups.forEach { (key, value) ->
            value.forEach { (g) ->
                if (key != null) {
                    map[g] = key
                }
            }
        }
        map
    }

    @Serializable
    data class Category(val key: Int, val name: String, val enable: Boolean?)

    interface CommonProps {
        val activityIds: List<String>?
        val excludeActivityIds: List<String>?
        val actionCd: Long?
        val actionDelay: Long?
        val quickFind: Boolean?
        val matchDelay: Long?
        val matchTime: Long?
        val actionMaximum: Int?
        val resetMatch: String?
    }

    @Serializable
    data class AppRaw(
        val id: String,
        val name: String?,
        override val actionCd: Long?,
        override val actionDelay: Long?,
        override val quickFind: Boolean?,
        override val actionMaximum: Int?,
        override val matchDelay: Long?,
        override val matchTime: Long?,
        override val resetMatch: String?,
        override val activityIds: List<String>?,
        override val excludeActivityIds: List<String>?,
        val groups: List<GroupRaw> = emptyList(),
    ) : CommonProps

    @Serializable
    data class GroupRaw(
        val name: String,
        val key: Int,
        val desc: String?,
        val enable: Boolean?,
        override val actionCd: Long?,
        override val actionDelay: Long?,
        override val quickFind: Boolean?,
        override val actionMaximum: Int?,
        override val matchDelay: Long?,
        override val matchTime: Long?,
        override val resetMatch: String?,
        override val activityIds: List<String>?,
        override val excludeActivityIds: List<String>?,
        val rules: List<RuleRaw>,
        val snapshotUrls: List<String>?,
        val exampleUrls: List<String>?,
    ) : CommonProps {

        @IgnoredOnParcel
        val valid by lazy {
            rules.all { r ->
                r.matches.all { s -> Selector.check(s) } && (r.excludeMatches
                    ?: emptyList()).all { s ->
                    Selector.check(
                        s
                    )
                }
            }
        }

        @IgnoredOnParcel
        val allExampleUrls by lazy {
            mutableListOf<String>().apply {
                if (exampleUrls != null) {
                    addAll(exampleUrls)
                }
                rules.forEach { r ->
                    if (r.exampleUrls != null) {
                        addAll(r.exampleUrls)
                    }
                }
            }
        }
    }

    @Serializable
    data class RuleRaw(
        val name: String?,
        val key: Int?,
        val preKeys: List<Int>?,
        val action: String?,
        val actionCdKey: Int?,
        val actionMaximumKey: Int?,
        override val actionCd: Long?,
        override val actionDelay: Long?,
        override val quickFind: Boolean?,
        override val actionMaximum: Int?,
        override val matchDelay: Long?,
        override val matchTime: Long?,
        override val resetMatch: String?,
        override val activityIds: List<String>?,
        override val excludeActivityIds: List<String>?,
        val matches: List<String>,
        val excludeMatches: List<String>?,
        val snapshotUrls: List<String>?,
        val exampleUrls: List<String>?,
    ) : CommonProps

    companion object {


        private fun getStringIArray(json: JsonObject? = null, name: String): List<String>? {
            return when (val element = json?.get(name)) {
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
        private fun getIntIArray(json: JsonObject? = null, name: String): List<Int>? {
            return when (val element = json?.get(name)) {
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

        private fun getString(json: JsonObject? = null, key: String): String? =
            when (val p = json?.get(key)) {
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

        private fun getLong(json: JsonObject? = null, key: String): Long? =
            when (val p = json?.get(key)) {
                JsonNull, null -> null
                is JsonPrimitive -> {
                    p.long
                }

                else -> error("Element $p is not a long")
            }

        private fun getInt(json: JsonObject? = null, key: String): Int? =
            when (val p = json?.get(key)) {
                JsonNull, null -> null
                is JsonPrimitive -> {
                    p.int
                }

                else -> error("Element $p is not a int")
            }

        @Suppress("SameParameterValue")
        private fun getBoolean(json: JsonObject? = null, key: String): Boolean? =
            when (val p = json?.get(key)) {
                JsonNull, null -> null
                is JsonPrimitive -> {
                    p.boolean
                }

                else -> error("Element $p is not a boolean")
            }

        private fun jsonToRuleRaw(rulesRawJson: JsonElement): RuleRaw {
            val rulesJson = when (rulesRawJson) {
                JsonNull -> error("miss current rule")
                is JsonObject -> rulesRawJson
                is JsonPrimitive, is JsonArray -> JsonObject(mapOf("matches" to rulesRawJson))
            }
            return RuleRaw(
                activityIds = getStringIArray(rulesJson, "activityIds"),
                excludeActivityIds = getStringIArray(rulesJson, "excludeActivityIds"),
                actionCd = getLong(rulesJson, "actionCd") ?: getLong(rulesJson, "cd"),
                actionDelay = getLong(rulesJson, "actionDelay") ?: getLong(rulesJson, "delay"),
                matches = (getStringIArray(
                    rulesJson, "matches"
                ) ?: emptyList()),
                excludeMatches = getStringIArray(rulesJson, "excludeMatches"),
                key = getInt(rulesJson, "key"),
                name = getString(rulesJson, "name"),
                preKeys = getIntIArray(rulesJson, "preKeys"),
                action = getString(rulesJson, "action"),
                quickFind = getBoolean(rulesJson, "quickFind"),
                actionMaximum = getInt(rulesJson, "actionMaximum"),
                matchDelay = getLong(rulesJson, "matchDelay"),
                matchTime = getLong(rulesJson, "matchTime"),
                resetMatch = getString(rulesJson, "resetMatch"),
                snapshotUrls = getStringIArray(rulesJson, "snapshotUrls"),
                exampleUrls = getStringIArray(rulesJson, "exampleUrls"),
                actionMaximumKey = getInt(rulesJson, "actionMaximumKey"),
                actionCdKey = getInt(rulesJson, "actionCdKey"),
            )
        }


        private fun jsonToGroupRaw(groupsRawJson: JsonElement, groupIndex: Int): GroupRaw {
            val groupsJson = when (groupsRawJson) {
                JsonNull -> error("")
                is JsonObject -> groupsRawJson
                is JsonPrimitive, is JsonArray -> JsonObject(mapOf("rules" to groupsRawJson))
            }
            return GroupRaw(
                activityIds = getStringIArray(groupsJson, "activityIds"),
                excludeActivityIds = getStringIArray(groupsJson, "excludeActivityIds"),
                actionCd = getLong(groupsJson, "actionCd") ?: getLong(groupsJson, "cd"),
                actionDelay = getLong(groupsJson, "actionDelay") ?: getLong(groupsJson, "delay"),
                name = getString(groupsJson, "name") ?: error("miss group name"),
                desc = getString(groupsJson, "desc"),
                enable = getBoolean(groupsJson, "enable"),
                key = getInt(groupsJson, "key") ?: groupIndex,
                rules = when (val rulesJson = groupsJson["rules"]) {
                    null, JsonNull -> emptyList()
                    is JsonPrimitive, is JsonObject -> JsonArray(listOf(rulesJson))
                    is JsonArray -> rulesJson
                }.map {
                    jsonToRuleRaw(it)
                },
                quickFind = getBoolean(groupsJson, "quickFind"),
                actionMaximum = getInt(groupsJson, "actionMaximum"),
                matchDelay = getLong(groupsJson, "matchDelay"),
                matchTime = getLong(groupsJson, "matchTime"),
                resetMatch = getString(groupsJson, "resetMatch"),
                snapshotUrls = getStringIArray(groupsJson, "snapshotUrls"),
                exampleUrls = getStringIArray(groupsJson, "exampleUrls"),
            )
        }

        private fun jsonToAppRaw(appsJson: JsonObject, appIndex: Int): AppRaw {
            return AppRaw(
                activityIds = getStringIArray(appsJson, "activityIds"),
                excludeActivityIds = getStringIArray(appsJson, "excludeActivityIds"),
                actionCd = getLong(appsJson, "actionCd") ?: getLong(appsJson, "cd"),
                actionDelay = getLong(appsJson, "actionDelay") ?: getLong(appsJson, "delay"),
                id = getString(appsJson, "id") ?: error("miss subscription.apps[$appIndex].id"),
                name = getString(appsJson, "name"),
                groups = (when (val groupsJson = appsJson["groups"]) {
                    null, JsonNull -> emptyList()
                    is JsonPrimitive, is JsonObject -> JsonArray(listOf(groupsJson))
                    is JsonArray -> groupsJson
                }).mapIndexed { index, jsonElement ->
                    jsonToGroupRaw(jsonElement, index)
                },
                quickFind = getBoolean(appsJson, "quickFind"),
                actionMaximum = getInt(appsJson, "actionMaximum"),
                matchDelay = getLong(appsJson, "matchDelay"),
                matchTime = getLong(appsJson, "matchTime"),
                resetMatch = getString(appsJson, "resetMatch")
            )
        }

        private fun jsonToSubscriptionRaw(rootJson: JsonObject): SubscriptionRaw {
            return SubscriptionRaw(id = getLong(rootJson, "id") ?: error("miss subscription.id"),
                name = getString(rootJson, "name") ?: error("miss subscription.name"),
                version = getInt(rootJson, "version") ?: error("miss subscription.version"),
                author = getString(rootJson, "author"),
                updateUrl = getString(rootJson, "updateUrl"),
                supportUri = getString(rootJson, "supportUri"),
                checkUpdateUrl = getString(rootJson, "checkUpdateUrl"),
                apps = rootJson["apps"]?.jsonArray?.mapIndexed { index, jsonElement ->
                    jsonToAppRaw(
                        jsonElement.jsonObject, index
                    )
                } ?: emptyList(),
                categories = rootJson["categories"]?.jsonArray?.mapIndexed { index, jsonElement ->
                    Category(
                        key = getInt(jsonElement.jsonObject, "key")
                            ?: error("miss categories[$index].key"),
                        name = getString(jsonElement.jsonObject, "name")
                            ?: error("miss categories[$index].name"),
                        enable = getBoolean(jsonElement.jsonObject, "enable"),
                    )
                } ?: emptyList()
            )
        }

        //  订阅文件状态: 文件不存在, 文件正常, 文件损坏(损坏原因)
        fun stringify(source: SubscriptionRaw) = json.encodeToString(source)

        fun parse(source: String, json5: Boolean = true): SubscriptionRaw {
            val text = if (json5) Jankson.builder().build().load(source).toJson() else source
            val obj = jsonToSubscriptionRaw(json.parseToJsonElement(text).jsonObject)
            // 校验 category 不重复
            obj.categories.forEach { c ->
                if (obj.categories.find { c2 -> c2 !== c && c2.key == c.key } != null) {
                    error("duplicated category: key:${c.key} ")
                }
            }
            // 校验 appId 不重复
            val duplicatedApps = obj.apps.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
            if (duplicatedApps.isNotEmpty()) {
                error("duplicated app: ${duplicatedApps.map { it.id }}")
            }
            obj.apps.forEach { appRaw ->
                // 校验 group key 不重复
                val duplicatedGroups =
                    appRaw.groups.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
                if (duplicatedGroups.isNotEmpty()) {
                    error("app:${appRaw.id}, duplicated group: ${duplicatedGroups.map { it.key }}")
                }
                // 校验 rule key 不重复
                appRaw.groups.forEach { groupRaw ->
                    val duplicatedRules =
                        groupRaw.rules.mapNotNull { r -> r.key }.groupingBy { it }.eachCount()
                            .filter { it.value > 1 }.keys
                    if (duplicatedRules.isNotEmpty()) {
                        error("app:${appRaw.id}, group:${groupRaw.key},  duplicated rule: $duplicatedRules")
                    }
                }
            }
            return obj
        }

        fun parseAppRaw(source: String, json5: Boolean = true): AppRaw {
            val text = if (json5) Jankson.builder().build().load(source).toJson() else source
            return jsonToAppRaw(json.parseToJsonElement(text).jsonObject, 0)
        }

        fun parseGroupRaw(source: String, json5: Boolean = true): GroupRaw {
            val text = if (json5) Jankson.builder().build().load(source).toJson() else source
            return jsonToGroupRaw(json.parseToJsonElement(text).jsonObject, 0)
        }
    }

}










