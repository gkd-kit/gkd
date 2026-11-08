package li.gkd.app.matching.diagnostics

import kotlinx.serialization.json.*
import li.gkd.app.util.JsonUtils

/** Query headers and shared selector plans are independent of evictable node-call detail. */
fun querySummarySections(queries: List<MatchQueryRecording>): Map<String, JsonElement> {
    val plans = linkedMapOf<MatchSelectorPlan, Int>()
    val summaries = queries.map { query ->
        buildJsonObject {
            put("queryId", query.queryId)
            put("planId", plans.getOrPut(query.plan) { plans.size })
            put("runId", query.runId)
            put("rule", query.rule)
            put("source", JsonUtils.default.encodeToJsonElement(query.source))
            query.result?.let { put("result", JsonUtils.default.encodeToJsonElement(it)) }
            query.failure?.let { put("failure", it) }
            query.incomplete?.let { put("incomplete", it) }
            query.operationId?.let { put("operationId", it) }
            put("completed", query.completed)
            query.startedAt?.let { put("startedAt", it) }
            query.startedNanos?.let { put("startedNanos", it) }
            query.endedAt?.let { put("endedAt", it) }
            query.durationNanos?.let { put("durationNanos", it) }
            query.outcome?.let { put("outcome", it) }
            put("detailsRetained", query.detailsRetained)
            query.retainedFor?.let { put("retainedFor", it) }
        }
    }
    return mapOf(
        "querySummaries" to JsonArray(summaries),
        "queryPlans" to JsonUtils.default.encodeToJsonElement(plans.keys.toList()),
    )
}

/** A local text table avoids repeating long values within one query and can be evicted with it. */
fun queryDetailJson(query: MatchQueryRecording): JsonObject {
    val detail =
        JsonObject(
            mapOf(
                "queryId" to JsonPrimitive(query.queryId),
                "calls" to JsonUtils.default.encodeToJsonElement(query.calls),
                "operations" to JsonUtils.default.encodeToJsonElement(query.operations),
            ),
        )
    val counts = mutableMapOf<String, Int>()
    fun count(value: JsonElement) {
        when (value) {
            is JsonArray -> value.forEach(::count)
            is JsonObject -> {
                if (value["type"] == JsonPrimitive("string")) {
                    val text = value["value"]?.jsonPrimitive?.content
                    if (text != null && text.length >= 64) counts[text] = (counts[text] ?: 0) + 1
                }
                value.values.forEach(::count)
            }
            else -> Unit
        }
    }
    count(detail)
    val texts = counts.filterValues { it > 1 }.keys.toList()
    val ids = texts.withIndex().associate { it.value to it.index }
    fun replace(value: JsonElement): JsonElement =
        when (value) {
            is JsonArray -> JsonArray(value.map(::replace))
            is JsonObject -> {
                val id =
                    if (value["type"] == JsonPrimitive("string"))
                        ids[value["value"]?.jsonPrimitive?.content]
                    else null
                if (id != null)
                    JsonObject(
                        value.filterKeys { it != "value" }.mapValues { replace(it.value) } +
                            ("valueRef" to JsonPrimitive(id)),
                    )
                else JsonObject(value.mapValues { replace(it.value) })
            }
            else -> value
        }
    return JsonObject(
        (if (texts.isEmpty()) detail else replace(detail).jsonObject) +
            ("texts" to JsonUtils.default.encodeToJsonElement(texts)),
    )
}
