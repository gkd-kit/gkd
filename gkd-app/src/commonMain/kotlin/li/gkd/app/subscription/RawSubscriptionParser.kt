package li.gkd.app.subscription

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import li.gkd.app.subscription.RawSubscription.IntegerMatcher
import li.gkd.app.subscription.RawSubscription.Position
import li.gkd.app.subscription.RawSubscription.RawApp
import li.gkd.app.subscription.RawSubscription.RawAppGroup
import li.gkd.app.subscription.RawSubscription.RawAppRule
import li.gkd.app.subscription.RawSubscription.RawCategory
import li.gkd.app.subscription.RawSubscription.RawGlobalApp
import li.gkd.app.subscription.RawSubscription.RawGlobalGroup
import li.gkd.app.subscription.RawSubscription.RawGlobalRule
import li.gkd.app.subscription.RawSubscription.RawGlobalRuleApp
import li.gkd.app.subscription.RawSubscription.StringMatcher
import li.gkd.app.subscription.RawSubscription.SwipeArg
import li.gkd.app.subscription.SubscriptionJson.json
import li.songe.json5.Json5
import li.songe.json5.Json5ParseException

object RawSubscriptionParser {
    fun parse(source: String, json5: Boolean = true): RawSubscription {
        return Parser().parse(source, json5)
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

    private fun <T : Any> required(
        jsonObject: JsonObject,
        key: String,
        value: T?,
        type: String,
    ): T =
        value
            ?: at(key) {
                if (jsonObject[key] == null || jsonObject[key] == JsonNull) {
                    fail("Required field is missing")
                }
                fail("Expected $type")
            }

    private fun <T> getArray(
        jsonObject: JsonObject,
        key: String,
        read: (JsonElement) -> T,
    ): List<T>? =
        at(key) {
            val element = jsonObject[key] ?: return@at null
            val array = element as? JsonArray ?: fail("Expected an array")
            array.mapIndexed { index, value -> at(index) { read(value) } }
        }

    private fun <T> getFlexibleList(
        jsonObject: JsonObject,
        key: String,
        read: (JsonElement) -> T,
    ): List<T> =
        at(key) {
            when (val element = jsonObject[key]) {
                null,
                JsonNull -> emptyList()
                is JsonArray -> element.mapIndexed { index, value -> at(index) { read(value) } }
                else -> listOf(read(element))
            }
        }

    private fun getPosition(jsonObject: JsonObject): Position? =
        at("position") {
            val element = jsonObject["position"] as? JsonObject ?: return@at null
            readPosition(element)
        }

    private fun readPosition(element: JsonObject): Position =
        Position(
            left = getPositionValue(element, "left"),
            bottom = getPositionValue(element, "bottom"),
            top = getPositionValue(element, "top"),
            right = getPositionValue(element, "right"),
            x = getPositionValue(element, "x"),
            y = getPositionValue(element, "y"),
        )

    private fun getPositionValue(jsonObject: JsonObject, key: String): String? =
        at(key) {
            val value = jsonObject[key] ?: return@at null
            (value as? JsonPrimitive)?.content ?: fail("Expected a primitive position expression")
        }

    private fun getSwipeArg(jsonObject: JsonObject): SwipeArg? =
        at("swipeArg") {
            val element = jsonObject["swipeArg"] as? JsonObject ?: return@at null
            SwipeArg(
                start =
                    at("start") {
                        val start = element["start"] ?: fail("Required field is missing")
                        readPosition(requireObject(start))
                    },
                end =
                    at("end") {
                        element["end"]?.let { readPosition(requireObject(it)) }
                    },
                duration =
                    required(element, "duration", getLong(element, "duration"), "an integer"),
            )
        }

    private fun getStringIArray(jsonObject: JsonObject, key: String): List<String>? =
        at(key) { readStringIArray(jsonObject[key]) }

    private fun readStringIArray(element: JsonElement?): List<String>? =
        when (element) {
            JsonNull,
            null -> null
            is JsonObject -> fail("Expected a primitive or an array of primitives")
            is JsonArray ->
                element.mapIndexed { index, value ->
                    at(index) {
                        if (value == JsonNull || value !is JsonPrimitive)
                            fail("Expected a non-null primitive")
                        value.content
                    }
                }
            is JsonPrimitive -> listOf(element.content)
        }

    private fun getIntMatcher(jsonObject: JsonObject, key: String): IntegerMatcher? =
        at(key) {
            when (val element = jsonObject[key]) {
                JsonNull,
                null -> null
                is JsonObject ->
                    IntegerMatcher(
                        minimum = getInt(element, "minimum"),
                        maximum = getInt(element, "maximum"),
                        include = getIntIArray(element, "include"),
                        exclude = getIntIArray(element, "exclude"),
                    )
                else -> fail("Expected an integer matcher object")
            }
        }

    private fun getStringMatcher(jsonObject: JsonObject, key: String): StringMatcher? =
        at(key) {
            when (val element = jsonObject[key]) {
                JsonNull,
                null -> null
                is JsonObject ->
                    StringMatcher(
                        pattern = getString(element, "pattern"),
                        include = getStringIArray(element, "include"),
                        exclude = getStringIArray(element, "exclude"),
                    )
                else -> fail("Expected a string matcher object")
            }
        }

    private fun getIntIArray(jsonObject: JsonObject, key: String): List<Int>? =
        at(key) {
            when (val element = jsonObject[key]) {
                JsonNull,
                null -> null
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

    private fun getLong(jsonObject: JsonObject, key: String): Long? =
        at(key) {
            when (val value = jsonObject[key]) {
                JsonNull,
                null -> null
                is JsonPrimitive -> value.longOrNull ?: fail("Expected an integer")
                else -> null
            }
        }

    private fun getInt(jsonObject: JsonObject, key: String): Int? =
        at(key) {
            when (val value = jsonObject[key]) {
                JsonNull,
                null -> null
                is JsonPrimitive -> readInt(value)
                else -> null
            }
        }

    private fun getBoolean(jsonObject: JsonObject, key: String): Boolean? =
        at(key) {
            when (val value = jsonObject[key]) {
                JsonNull,
                null -> null
                is JsonPrimitive -> value.booleanOrNull ?: fail("Expected a boolean")
                else -> null
            }
        }

    private fun getCompatVersionCode(jsonObject: JsonObject): IntegerMatcher? {
        getIntMatcher(jsonObject, "versionCode")?.let {
            return it
        }
        // compat old value
        val a = getIntIArray(jsonObject, "versionCodes")
        val b = getIntIArray(jsonObject, "excludeVersionCodes")
        if (a != null || b != null) {
            return IntegerMatcher(
                minimum = null,
                maximum = null,
                include = a,
                exclude = b,
            )
        }
        return null
    }

    private fun getCompatVersionName(jsonObject: JsonObject): StringMatcher? {
        getStringMatcher(jsonObject, "versionName")?.let {
            return it
        }
        // compat old value
        val a = getStringIArray(jsonObject, "versionNames")
        val b = getStringIArray(jsonObject, "excludeVersionNames")
        if (a != null || b != null) {
            return StringMatcher(
                pattern = null,
                include = a,
                exclude = b,
            )
        }
        return null
    }

    private fun jsonToRuleRaw(rulesRawJson: JsonElement): RawAppRule {
        val jsonObject =
            when (rulesRawJson) {
                JsonNull -> fail("Rule must not be null")
                is JsonObject -> rulesRawJson
                is JsonPrimitive,
                is JsonArray -> JsonObject(emptyMap())
            }
        return RawAppRule(
            activityIds = getStringIArray(jsonObject, "activityIds"),
            excludeActivityIds = getStringIArray(jsonObject, "excludeActivityIds"),
            matches =
                if (rulesRawJson is JsonObject) getStringIArray(jsonObject, "matches")
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
            rules =
                getFlexibleList(jsonObject, "rules", ::jsonToRuleRaw).distinctNotNullBy { it.key },
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
            groups =
                getFlexibleList(jsonObject, "groups", ::jsonToGroupRaw).distinctByIfAny { it.key },
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
            apps =
                getArray(jsonObject, "apps") { jsonElement ->
                        jsonToGlobalRuleApp(requireObject(jsonElement))
                    }
                    ?.distinctByIfAny { it.id },
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
            apps =
                getArray(jsonObject, "apps") { jsonElement ->
                        jsonToGlobalApp(requireObject(jsonElement))
                    }
                    ?.distinctByIfAny { it.id },
            rules =
                (getArray(jsonObject, "rules") { jsonElement ->
                        jsonToGlobalRule(requireObject(jsonElement))
                    } ?: emptyList())
                    .distinctNotNullBy { it.key },
            order = getInt(jsonObject, "order"),
            scopeKeys = getIntIArray(jsonObject, "scopeKeys"),
            forcedTime = getLong(jsonObject, "forcedTime"),
            priorityTime = getLong(jsonObject, "priorityTime"),
            priorityActionMaximum = getInt(jsonObject, "priorityActionMaximum"),
            disableIfAppGroupMatch = getString(jsonObject, "disableIfAppGroupMatch"),
        )
    }

    private fun jsonToSubscriptionRaw(element: JsonElement): RawSubscription {
        val rootJson = requireObject(element)
        return RawSubscription(
            id = required(rootJson, "id", getLong(rootJson, "id"), "an integer"),
            name = required(rootJson, "name", getString(rootJson, "name"), "a string"),
            version = required(rootJson, "version", getInt(rootJson, "version"), "an integer"),
            author = getString(rootJson, "author"),
            updateUrl = getString(rootJson, "updateUrl"),
            supportUri = getString(rootJson, "supportUri"),
            checkUpdateUrl = getString(rootJson, "checkUpdateUrl"),
            apps =
                (getArray(rootJson, "apps") { jsonElement ->
                        jsonToAppRaw(requireObject(jsonElement))
                    } ?: emptyList())
                    .filterIfNotAll { it.groups.isNotEmpty() }
                    .distinctByIfAny { it.id },
            categories =
                (getArray(rootJson, "categories") { jsonElement ->
                        val category = requireObject(jsonElement)
                        RawCategory(
                            key = required(category, "key", getInt(category, "key"), "an integer"),
                            name =
                                required(category, "name", getString(category, "name"), "a string"),
                            enable = getBoolean(category, "enable"),
                            desc = getString(category, "desc"),
                        )
                    } ?: emptyList())
                    .filterIfNotAll { it.name.isNotEmpty() }
                    .distinctByIfAny { it.key },
            globalGroups =
                (getArray(rootJson, "globalGroups") { jsonElement ->
                        jsonToGlobalGroup(requireObject(jsonElement))
                    } ?: emptyList())
                    .distinctByIfAny { it.key },
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
        val element =
            try {
                if (json5) Json5.parseToJsonElement(source) else json.parseToJsonElement(source)
            } catch (e: Json5ParseException) {
                throw SubscriptionParseException("$", e.diagnostic.message, e)
            } catch (e: SerializationException) {
                throw SubscriptionParseException("$", e.message ?: "Invalid JSON", e)
            }
        return jsonToSubscriptionRaw(element)
    }
}

private inline fun <T> Iterable<T>.filterIfNotAll(predicate: (T) -> Boolean): List<T> =
    if (count() > 0 && !all(predicate)) filter(predicate) else this as? List<T> ?: toList()

private inline fun <T, K> Iterable<T>.distinctByIfAny(selector: (T) -> K): List<T> =
    if (count() > 1 && any { a -> any { b -> a !== b && selector(a) == selector(b) } })
        distinctBy(
            selector,
        )
    else this as? List<T> ?: toList()
