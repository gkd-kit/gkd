package li.songe.gkd.data

import blue.endless.jankson.Jankson
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import li.songe.gkd.util.Singleton
import li.songe.selector.Selector


@Serializable
data class SubscriptionRaw(
    val id: Long,
    val name: String,
    val version: Int,
    val author: String? = null,
    val updateUrl: String? = null,
    val supportUri: String? = null,
    val apps: List<AppRaw> = emptyList(),
) {

    interface CommonProps {
        val activityIds: List<String>?
        val excludeActivityIds: List<String>?
        val actionCd: Long?
        val actionDelay: Long?
        val matchLauncher: Boolean?
        val quickFind: Boolean?
        val matchDelay: Long?
        val matchTime: Long?
        val actionMaximum: Int?
    }

    @Serializable
    data class AppRaw(
        val id: String,
        val name: String? = null,
        override val actionCd: Long? = null,
        override val actionDelay: Long? = null,
        override val matchLauncher: Boolean? = null,
        override val quickFind: Boolean? = null,
        override val actionMaximum: Int?,
        override val matchDelay: Long?,
        override val matchTime: Long?,
        override val activityIds: List<String>? = null,
        override val excludeActivityIds: List<String>? = null,
        val groups: List<GroupRaw> = emptyList(),
    ) : CommonProps

    @Serializable
    data class GroupRaw(
        val name: String,
        val desc: String? = null,
        val enable: Boolean? = null,
        val key: Int,
        override val actionCd: Long? = null,
        override val actionDelay: Long? = null,
        override val matchLauncher: Boolean? = null,
        override val quickFind: Boolean? = null,
        override val actionMaximum: Int?,
        override val matchDelay: Long?,
        override val matchTime: Long?,
        override val activityIds: List<String>? = null,
        override val excludeActivityIds: List<String>? = null,
        val rules: List<RuleRaw> = emptyList(),
    ) : CommonProps {

        @IgnoredOnParcel
        val valid by lazy {
            rules.all { r ->
                r.matches.all { s -> Selector.check(s) } && r.excludeMatches.all { s ->
                    Selector.check(
                        s
                    )
                }
            }
        }
    }

    @Serializable
    data class RuleRaw(
        val name: String? = null,
        val key: Int? = null,
        val preKeys: List<Int> = emptyList(),
        val action: String? = null,
        override val actionCd: Long? = null,
        override val actionDelay: Long? = null,
        override val matchLauncher: Boolean? = null,
        override val quickFind: Boolean? = null,
        override val actionMaximum: Int?,
        override val matchDelay: Long?,
        override val matchTime: Long?,
        override val activityIds: List<String>? = null,
        override val excludeActivityIds: List<String>? = null,
        val matches: List<String> = emptyList(),
        val excludeMatches: List<String> = emptyList(),
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
                excludeMatches = (getStringIArray(
                    rulesJson, "excludeMatches"
                ) ?: emptyList()),
                key = getInt(rulesJson, "key"),
                name = getString(rulesJson, "name"),
                preKeys = getIntIArray(rulesJson, "preKeys") ?: emptyList(),
                action = getString(rulesJson, "action"),
                matchLauncher = getBoolean(rulesJson, "matchLauncher"),
                quickFind = getBoolean(rulesJson, "quickFind"),
                actionMaximum = getInt(rulesJson, "actionMaximum"),
                matchDelay = getLong(rulesJson, "matchDelay"),
                matchTime = getLong(rulesJson, "matchTime")
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
                matchLauncher = getBoolean(groupsJson, "matchLauncher"),
                quickFind = getBoolean(groupsJson, "quickFind"),
                actionMaximum = getInt(groupsJson, "actionMaximum"),
                matchDelay = getLong(groupsJson, "matchDelay"),
                matchTime = getLong(groupsJson, "matchTime")
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
                matchLauncher = getBoolean(appsJson, "matchLauncher"),
                quickFind = getBoolean(appsJson, "quickFind"),
                actionMaximum = getInt(appsJson, "actionMaximum"),
                matchDelay = getLong(appsJson, "matchDelay"),
                matchTime = getLong(appsJson, "matchTime")
            )
        }

        private fun jsonToSubscriptionRaw(rootJson: JsonObject): SubscriptionRaw {
            return SubscriptionRaw(id = getLong(rootJson, "id") ?: error("miss subscription.id"),
                name = getString(rootJson, "name") ?: error("miss subscription.name"),
                version = getInt(rootJson, "version") ?: error("miss subscription.version"),
                author = getString(rootJson, "author"),
                updateUrl = getString(rootJson, "updateUrl"),
                supportUri = getString(rootJson, "supportUri"),
                apps = rootJson["apps"]?.jsonArray?.mapIndexed { index, jsonElement ->
                    jsonToAppRaw(
                        jsonElement.jsonObject, index
                    )
                } ?: emptyList())
        }

        //  订阅文件状态: 文件不存在, 文件正常, 文件损坏(损坏原因)
        fun stringify(source: SubscriptionRaw) = Singleton.json.encodeToString(source)

        fun parse(source: String, json5: Boolean = true): SubscriptionRaw {
            val text = if (json5) Jankson.builder().build().load(source).toJson() else source

            val obj = jsonToSubscriptionRaw(Singleton.json.parseToJsonElement(text).jsonObject)

            val duplicatedApps = obj.apps.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
            if (duplicatedApps.isNotEmpty()) {
                error("duplicated app: ${duplicatedApps.map { it.id }}")
            }
            obj.apps.forEach { appRaw ->
                val duplicatedGroups =
                    appRaw.groups.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
                if (duplicatedGroups.isNotEmpty()) {
                    error("app:${appRaw.id}, duplicated group: ${duplicatedGroups.map { it.key }}")
                }
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
            return jsonToAppRaw(Singleton.json.parseToJsonElement(text).jsonObject, 0)
        }

        fun parseGroupRaw(source: String, json5: Boolean = true): GroupRaw {
            val text = if (json5) Jankson.builder().build().load(source).toJson() else source
            return jsonToGroupRaw(Singleton.json.parseToJsonElement(text).jsonObject, 0)
        }
    }

}










