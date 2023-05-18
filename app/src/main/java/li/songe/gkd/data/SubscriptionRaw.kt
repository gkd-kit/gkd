package li.songe.gkd.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import li.songe.gkd.util.Singleton
import li.songe.selector_android.GkdSelector


@Parcelize
@Serializable
data class SubscriptionRaw(
    @SerialName("name") val name: String,
    @SerialName("version") val version: Int,
    @SerialName("author") val author: String? = null,
    @SerialName("updateUrl") val updateUrl: String? = null,
    @SerialName("supportUrl") val supportUrl: String? = null,
    @SerialName("apps") val apps: List<AppRaw> = emptyList(),
) : Parcelable {

    @Parcelize
    @Serializable
    data class AppRaw(
        @SerialName("id") val id: String,
        @SerialName("cd") val cd: Long? = null,
        @SerialName("activityIds") val activityIds: List<String>? = null,
        @SerialName("excludeActivityIds") val excludeActivityIds: List<String>? = null,
        @SerialName("groups") val groups: List<GroupRaw> = emptyList(),
    ) : Parcelable

    @Parcelize
    @Serializable
    data class GroupRaw(
        @SerialName("name") val name: String? = null,
        @SerialName("key") val key: Int? = null,
        @SerialName("cd") val cd: Long? = null,
        @SerialName("activityIds") val activityIds: List<String>? = null,
        @SerialName("excludeActivityIds") val excludeActivityIds: List<String>? = null,
        @SerialName("rules") val rules: List<RuleRaw> = emptyList(),
    ) : Parcelable

    @Parcelize
    @Serializable
    data class RuleRaw(
        @SerialName("name") val name: String? = null,
        @SerialName("key") val key: Int? = null,
        @SerialName("preKeys") val preKeys: List<Int> = emptyList(),
        @SerialName("cd") val cd: Long? = null,
        @SerialName("activityIds") val activityIds: List<String>? = null,
        @SerialName("excludeActivityIds") val excludeActivityIds: List<String>? = null,
        @SerialName("matches") val matches: List<String> = emptyList(),
        @SerialName("excludeMatches") val excludeMatches: List<String> = emptyList(),
    ) : Parcelable

    companion object {

        private fun getStringIArray(json: JsonObject? = null, name: String = ""): List<String>? {
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
        private fun getIntIArray(json: JsonObject? = null, name: String = ""): List<Int>? {
            return when (val element = json?.get(name)) {
                JsonNull, null -> null
                is JsonArray -> element.map {
                    when (it) {
                        is JsonObject, is JsonArray, JsonNull -> error("Element ${this::class} is not a int")
                        is JsonPrimitive -> it.int
                    }
                }

                is JsonPrimitive -> listOf(element.int)
                else -> error("")
            }
        }

        private fun getString(json: JsonObject? = null, key: String = ""): String? =
            when (val p = json?.get(key)) {
                JsonNull, null -> null
                is JsonPrimitive -> {
                    if (p.isString) {
                        p.content
                    } else {
                        error("")
                    }
                }

                else -> error("")
            }

        @Suppress("SameParameterValue")
        private fun getLong(json: JsonObject? = null, key: String = ""): Long? =
            when (val p = json?.get(key)) {
                JsonNull, null -> null
                is JsonPrimitive -> {
                    p.long
                }

                else -> error("")
            }

        private fun getInt(json: JsonObject? = null, key: String = ""): Int? =
            when (val p = json?.get(key)) {
                JsonNull, null -> null
                is JsonPrimitive -> {
                    p.int
                }

                else -> error("")
            }

        private fun jsonToRuleRaw(rulesRawJson: JsonElement): RuleRaw {
            val rulesJson = when (rulesRawJson) {
                JsonNull -> error("")
                is JsonObject -> rulesRawJson
                is JsonPrimitive, is JsonArray -> JsonObject(mapOf("matches" to rulesRawJson))
            }
            return RuleRaw(
                activityIds = getStringIArray(rulesJson, "activityIds"),
                excludeActivityIds = getStringIArray(rulesJson, "excludeActivityIds"),
                cd = getLong(rulesJson, "cd"),
                matches = (getStringIArray(
                    rulesJson,
                    "matches"
                ) ?: emptyList()).onEach { GkdSelector.gkdSelectorParser(it) },
                excludeMatches = (getStringIArray(
                    rulesJson,
                    "excludeMatches"
                ) ?: emptyList()).onEach { GkdSelector.gkdSelectorParser(it) },
                key = getInt(rulesJson, "key"),
                name = getString(rulesJson, "name"),
                preKeys = getIntIArray(rulesJson, "preKeys") ?: emptyList(),
            )
        }


        private fun jsonToGroupRaw(groupsRawJson: JsonElement): GroupRaw {
            val groupsJson = when (groupsRawJson) {
                JsonNull -> error("")
                is JsonObject -> groupsRawJson
                is JsonPrimitive, is JsonArray -> JsonObject(mapOf("rules" to groupsRawJson))
            }
            return GroupRaw(
                activityIds = getStringIArray(groupsJson, "activityIds"),
                excludeActivityIds = getStringIArray(groupsJson, "excludeActivityIds"),
                cd = getLong(groupsJson, "cd"),
                name = getString(groupsJson, "name"),
                key = getInt(groupsJson, "key"),
                rules = when (val rulesJson = groupsJson["rules"]) {
                    null, JsonNull -> emptyList()
                    is JsonPrimitive, is JsonObject -> JsonArray(listOf(rulesJson))
                    is JsonArray -> rulesJson
                }.map {
                    jsonToRuleRaw(it)
                }
            )
        }

        private fun jsonToAppRaw(appsJson: JsonObject): AppRaw {
            return AppRaw(
                activityIds = getStringIArray(appsJson, "activityIds"),
                excludeActivityIds = getStringIArray(appsJson, "excludeActivityIds"),
                cd = getLong(appsJson, "cd"),
                id = getString(appsJson, "id") ?: error(""),
                groups = (when (val groupsJson = appsJson["groups"]) {
                    null, JsonNull -> emptyList()
                    is JsonPrimitive, is JsonObject -> JsonArray(listOf(groupsJson))
                    is JsonArray -> groupsJson
                }).map {
                    jsonToGroupRaw(it)
                }
            )
        }

        private fun jsonToSubscriptionRaw(rootJson: JsonObject): SubscriptionRaw {
            return SubscriptionRaw(
                name = getString(rootJson, "name") ?: error(""),
                version = getInt(rootJson, "version") ?: error(""),
                author = getString(rootJson, "author"),
                updateUrl = getString(rootJson, "updateUrl"),
                supportUrl = getString(rootJson, "supportUrl"),
                apps = rootJson["apps"]?.jsonArray?.map { jsonToAppRaw(it.jsonObject) }
                    ?: emptyList()
            )
        }

        fun stringify(source: SubscriptionRaw) = Singleton.json.encodeToString(source)

        fun parse(source: String): SubscriptionRaw {
            return jsonToSubscriptionRaw(Singleton.json.parseToJsonElement(source).jsonObject)
        }

        fun parse5(source: String): SubscriptionRaw {
            return parse(
                Singleton.json5.load(source).toJson()
            )
        }
    }

}










