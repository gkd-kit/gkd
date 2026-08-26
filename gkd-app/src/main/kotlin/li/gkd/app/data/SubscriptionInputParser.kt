package li.gkd.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import li.songe.json5.Json5

class SubscriptionInputParser private constructor(
    val jsonObject: JsonObject,
) {
    fun parseApp(): RawSubscription.RawApp {
        val app = parseAppObject()
        app.groups.forEach(::requireValid)
        return app
    }

    fun parseAppGroups(expectedAppId: String): List<RawSubscription.RawAppGroup> {
        val groups = if (isAppObject()) {
            parseAppObject(expectedAppId).groups
        } else {
            listOf(parseRule { RawSubscription.parseAppGroup(jsonObject) })
        }
        groups.forEach(::requireValid)
        return groups
    }

    fun parseAppGroup(expectedAppId: String): RawSubscription.RawAppGroup {
        val group = if (isAppObject()) {
            parseAppObject(expectedAppId).groups.first()
        } else {
            parseRule { RawSubscription.parseAppGroup(jsonObject) }
        }
        requireValid(group)
        return group
    }

    fun parseGlobalGroup(): RawSubscription.RawGlobalGroup {
        val group = parseRule { RawSubscription.parseGlobalGroup(jsonObject) }
        requireValid(group)
        return group
    }

    private fun isAppObject(): Boolean = jsonObject["groups"] is JsonArray

    private fun parseAppObject(
        expectedAppId: String? = null,
    ): RawSubscription.RawApp = parseRule {
        if (expectedAppId != null) requireExpectedAppId(expectedAppId)
        RawSubscription.parseApp(jsonObject).requireGroups()
    }

    private fun requireExpectedAppId(expectedAppId: String) {
        val id = jsonObject["id"] ?: error("缺少id")
        if (id !is JsonPrimitive || !id.isString || id.content != expectedAppId) {
            error("id与当前应用不一致")
        }
    }

    private fun RawSubscription.RawApp.requireGroups(): RawSubscription.RawApp {
        if (groups.isEmpty()) error("至少输入一个规则")
        return this
    }

    private fun <T : RawSubscription.RawGroupProps> requireValid(group: T): T {
        group.errorDesc?.let(::error)
        return group
    }

    private fun <T> parseRule(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        error("非法规则\n${e.message}")
    }

    companion object {
        fun parse(
            source: String,
            defaultGroupKey: Int = 0,
        ): SubscriptionInputParser {
            val element = try {
                Json5.parseToJsonElement(source)
            } catch (e: Exception) {
                error("非法格式\n${e.message}")
            }
            if (element !is JsonObject) error("规则应为对象格式")
            return SubscriptionInputParser(element.fillGroupKeys(defaultGroupKey))
        }

        private fun JsonObject.fillGroupKeys(defaultGroupKey: Int): JsonObject {
            val groups = this["groups"]
            if (groups is JsonArray) {
                val usedKeys = groups.mapNotNull { group ->
                    (group as? JsonObject)?.get("key")?.groupKeyOrNull()
                }.toMutableSet()
                var nextKey = defaultGroupKey
                var changed = false
                val newGroups = groups.map { group ->
                    if (group is JsonObject && group["name"] != null && group["key"] == null) {
                        while (nextKey in usedKeys) nextKey += 1
                        changed = true
                        JsonObject(group + ("key" to JsonPrimitive(nextKey))).also {
                            usedKeys.add(nextKey)
                            nextKey += 1
                        }
                    } else {
                        group
                    }
                }
                return if (changed) {
                    JsonObject(this + ("groups" to JsonArray(newGroups)))
                } else {
                    this
                }
            }
            return if (this["name"] != null && this["key"] == null) {
                JsonObject(this + ("key" to JsonPrimitive(defaultGroupKey)))
            } else {
                this
            }
        }

        private fun JsonElement.groupKeyOrNull(): Int? {
            val primitive = this as? JsonPrimitive ?: return null
            return primitive.content.toIntOrNull()
        }
    }
}
