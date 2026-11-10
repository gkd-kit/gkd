package li.gkd.app.matching.diagnostics

import java.io.OutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import li.gkd.app.matching.input.MatchTimingPolicy
import li.gkd.app.util.JsonUtils

const val MATCH_DIAGNOSTIC_EXPORT_LIMIT_BYTES = 40_000_000
private const val METADATA_ALLOWANCE_BYTES = 500_000L

/** Shared by file and HTTP export. Serialization never runs on the event admission path. */
fun prepareDiagnosticArchive(value: MatchDiagnosticCapture): MatchDiagnosticArchive =
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

/** An object becomes [shape index, values...]. A JSON array uses -1 in place of the shape. */
private class DiagnosticShapes {
    private val shapes = linkedMapOf<Set<String>, Int>()
    private val fields = mutableListOf<JsonArray>()
    private val arrayTag = JsonPrimitive(-1)
    var bytes = 2L
        private set

    val size: Int
        get() = fields.size

    fun table(count: Int = size) = JsonArray(fields.take(count))

    fun pack(value: JsonElement): JsonElement =
        when (value) {
            is JsonObject -> {
                val index =
                    shapes[value.keys]
                        ?: run {
                            val names = JsonArray(value.keys.map(::JsonPrimitive))
                            bytes += jsonBytes(names) + 1
                            fields.add(names)
                            shapes[value.keys.toSet()] = fields.lastIndex
                            fields.lastIndex
                        }
                JsonArray(
                    listOf(JsonPrimitive(index)) +
                        fields[index].map {
                            pack(value.getValue(it.jsonPrimitive.content))
                        },
                )
            }
            is JsonArray -> JsonArray(listOf(arrayTag) + value.map(::pack))
            else -> value
        }
}

private data class EncodedRow(val text: String, val bytes: Int) {
    constructor(value: JsonElement) : this(value.toString())

    private constructor(text: String) : this(text, text.toByteArray(Charsets.UTF_8).size)
}

private data class EncodedTimeline(
    val rows: List<EncodedRow>,
    val strings: JsonArray,
    val shapes: JsonArray?,
    val bytes: Long,
)

/** Keeps encoded rows across shared-budget passes and examines at most one row past the limit. */
private class TimelineEncoder(
    values: List<MatchTrace>,
    private val repeated: Set<String>,
    private val sessionPolicy: MatchTimingPolicy,
    packed: Boolean,
) {
    private val remaining = values.asReversed().iterator()
    private val rows = mutableListOf<EncodedRow>()
    private val indices = mutableMapOf<String, Int>()
    private val strings = mutableListOf<JsonPrimitive>()
    private val shapes = if (packed) DiagnosticShapes() else null
    private var stringBytes = 2L
    private var acceptedStringCount = 0
    private var acceptedShapeCount = 0
    private var bytes = 4L + (shapes?.bytes ?: 0L)
    private var pending: EncodedRow? = null
    private var pendingBytes = 0L

    fun retain(budget: Long): EncodedTimeline {
        while (pending != null || remaining.hasNext()) {
            if (pending == null) {
                val before = stringBytes + (shapes?.bytes ?: 0L)
                val row = encode(remaining.next())
                pending = row
                pendingBytes = row.bytes + 1L + stringBytes + (shapes?.bytes ?: 0L) - before
            }
            if (bytes + pendingBytes > budget) break
            rows.add(checkNotNull(pending))
            pending = null
            bytes += pendingBytes
            acceptedStringCount = strings.size
            acceptedShapeCount = shapes?.size ?: 0
        }
        return EncodedTimeline(
            rows.toList().asReversed(),
            JsonArray(strings.take(acceptedStringCount)),
            shapes?.table(acceptedShapeCount),
            bytes,
        )
    }

    private fun encode(trace: MatchTrace): EncodedRow {
        fun reference(value: String?): JsonPrimitive? {
            if (value == null || value !in repeated) return null
            val index =
                indices.getOrPut(value) {
                    val entry = JsonPrimitive(value)
                    stringBytes += jsonBytes(entry) + 1
                    strings.add(entry)
                    strings.lastIndex
                }
            return JsonPrimitive(index)
        }
        val fields = JsonUtils.default.encodeToJsonElement(trace).jsonObject.toMutableMap()
        reference(trace.rule)?.let { fields["rule"] = it }
        reference(trace.condition?.selector)?.let {
            fields["condition"] =
                JsonObject(fields.getValue("condition").jsonObject + ("selector" to it))
        }
        reference(trace.operation?.rule)?.let {
            fields["operation"] =
                JsonObject(fields.getValue("operation").jsonObject + ("rule" to it))
        }
        if (trace.input?.policy == sessionPolicy) {
            fields["input"] = JsonObject(fields.getValue("input").jsonObject - "policy")
        }
        val row = JsonObject(fields)
        return EncodedRow(shapes?.pack(row) ?: row)
    }
}

private fun packedQueryDetail(query: MatchQueryRecording): EncodedRow {
    val shapes = DiagnosticShapes()
    val row = shapes.pack(queryDetailJson(query))
    return EncodedRow(JsonArray(listOf(shapes.table(), row)))
}

private fun rowsBytes(rows: List<EncodedRow>): Long = 2L + rows.sumOf { it.bytes + 1L }

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

class MatchDiagnosticArchive(
    val metadata: JsonObject,
    private val sections: Map<String, JsonElement>,
    private val encodedRows: Map<String, List<String>>,
) {
    fun writeSection(name: String, lines: Boolean, output: Appendable) {
        val rows = encodedRows[name]
        if (rows != null) {
            if (lines) rows.forEach { output.append(it).append('\n') }
            else rows.joinTo(output, separator = ",", prefix = "[", postfix = "]")
        } else {
            val value = sections.getValue(name)
            if (lines) value.jsonArray.forEach { output.append(it.toString()).append('\n') }
            else output.append(value.toString())
        }
    }

    fun writeJson(output: Appendable) {
        output.append('{')
        metadata.entries.joinTo(output, separator = ",") { (name, value) ->
            "${JsonPrimitive(name)}:$value"
        }
        for (name in sections.keys + encodedRows.keys) {
            output.append(',').append(JsonPrimitive(name).toString()).append(':')
            writeSection(name, lines = false, output)
        }
        output.append('}')
    }
}

private fun MatchDiagnosticCapture.limitExportSize(): MatchDiagnosticArchive {
    val omissions = omittedForSize.toMutableMap()
    fun omitted(section: String, count: Int) {
        if (count > 0) omissions[section] = (omissions[section] ?: 0) + count
    }
    fun timeline(
        values: List<MatchTrace>,
        baseline: Long,
        packed: Boolean = false,
    ): DiagnosticBudget<EncodedTimeline> {
        val repeated = mutableSetOf<String>()
        val seen = mutableSetOf<String>()
        for (trace in values) {
            for (value in
                listOfNotNull(trace.rule, trace.operation?.rule, trace.condition?.selector)) {
                if (value.length >= 16 && !seen.add(value)) repeated.add(value)
            }
        }
        val encoder = TimelineEncoder(values, repeated, session.policy, packed)
        return DiagnosticBudget(baseline, encoder::retain, { it.bytes })
    }
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
    val detailCandidates =
        queryRecordings
            .filter { it.detailsRetained }
            .sortedWith(
                compareBy<MatchQueryRecording> { it.retainedFor != null }.thenBy { it.queryId },
            )
            .asReversed()
    val encodedDetails = mutableMapOf<Long, EncodedRow>()
    val details =
        DiagnosticBudget(
            4_000_000L,
            { budget ->
                val ids = summaries.value.mapTo(mutableSetOf()) { it.queryId }
                buildMap {
                    var bytes = 2L
                    for (query in detailCandidates) {
                        if (query.queryId !in ids) continue
                        val row =
                            encodedDetails.getOrPut(query.queryId) { packedQueryDetail(query) }
                        if (bytes + row.bytes + 1 > budget) break
                        put(query.queryId, row)
                        bytes += row.bytes + 1
                    }
                }
            },
            { 2L + it.values.sumOf { row -> row.bytes + 1L } },
        )
    val detail = timeline(events, 9_000_000L, packed = true)
    val keys = timeline(keyEvents, 18_000_000L)
    val boundaries = timeline(boundaryEvents, 4_000_000L)
    val system =
        DiagnosticBudget(
            3_000_000L,
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
    var remaining =
        MATCH_DIAGNOSTIC_EXPORT_LIMIT_BYTES - METADATA_ALLOWANCE_BYTES - sections.sumOf { it.bytes }
    do {
        var used = 0L
        for (section in sections) {
            val extra = section.expand(remaining)
            remaining -= extra
            used += extra
        }
    } while (used > 0 && remaining > 0)

    val retainedIds = details.value.keys
    val queries =
        summaries.value.map {
            if (it.queryId in retainedIds) it
            else it.copy(calls = emptyList(), operations = emptyList(), detailsRetained = false)
        }
    val lostDetails = queryRecordings.count { it.detailsRetained } - retainedIds.size
    omitted("queryRecordings", lostDetails)
    omitted("querySummaries", queryRecordings.size - queries.size)
    val timelines =
        mapOf(
            "events" to detail.value,
            "keyEvents" to keys.value,
            "boundaryEvents" to boundaries.value,
        )
    val lostEvents = events.size - detail.value.rows.size
    val lostKeyEvents = keyEvents.size - keys.value.rows.size
    val lostBoundaryEvents = boundaryEvents.size - boundaries.value.rows.size
    omitted("events", lostEvents)
    omitted("keyEvents", lostKeyEvents)
    omitted("boundaryEvents", lostBoundaryEvents)
    omitted("systemSteps", (systemRecording?.steps?.size ?: 0) - (system.value?.steps?.size ?: 0))
    omitted("accessibilitySettings", accessibilitySettings.size - settings.value.size)
    var capture =
        copy(
            session = session.copy(dropped = session.dropped + lostEvents),
            queryRecordings = queries,
            droppedQueryRecordings = droppedQueryRecordings + lostDetails,
            droppedQuerySummaries = droppedQuerySummaries + queryRecordings.size - queries.size,
            droppedKeyEvents = droppedKeyEvents + lostKeyEvents,
            droppedBoundaryEvents = droppedBoundaryEvents + lostBoundaryEvents,
            systemRecording = system.value,
            accessibilitySettings = settings.value,
            droppedAccessibilitySettings =
                droppedAccessibilitySettings + accessibilitySettings.size - settings.value.size,
            sizeLimitBytes = MATCH_DIAGNOSTIC_EXPORT_LIMIT_BYTES,
            omittedForSize = omissions.toMap(),
        )
    val archiveRows =
        timelines.mapValues { it.value.rows } +
            ("queryDetails" to queries.mapNotNull { details.value[it.queryId] })
    val payload =
        mapOf(
            "traceStrings" to JsonObject(timelines.mapValues { it.value.strings }),
            "eventShapes" to checkNotNull(detail.value.shapes),
            "accessibilitySettings" to
                JsonUtils.default.encodeToJsonElement(capture.accessibilitySettings),
            "systemRecording" to JsonUtils.default.encodeToJsonElement(capture.systemRecording),
        ) + querySummarySections(queries)
    fun metadata() =
        JsonObject(
            JsonUtils.default.encodeToJsonElement(capture.metadataOnly()).jsonObject.filterKeys {
                it !in payload && it !in archiveRows && it != "queryRecordings"
            } + ("formatVersion" to JsonPrimitive(4)),
        )
    var encodedMetadata = metadata()
    var bytes = jsonBytes(encodedMetadata)
    // Each payload field follows metadata with a comma, its quoted name and a colon.
    val payloadBytes =
        payload.entries.sumOf { (name, value) ->
            jsonBytes(JsonPrimitive(name)) + 2 + jsonBytes(value)
        } +
            archiveRows.entries.sumOf { (name, rows) ->
                jsonBytes(JsonPrimitive(name)) + 2 + rowsBytes(rows)
            }
    val effectiveLimit = minOf(490_000L, MATCH_DIAGNOSTIC_EXPORT_LIMIT_BYTES - payloadBytes)
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
    return MatchDiagnosticArchive(
        encodedMetadata,
        payload,
        archiveRows.mapValues { it.value.map(EncodedRow::text) },
    )
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
