package li.gkd.app.matching.diagnostics

import java.io.OutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.encodeToStream
import li.gkd.app.util.JsonUtils

private const val EXPORT_LIMIT_BYTES = 20_000_000
private const val DETAIL_LIMIT_BYTES = 10_000_000L
private const val KEY_LIMIT_BYTES = 4_000_000L
private const val BOUNDARY_LIMIT_BYTES = 2_000_000L

/** Shared by file and HTTP export. Serialization never runs on the event admission path. */
fun encodeDiagnosticCapture(value: MatchDiagnosticCapture): String =
    JsonUtils.keepingNulls.encodeToString(prepareDiagnosticCapture(value))

fun prepareDiagnosticCapture(value: MatchDiagnosticCapture): MatchDiagnosticCapture =
    value
        .copy(
            events = value.events.filter { !it.isKeyTrace() && !it.isBoundaryTrace() },
            keyEvents = value.keyEvents.filter { !it.isBoundaryTrace() },
        )
        .limitExportSize()

/** Exclude separately stored payloads when measuring or writing session metadata. */
fun MatchDiagnosticCapture.metadataOnly(): MatchDiagnosticCapture =
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

private fun MatchDiagnosticCapture.limitExportSize(): MatchDiagnosticCapture {
    val omissions = omittedForSize.toMutableMap()
    fun omitted(section: String, count: Int) {
        if (count > 0) omissions[section] = (omissions[section] ?: 0) + count
    }
    fun retainTimeline(section: String, values: List<MatchTrace>, budget: Long): List<MatchTrace> {
        val retained = retainNewest(values, budget)
        omitted(section, values.size - retained.size)
        return retained
    }

    // Independent budgets: successful matching cannot consume foreground/failure evidence space.
    val detail = retainTimeline("events", events, DETAIL_LIMIT_BYTES)
    val keys = retainTimeline("keyEvents", keyEvents, KEY_LIMIT_BYTES)
    val boundaries = retainTimeline("boundaryEvents", boundaryEvents, BOUNDARY_LIMIT_BYTES)
    var capture =
        copy(
            session = session.copy(dropped = session.dropped + events.size - detail.size),
            events = detail,
            keyEvents = keys,
            droppedKeyEvents = droppedKeyEvents + keyEvents.size - keys.size,
            boundaryEvents = boundaries,
            droppedBoundaryEvents = droppedBoundaryEvents + boundaryEvents.size - boundaries.size,
            sizeLimitBytes = EXPORT_LIMIT_BYTES,
            omittedForSize = omissions.toMap(),
        )
    val queries = retainNewest(capture.queryRecordings, 2_000_000L)
    omitted("queryRecordings", capture.queryRecordings.size - queries.size)
    val recording =
        capture.systemRecording?.let { recording ->
            var stepBytes =
                jsonBytes(
                    recording.copy(
                        steps = emptyList(),
                        incomplete = recording.incomplete ?: "export_size_limit",
                    ),
                )
            val steps =
                recording.steps.takeWhile {
                    stepBytes += jsonBytes(it) + 1
                    stepBytes <= 1_000_000L
                }
            omitted("systemSteps", recording.steps.size - steps.size)
            recording.copy(
                steps = steps,
                incomplete =
                    if (steps.size < recording.steps.size)
                        recording.incomplete ?: "export_size_limit"
                    else recording.incomplete,
            )
        }
    capture =
        capture.copy(
            queryRecordings = queries,
            droppedQueryRecordings =
                capture.droppedQueryRecordings + capture.queryRecordings.size - queries.size,
            systemRecording = recording,
            omittedForSize = omissions.toMap(),
        )
    val settings = retainNewest(capture.accessibilitySettings, 500_000L)
    omitted("accessibilitySettings", capture.accessibilitySettings.size - settings.size)
    capture =
        capture.copy(
            accessibilitySettings = settings,
            droppedAccessibilitySettings =
                capture.droppedAccessibilitySettings + capture.accessibilitySettings.size -
                    settings.size,
            omittedForSize = omissions.toMap(),
        )
    var bytes = jsonBytes(capture.metadataOnly())
    val payloadBytes = jsonBytes(capture) - bytes
    val effectiveLimit = minOf(490_000L, EXPORT_LIMIT_BYTES - payloadBytes)
    fun replace(next: MatchDiagnosticCapture) {
        capture = next.copy(omittedForSize = omissions.toMap())
        bytes = jsonBytes(capture.metadataOnly())
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
    return capture
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
    JsonUtils.keepingNulls.encodeToStream(value, counter)
    return counter.size
}
