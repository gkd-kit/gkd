package li.gkd.app.subscription

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import li.gkd.app.error.ErrorMessage
import li.songe.json5.Json5
import li.songe.json5.Json5ParseException

class SubscriptionInputParser
private constructor(
    val jsonObject: JsonObject,
) {
    fun parseApp(): SubscriptionEditResult<RawSubscription.RawApp> =
        parseAppObject().flatMap { app ->
            validateGroups(app.groups)?.let {
                return@flatMap it
            }
            SubscriptionEditResult.Success(app)
        }

    fun parseAppGroups(
        expectedAppId: String,
    ): SubscriptionEditResult<List<RawSubscription.RawAppGroup>> {
        val result =
            if (isAppObject()) parseAppObject(expectedAppId).map { it.groups }
            else parseRule { RawSubscriptionParser.parseAppGroup(jsonObject) }.map { listOf(it) }
        return result.flatMap { groups ->
            validateGroups(groups) ?: SubscriptionEditResult.Success(groups)
        }
    }

    fun parseAppGroup(expectedAppId: String): SubscriptionEditResult<RawSubscription.RawAppGroup> {
        val result =
            if (isAppObject()) parseAppObject(expectedAppId).map { it.groups.first() }
            else parseRule { RawSubscriptionParser.parseAppGroup(jsonObject) }
        return result.flatMap { group ->
            validateGroups(listOf(group)) ?: SubscriptionEditResult.Success(group)
        }
    }

    fun parseGlobalGroup(): SubscriptionEditResult<RawSubscription.RawGlobalGroup> = parseRule {
        RawSubscriptionParser.parseGlobalGroup(jsonObject)
    }
        .flatMap { group ->
            validateGroups(listOf(group)) ?: SubscriptionEditResult.Success(group)
        }

    private fun isAppObject(): Boolean = jsonObject["groups"] is JsonArray

    private fun parseAppObject(
        expectedAppId: String? = null,
    ): SubscriptionEditResult<RawSubscription.RawApp> {
        if (expectedAppId != null) {
            val id =
                jsonObject["id"]
                    ?: return SubscriptionEditResult.Rejected(
                        SubscriptionFailureReason.RuleIdRequired,
                    )
            if (id !is JsonPrimitive || !id.isString || id.content != expectedAppId) {
                return SubscriptionEditResult.Rejected(SubscriptionFailureReason.RuleAppIdMismatch)
            }
        }
        return parseRule { RawSubscriptionParser.parseApp(jsonObject) }
            .flatMap { app ->
                if (app.groups.isEmpty())
                    SubscriptionEditResult.Rejected(SubscriptionFailureReason.RuleInputRequired)
                else SubscriptionEditResult.Success(app)
            }
    }

    private fun validateGroups(
        groups: List<RawSubscription.RawGroupProps>,
    ): SubscriptionEditResult.Rejected? {
        for (group in groups) {
            when (val failure = group.validationError) {
                null -> Unit
                is RuleValidationError.SelectorError ->
                    return SubscriptionEditResult.Rejected(
                        SubscriptionFailureReason.SelectorInvalidDetail,
                        listOf(failure.source, failure.detail),
                        errorCode = "INVALID_SELECTOR",
                    )
                is RuleValidationError.InvalidPosition ->
                    return SubscriptionEditResult.Rejected(
                        SubscriptionFailureReason.RuleInvalidPosition,
                        detail = failure.error.message,
                        errorPath = failure.error.field,
                    )
                is RuleValidationError.InvalidVersionPattern ->
                    return SubscriptionEditResult.Rejected(
                        SubscriptionFailureReason.RuleInvalidVersionPattern,
                        listOf(failure.field, failure.source, failure.detail),
                        errorPath = failure.field,
                    )
            }
        }
        return null
    }

    private fun <T> parseRule(block: () -> T): SubscriptionEditResult<T> =
        try {
            SubscriptionEditResult.Success(block())
        } catch (e: SubscriptionParseException) {
            SubscriptionEditResult.Rejected(
                SubscriptionFailureReason.RuleInvalidDetail,
                detail = ErrorMessage.Text(e.message.orEmpty()),
                errorPath = e.path,
                errorCode = "INVALID_SUBSCRIPTION",
            )
        }

    companion object {
        fun parse(
            source: String,
            defaultGroupKey: Int = 0,
        ): SubscriptionEditResult<SubscriptionInputParser> {
            val element =
                try {
                    Json5.parseToJsonElement(source)
                } catch (e: Json5ParseException) {
                    return SubscriptionEditResult.Rejected(
                        SubscriptionFailureReason.FormatInvalidDetail,
                        detail = ErrorMessage.Text(e.message.orEmpty()),
                        errorPath = "$",
                        errorCode = "INVALID_SUBSCRIPTION",
                    )
                }
            if (element !is JsonObject)
                return SubscriptionEditResult.Rejected(SubscriptionFailureReason.RuleObjectRequired)
            return SubscriptionEditResult.Success(
                SubscriptionInputParser(element.fillGroupKeys(defaultGroupKey)),
            )
        }

        private fun JsonObject.fillGroupKeys(defaultGroupKey: Int): JsonObject {
            val groups = this["groups"]
            if (groups is JsonArray) {
                val usedKeys =
                    groups
                        .mapNotNull { group ->
                            (group as? JsonObject)?.get("key")?.groupKeyOrNull()
                        }
                        .toMutableSet()
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
