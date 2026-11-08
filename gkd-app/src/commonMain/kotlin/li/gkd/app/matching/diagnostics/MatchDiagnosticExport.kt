package li.gkd.app.matching.diagnostics

import java.io.OutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import li.gkd.app.util.JsonUtils

private const val EXPORT_LIMIT_BYTES = 20_000_000
private const val KEY_BASELINE_BYTES = 4_000_000L
private const val BOUNDARY_BASELINE_BYTES = 2_000_000L

/** Shared by file and HTTP export. Serialization never runs on the event admission path. */
fun encodeDiagnosticCapture(value: MatchDiagnosticCapture): String =
    prepareDiagnosticArchive(value).toString()

fun prepareDiagnosticArchive(value: MatchDiagnosticCapture): JsonObject =
    value
        .copy(
            queryRecordings =
                value.queryRecordings.mapIndexed { index, query ->
                    if (query.queryId == 0L) query.copy(queryId = index + 1L) else query
                },
            events = value.events.filter { !it.isKeyTrace() && !it.isBoundaryTrace() },
            keyEvents = value.keyEvents.filter { !it.isBoundaryTrace() },
        )
        .limitExportSize()

/** Exclude separately stored payloads when measuring or writing session metadata. */
private fun MatchDiagnosticCapture.metadataOnly(): MatchDiagnosticCapture =
    copy(
        events = emptyList(),
        keyEvents = emptyList(),
        boundaryEvents = emptyList(),
        queryRecordings = emptyList(),
        systemRecording = null,
        accessibilitySettings = emptyList(),
    )

private inline fun <reified T> retainNewest(values: List<T>, budget: Long): List<T> {
    var bytes = 2L // JSON array brackets; JSONL is no larger.
    var count = 0
    for (value in values.asReversed()) {
        val size = jsonBytes(value) + if (count == 0) 0 else 1
        if (bytes + size > budget) break
        bytes += size
        count++
    }
    return values.takeLast(count)
}

/** Reallocates only unused bytes; another section's retained evidence is never displaced. */
private class DiagnosticBudget<T>(
    baseline: Long,
    private val select: (Long) -> T,
    private val measure: (T) -> Long,
) {
    var value = select(baseline)
        private set

    var bytes = measure(value)
        private set

    fun expand(available: Long): Long {
        val next = select(bytes + available)
        if (next == value) return 0
        val before = bytes
        value = next
        bytes = measure(value)
        return bytes - before
    }
}

private fun <T> retainRecent(values: List<T>, budget: Long, measure: (List<T>) -> Long): List<T> {
    if (measure(values) <= budget) return values
    var low = 0
    var high = values.size
    while (low < high) {
        val middle = (low + high + 1) / 2
        if (measure(values.takeLast(middle)) <= budget) low = middle else high = middle - 1
    }
    return values.takeLast(low)
}

private fun MatchDiagnosticCapture.limitExportSize(): JsonObject {
    val omissions = omittedForSize.toMutableMap()
    fun omitted(section: String, count: Int) {
        if (count > 0) omissions[section] = (omissions[section] ?: 0) + count
    }
    fun timeline(values: List<MatchTrace>, baseline: Long) =
        DiagnosticBudget(
            baseline,
            { retainNewest(values, it) },
            { jsonBytes(it) },
        )
    fun summaryBytes(records: List<MatchQueryRecording>) =
        jsonBytes(
            JsonObject(querySummarySections(records.map { it.copy(detailsRetained = false) })),
        )
    val summaries =
        DiagnosticBudget(
            1_000_000L,
            { budget -> retainRecent(queryRecordings, budget, ::summaryBytes) },
            ::summaryBytes,
        )
    val encodedDetails =
        queryRecordings
            .filter { it.detailsRetained }
            .sortedWith(
                compareBy<MatchQueryRecording> { it.retainedFor != null }.thenBy { it.queryId },
            )
            .map(::queryDetailJson)
    val details =
        DiagnosticBudget(
            2_000_000L,
            { budget ->
                val ids = summaries.value.mapTo(mutableSetOf()) { it.queryId }
                retainNewest(
                    encodedDetails.filter { it.getValue("queryId").jsonPrimitive.long in ids },
                    budget,
                )
            },
            { jsonBytes(it) },
        )
    val detail = timeline(events, 9_000_000L)
    val keys = timeline(keyEvents, KEY_BASELINE_BYTES)
    val boundaries = timeline(boundaryEvents, BOUNDARY_BASELINE_BYTES)
    val system =
        DiagnosticBudget(
            1_000_000L,
            { budget ->
                systemRecording?.let { recording ->
                    if (jsonBytes(recording) <= budget) return@let recording
                    var bytes =
                        jsonBytes(
                            recording.copy(
                                steps = emptyList(),
                                incomplete = recording.incomplete ?: "export_size_limit",
                            ),
                        )
                    val steps =
                        recording.steps
                            .asSequence()
                            .withIndex()
                            .takeWhile { (index, step) ->
                                bytes += jsonBytes(step) + if (index == 0) 0 else 1
                                bytes <= budget
                            }
                            .map { it.value }
                            .toList()
                    recording.copy(
                        steps = steps,
                        incomplete =
                            if (steps.size < recording.steps.size)
                                recording.incomplete ?: "export_size_limit"
                            else recording.incomplete,
                    )
                }
            },
            { jsonBytes(it) },
        )
    val settings =
        DiagnosticBudget(500_000L, { retainNewest(accessibilitySettings, it) }, { jsonBytes(it) })
    val sections = listOf(summaries, details, detail, keys, boundaries, system, settings)
    var remaining = 19_500_000L - sections.sumOf { it.bytes }
    do {
        var used = 0L
        for (section in sections) {
            val extra = section.expand(remaining)
            remaining -= extra
            used += extra
        }
    } while (used > 0 && remaining > 0)

    val retainedIds =
        details.value.mapTo(mutableSetOf()) { it.getValue("queryId").jsonPrimitive.long }
    val queries =
        summaries.value.map {
            if (it.queryId in retainedIds) it
            else it.copy(calls = emptyList(), operations = emptyList(), detailsRetained = false)
        }
    val lostDetails = queryRecordings.count { it.detailsRetained } - retainedIds.size
    omitted("queryRecordings", lostDetails)
    omitted("querySummaries", queryRecordings.size - queries.size)
    omitted("events", events.size - detail.value.size)
    omitted("keyEvents", keyEvents.size - keys.value.size)
    omitted("boundaryEvents", boundaryEvents.size - boundaries.value.size)
    omitted("systemSteps", (systemRecording?.steps?.size ?: 0) - (system.value?.steps?.size ?: 0))
    omitted("accessibilitySettings", accessibilitySettings.size - settings.value.size)
    var capture =
        copy(
            session = session.copy(dropped = session.dropped + events.size - detail.value.size),
            events = detail.value,
            keyEvents = keys.value,
            boundaryEvents = boundaries.value,
            queryRecordings = queries,
            droppedQueryRecordings = droppedQueryRecordings + lostDetails,
            droppedQuerySummaries = droppedQuerySummaries + queryRecordings.size - queries.size,
            droppedKeyEvents = droppedKeyEvents + keyEvents.size - keys.value.size,
            droppedBoundaryEvents =
                droppedBoundaryEvents + boundaryEvents.size - boundaries.value.size,
            systemRecording = system.value,
            accessibilitySettings = settings.value,
            droppedAccessibilitySettings =
                droppedAccessibilitySettings + accessibilitySettings.size - settings.value.size,
            sizeLimitBytes = EXPORT_LIMIT_BYTES,
            omittedForSize = omissions.toMap(),
        )
    val retainedDetails = details.value.associateBy { it.getValue("queryId").jsonPrimitive.long }
    val payload =
        mapOf(
            "events" to JsonUtils.default.encodeToJsonElement(capture.events),
            "keyEvents" to JsonUtils.default.encodeToJsonElement(capture.keyEvents),
            "boundaryEvents" to JsonUtils.default.encodeToJsonElement(capture.boundaryEvents),
            "accessibilitySettings" to
                JsonUtils.default.encodeToJsonElement(capture.accessibilitySettings),
            "systemRecording" to JsonUtils.default.encodeToJsonElement(capture.systemRecording),
            "queryDetails" to JsonArray(queries.mapNotNull { retainedDetails[it.queryId] }),
        ) + querySummarySections(queries)
    fun metadata() =
        JsonObject(
            JsonUtils.default.encodeToJsonElement(capture.metadataOnly()).jsonObject.filterKeys {
                it !in payload && it != "queryRecordings"
            } + ("formatVersion" to JsonPrimitive(2)),
        )
    var encodedMetadata = metadata()
    var bytes = jsonBytes(encodedMetadata)
    // Combining two nonempty objects removes two braces and adds one separator.
    val payloadBytes = jsonBytes(JsonObject(payload)) - 1
    val effectiveLimit = minOf(490_000L, EXPORT_LIMIT_BYTES - payloadBytes)
    fun replace(next: MatchDiagnosticCapture) {
        capture = next.copy(omittedForSize = omissions.toMap())
        encodedMetadata = metadata()
        bytes = jsonBytes(encodedMetadata)
    }

    // Metadata has its own budget; it cannot consume any timeline or recording allowance.
    fun trimSection(
        name: String,
        sizes: () -> List<Long>,
        retain: (Int) -> MatchDiagnosticCapture,
    ) {
        if (bytes <= effectiveLimit) return
        val itemSizes = sizes()
        if (itemSizes.isEmpty()) return
        val budget = itemSizes.sum() + itemSizes.size - (bytes - effectiveLimit) - 1024
        var keptBytes = 0L
        var count = 0
        for (size in itemSizes.asReversed()) {
            if (keptBytes + size + 1 > budget) break
            keptBytes += size + 1
            count++
        }
        omitted(name, itemSizes.size - count)
        replace(retain(count))
    }

    trimSection("runs", { capture.runs.map { jsonBytes(it) } }) { count ->
        capture.copy(runs = capture.runs.takeLast(count))
    }
    while (bytes > effectiveLimit && capture.ruleSnapshots.isNotEmpty()) {
        val snapshots = capture.ruleSnapshots.drop(1)
        val referenced = snapshots.flatMapTo(mutableSetOf()) { it.definitions }
        val definitions = capture.ruleDefinitions.filterKeys { it in referenced }
        omitted("ruleSnapshots", 1)
        omitted("ruleDefinitions", capture.ruleDefinitions.size - definitions.size)
        replace(
            capture.copy(
                ruleSnapshots = snapshots,
                droppedRuleSnapshots = capture.droppedRuleSnapshots + 1,
                ruleDefinitions = definitions,
            ),
        )
    }
    capture.initialRules?.let { rules ->
        trimSection("initialRules", { rules.map { jsonBytes(it) } }) { count ->
            capture.copy(initialRules = rules.takeLast(count))
        }
    }
    capture.operationStatistics?.let { statistics ->
        trimSection("operationStatistics", { statistics.totals.map { jsonBytes(it) } }) { count ->
            capture.copy(
                operationStatistics =
                    statistics.copy(
                        totals = statistics.totals.takeLast(count),
                        omitted =
                            statistics.omitted +
                                statistics.totals.dropLast(count).sumOf { it.count },
                    ),
            )
        }
    }
    trimSection("entries", { capture.entries.map { jsonBytes(it) } }) { count ->
        capture.copy(entries = capture.entries.takeLast(count))
    }
    if (bytes > effectiveLimit && capture.runtime != null) {
        omitted("runtime", 1)
        replace(capture.copy(runtime = null, runtimeCapturedAt = null))
    }
    check(bytes <= effectiveLimit) {
        "Diagnostic metadata exceeds its $effectiveLimit-byte budget"
    }
    return JsonObject(encodedMetadata + payload)
}

/** Count actual UTF-8 JSON bytes without allocating a serialized copy of the entire capture. */
private class JsonByteCounter : OutputStream() {
    var size = 0L
        private set

    override fun write(value: Int) {
        size++
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        size += length
    }
}

@OptIn(ExperimentalSerializationApi::class)
private inline fun <reified T> jsonBytes(value: T): Long {
    val counter = JsonByteCounter()
    JsonUtils.default.encodeToStream(value, counter)
    return counter.size
}
