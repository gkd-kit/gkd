package li.gkd.app.matching.diagnostics

import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import li.gkd.app.matching.MatchCoordinator
import li.gkd.app.matching.MatchEngineSnapshot
import li.gkd.app.matching.input.MatchTimingPolicy
import li.gkd.app.testing.TestFiles

class MatchDiagnosticFileStoreTest {
    @Test
    fun metadataTrimmingPreservesIndependentEventPayloadAndLossCounts() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy())
        diagnostics.entry("root_read", "root_unavailable")
        diagnostics.stop(session.id)
        val capture =
            diagnostics
                .export(session.id)
                .copy(
                    entries = List(1_000) { MatchEntrySummary("source-$it", "汉".repeat(1_000), 1) },
                )
        val restored = decodeArchive(encodeDiagnosticCapture(capture))
        assertEquals(capture.boundaryEvents, restored.boundaryEvents)
        assertTrue(restored.entries.size in 1 until capture.entries.size)
        assertEquals(capture.entries.last(), restored.entries.last())
        assertEquals(
            capture.entries.size - restored.entries.size,
            restored.omittedForSize.getValue("entries"),
        )
        assertEquals(
            restored,
            decodeArchive(encodeDiagnosticCapture(restored)),
        )
    }

    private fun decodeArchive(text: String): MatchDiagnosticCapture {
        val root = Json.parseToJsonElement(text).jsonObject
        val plans = root.getValue("queryPlans").jsonArray
        val details =
            root.getValue("queryDetails").jsonArray.associateBy {
                it.jsonObject.getValue("queryId")
            }
        val queries =
            root.getValue("querySummaries").jsonArray.map { value ->
                val summary = value.jsonObject
                val detail = details[summary.getValue("queryId")]?.jsonObject
                val texts = detail?.get("texts")?.jsonArray ?: JsonArray(emptyList())
                fun expand(value: JsonElement): JsonElement =
                    when (value) {
                        is JsonArray -> JsonArray(value.map(::expand))
                        is JsonObject -> {
                            val ref = value["valueRef"]?.jsonPrimitive?.int
                            JsonObject(
                                value
                                    .filterKeys { it != "valueRef" }
                                    .mapValues { expand(it.value) } +
                                    if (ref == null) emptyMap() else mapOf("value" to texts[ref]),
                            )
                        }
                        else -> value
                    }
                JsonObject(
                    summary.filterKeys { it != "planId" } +
                        mapOf(
                            "plan" to plans[summary.getValue("planId").jsonPrimitive.int],
                            "calls" to expand(detail?.get("calls") ?: JsonArray(emptyList())),
                            "operations" to
                                expand(detail?.get("operations") ?: JsonArray(emptyList())),
                        ),
                )
            }
        return li.gkd.app.util.JsonUtils.default.decodeFromJsonElement(
            JsonObject(
                root.filterKeys {
                    it !in setOf("formatVersion", "queryPlans", "queryDetails", "querySummaries")
                } + ("queryRecordings" to JsonArray(queries)),
            ),
        )
    }

    private fun java.io.File.readCapture(): String {
        val metadata = Json.parseToJsonElement(readText()).jsonObject
        val sections =
            metadata.getValue("files").jsonObject.mapValues { (key, name) ->
                if (name.jsonPrimitive.content.endsWith(".json"))
                    Json.parseToJsonElement(
                        parentFile.resolve(name.jsonPrimitive.content).readText(),
                    )
                else
                    JsonArray(
                        parentFile
                            .resolve(name.jsonPrimitive.content)
                            .readLines()
                            .filter { it.isNotEmpty() }
                            .map { Json.parseToJsonElement(it) },
                    )
            }
        return JsonObject(metadata.filterKeys { it != "files" } + sections).toString()
    }

    private fun canonical(capture: MatchDiagnosticCapture) =
        decodeArchive(encodeDiagnosticCapture(capture))

    /** Large Unicode node recordings share a 20 MB file budget with independent event timelines. */
    @Test
    fun exportCapsActualUtf8BytesAndPreservesFailuresWhenRecoveryFillsTheFile() {
        val directory =
            Files.createTempDirectory(TestFiles.root.toPath(), "diagnostic-budget-").toFile()
        try {
            val diagnostics = MatchDiagnostics()
            val session = diagnostics.start(MatchTimingPolicy())
            diagnostics.entry("root_read", "root_unavailable")
            diagnostics.entry("foreground_confirmation", "foreground_wait_expired")
            repeat(25_000) { diagnostics.record(1, MatchStage.Selector, "matches[0]:miss") }
            diagnostics.stop(session.id)
            val call =
                MatchNodeCall(
                    MatchNodeOperation.Attr,
                    listOf(MatchRecordedValue(MatchRecordedValueType.String, "text")),
                    0,
                    1,
                    "reader",
                    result =
                        MatchRecordedValue(MatchRecordedValueType.String, "汉字\"\n".repeat(2_000)),
                )
            val query =
                MatchQueryRecording(
                    1,
                    "rule",
                    MatchSelectorPlan(listOf("[text]")),
                    MatchRecordedValue(MatchRecordedValueType.Reference, reference = 1),
                    List(30) { index ->
                        call.copy(result = call.result!!.copy(value = call.result.value + index))
                    },
                    null,
                    null,
                    null,
                )
            val capture =
                diagnostics
                    .export(session.id)
                    .copy(
                        queryRecordings =
                            List(32) {
                                query.copy(
                                    runId = it.toLong(),
                                    queryId = it + 1L,
                                    retainedFor = if (it == 0) "query_failed" else null,
                                )
                            },
                        accessibilitySettings =
                            List(2_000) { index ->
                                AccessibilitySettingRecord(
                                    index.toLong(),
                                    index.toLong(),
                                    "changed",
                                    0,
                                    "enabled_accessibility_services",
                                    "应用".repeat(400),
                                    "android",
                                )
                            },
                    )
            assertTrue(capture.events.size > 2_000)
            // Queries exceeding the old 2 MB ceiling fit when other sections leave room.
            val smaller = capture.copy(queryRecordings = capture.queryRecordings.takeLast(8))
            val shared = decodeArchive(prepareDiagnosticArchive(smaller).toString())
            assertEquals(smaller.queryRecordings, shared.queryRecordings)
            assertEquals(smaller.boundaryEvents, shared.boundaryEvents)
            assertEquals(0, shared.omittedForSize["queryRecordings"] ?: 0)
            val store = MatchDiagnosticFileStore(directory)
            val saved = store.save(capture)!!
            val file = store.directory(saved.id).resolve("diagnostic.json")
            assertTrue(saved.sizeBytes <= 20_000_000)
            assertTrue(
                saved.sizeBytes > 2_000_000,
                "Keep useful detail rather than clearing the file",
            )
            assertEquals(
                file.parentFile.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                saved.sizeBytes,
            )
            val restored = decodeArchive(file.readCapture())
            assertEquals(20_000_000, restored.sizeLimitBytes)
            val storedEvents = restored.events + restored.keyEvents + restored.boundaryEvents
            assertEquals(storedEvents.size, storedEvents.map { it.sequence }.toSet().size)
            assertTrue(file.parentFile.resolve("boundary-events.jsonl").length() <= 2_000_000)
            assertTrue(
                file.parentFile.resolve("query-details.jsonl").length() > 2_000_000,
                "Queries borrow unused detail and boundary space",
            )
            assertTrue(file.parentFile.resolve("system-recording.json").length() <= 1_000_000)
            assertEquals(1999L, restored.accessibilitySettings.last().notifiedAt)
            assertEquals(capture.boundaryEvents, restored.boundaryEvents)
            assertEquals(0L, restored.droppedBoundaryEvents)
            assertTrue(restored.keyEvents.size > 2_000)
            assertTrue(restored.omittedForSize.getValue("queryRecordings") > 0)
            assertEquals(31L, restored.queryRecordings.last().runId)
            assertEquals(
                32,
                restored.queryRecordings.size,
                "Detail trimming must not erase query summaries",
            )
            assertTrue(
                restored.queryRecordings.first().detailsRetained,
                "Fault detail precedes newer ordinary queries in the export budget",
            )
            assertTrue(restored.queryRecordings.last().detailsRetained)
            // Unused query slots are returned to other sections, including small event records.
            val payloadBytes =
                file.parentFile
                    .listFiles()!!
                    .filter { it.name != "diagnostic.json" }
                    .sumOf { it.length() }
            val candidates = capture.keyEvents.filter { !it.isBoundaryTrace() }
            if (restored.keyEvents.size < candidates.size) {
                val next = candidates[candidates.size - restored.keyEvents.size - 1]
                val nextBytes =
                    li.gkd.app.util.JsonUtils.default
                        .encodeToJsonElement(next)
                        .toString()
                        .toByteArray()
                        .size
                assertTrue(
                    19_500_000L - payloadBytes < nextBytes + 1024,
                    "Reusable fragments must return to the shared pool",
                )
            }
            assertEquals(
                32L - restored.queryRecordings.count { it.detailsRetained },
                restored.droppedQueryRecordings,
            )
            // HTTP uses this exact encoder; limiting a saved capture again must not double-count
            // loss.
            assertEquals(canonical(capture), restored)
            assertEquals(
                restored,
                decodeArchive(encodeDiagnosticCapture(restored)),
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun savesSummaryAndFileAndDoesNotResurrectDeletedCapture() {
        val directory = Files.createTempDirectory(TestFiles.root.toPath(), "diagnostic-").toFile()
        try {
            val store = MatchDiagnosticFileStore(directory)
            var nanos = 0L
            var wall = 1000L
            val diagnostics = MatchDiagnostics(nanoTime = { nanos }, wallTime = { wall })
            val session = diagnostics.start(MatchTimingPolicy())
            assertFails { store.save(diagnostics.export(session.id)) }
            nanos = 2_500_000_000L
            wall = 500L // A wall-clock adjustment must not change elapsed duration.
            diagnostics.stop(session.id)
            val capture = diagnostics.export(session.id)
            val record = assertNotNull(store.save(capture))
            assertEquals(1000L, record.id)
            assertEquals(1000L, record.startedAt)
            assertEquals(500L, record.stoppedAt)
            assertEquals(2500L, record.durationMillis)
            val file = store.directory(record.id).resolve("diagnostic.json")
            assertEquals(
                file.parentFile.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                record.sizeBytes,
            )
            assertEquals(
                canonical(capture),
                decodeArchive(file.readCapture()),
            )
            val attachment = file.parentFile.resolve("attachments/evidence.txt")
            attachment.parentFile.mkdirs()
            attachment.writeText("evidence")
            assertEquals(
                record.sizeBytes + attachment.length(),
                assertNotNull(store.save(capture)).sizeBytes,
            )
            store.delete(record.id)
            assertFalse(file.parentFile.exists())
            assertNull(store.save(capture))
            store.delete(record.id) // Retrying after database deletion failure is safe.
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun observedAppsSurviveEventEvictionAndRemainFrozenAfterStop() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy(), capacity = 10)
        val reducer =
            li.gkd.app.matching.input.MatchInputReducer(
                li.gkd.app.matching.input.ForegroundIdentity("first.app"),
            )
        repeat(25) { index ->
            val input =
                li.gkd.app.matching.input.MatchInput.Foreground(
                    li.gkd.app.matching.input.ForegroundSource.entries.first(),
                    li.gkd.app.matching.input.ForegroundIdentity("app.$index"),
                )
            val before = reducer.checkpoint(index.toLong(), index.toLong())
            // Feed confirmed foreground decisions, independent of platform observation policy.
            val decision =
                li.gkd.app.matching.input.MatchInputDecision(
                    true,
                    "confirmed",
                    before.foreground.let {
                        li.gkd.app.matching.input.ForegroundDecision(
                            confirmed = input.identity,
                            pending = null,
                            changed = true,
                            reason =
                                li.gkd.app.matching.input.ForegroundDecisionReason.entries.first(),
                        )
                    },
                )
            diagnostics.recordInput(
                before,
                li.gkd.app.matching.input.MatchInputRecord(
                    index.toLong(),
                    index.toLong(),
                    null,
                    MatchTimingPolicy(),
                    input,
                ),
                decision,
            )
        }
        diagnostics.stop(session.id)
        val stopped = diagnostics.export(session.id)
        assertTrue(stopped.session.dropped > 0)
        assertEquals(listOf("first.app") + (0 until 25).map { "app.$it" }, stopped.session.appIds)
        diagnostics.start(MatchTimingPolicy())
        assertEquals(stopped.session.appIds, diagnostics.export(session.id).session.appIds)
    }

    @Test
    fun lateApiCompletionUpdatesSavedCaptureWithoutChangingStopContextOrWritingWhileActive() {
        val directory = Files.createTempDirectory(TestFiles.root.toPath(), "late-api-").toFile()
        try {
            val store = MatchDiagnosticFileStore(directory)
            var nanos = 0L
            var writes = 0
            val diagnostics =
                MatchDiagnostics(
                    nanoTime = { nanos },
                    wallTime = { nanos / 1_000_000 },
                    onStoppedCapture = {
                        writes++
                        store.save(it)
                    },
                )
            val session = diagnostics.start(MatchTimingPolicy())
            val runtime =
                MatchDebugSnapshot(
                    MatchEngineSnapshot(true, 4, 7, false, null, 0, MatchTimingPolicy()),
                    null,
                )
            diagnostics.captureRuntime(runtime)
            val request =
                MatchDebugRunState(
                    MatchDebugRunRequest("request", "{}", "rule"),
                    MatchDebugRunPhase.Running,
                    engineRunId = 7,
                )
            diagnostics.recordRequest(session.id, request)
            val operation = diagnostics.beginOperation("NodeRead:Root", 7)!!
            assertEquals(0, writes)
            nanos = 10_000_000
            diagnostics.stop(session.id)
            // Request jobs finish before replacement; only the physical API may outlive it.
            diagnostics.recordRequest(
                session.id,
                request.copy(phase = MatchDebugRunPhase.Cancelled),
            )
            val stopped = diagnostics.export(session.id)
            store.save(stopped)
            val next = diagnostics.start(MatchTimingPolicy())
            assertEquals(stopped.runs, diagnostics.requests(session.id))
            assertTrue(diagnostics.requests(next.id).isEmpty())
            nanos = 30_000_000
            diagnostics.captureRuntime(runtime.copy(engine = runtime.engine.copy(attached = false)))
            diagnostics.endOperation(operation, "returned")
            val file = directory.listFiles()!!.single().resolve("diagnostic.json")
            val restored = decodeArchive(file.readCapture())
            assertEquals(canonical(diagnostics.export(session.id)), restored)
            assertEquals(runtime, restored.runtime)
            assertEquals(stopped.runtimeCapturedAt, restored.runtimeCapturedAt)
            assertEquals(10L, restored.session.stoppedAt)
            assertEquals(listOf(operation), restored.session.operationsAtStop)
            assertTrue(restored.session.openOperations.isEmpty())
            assertEquals(30_000_000L, restored.keyEvents.last().operation!!.durationNanos)
            assertEquals(MatchDebugRunPhase.Cancelled, restored.runs.single().phase)
            assertEquals(restored.runs, diagnostics.requests(session.id))
            assertFailsWith<IllegalArgumentException> { diagnostics.requests("unknown-session") }
            assertTrue(file.readCapture().contains("appVersion"))
            assertTrue(file.readCapture().contains("platform"))
            assertTrue(
                file.readCapture().contains("\"droppedQueryRecordings\":0"),
                "Diagnostic files must include defaults just like HTTP",
            )
            assertFalse(file.readCapture().contains("\"foreground\":null"))
            diagnostics.endOperation(operation, "duplicate")
            diagnostics.recordRequest(session.id, request)
            assertEquals(2, writes)
            assertTrue(diagnostics.export(next.id).runs.isEmpty())
            assertTrue(diagnostics.export(next.id).events.isEmpty())
            assertTrue(diagnostics.export(next.id).session.active)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun stoppedAndClosedCapturesSaveUnfinishedEvidenceThenUpdateAfterCleanup() = runTest {
        for (close in listOf(false, true)) {
            val directory =
                Files.createTempDirectory(TestFiles.root.toPath(), "diagnostic-").toFile()
            val store = MatchDiagnosticFileStore(directory)
            val captures = mutableListOf<MatchDiagnosticCapture>()
            val release = CompletableDeferred<Unit>()
            val coordinator =
                MatchCoordinator(
                    this,
                    StandardTestDispatcher(testScheduler),
                    nowMillis = { testScheduler.currentTime },
                    onDiagnosticCapture = {
                        captures.add(it)
                        store.save(it)
                    },
                    onFailure = { throw it },
                )
            try {
                val key = Any()
                coordinator.attach(key)
                val session = coordinator.startDiagnostics(false, null, recordSystem = true)
                coordinator.diagnosticScope(session.id).launch {
                    coordinator.execute(key) {
                        try {
                            awaitCancellation()
                        } finally {
                            withContext(NonCancellable) { release.await() }
                        }
                    }
                }
                runCurrent()
                if (close) coordinator.close() else coordinator.stopDiagnostics(session.id)
                runCurrent()
                assertEquals(1, captures.size)
                val unfinished =
                    decodeArchive(
                        directory.listFiles()!!.single().resolve("diagnostic.json").readCapture(),
                    )
                assertFalse(unfinished.session.active)
                assertFalse(unfinished.systemRecording!!.sealed)
                assertTrue(unfinished.session.systemFinalizing)
                assertNotNull(unfinished.systemRecording.final.active)
                release.complete(Unit)
                runCurrent()
                assertEquals(2, captures.size)
                val restored =
                    decodeArchive(
                        directory.listFiles()!!.single().resolve("diagnostic.json").readCapture(),
                    )
                assertEquals(canonical(captures.last()), restored)
                assertTrue(restored.systemRecording!!.sealed)
                coordinator.stopDiagnostics(session.id)
                assertEquals(2, captures.size)
            } finally {
                release.complete(Unit)
                coordinator.close()
                directory.deleteRecursively()
            }
        }
    }

    @Test
    fun automaticWorkIsSavedAtStopWithoutBeingCancelledAndUpdatesAfterCompletion() = runTest {
        val captures = mutableListOf<MatchDiagnosticCapture>()
        val release = CompletableDeferred<Unit>()
        val coordinator =
            MatchCoordinator(
                this,
                StandardTestDispatcher(testScheduler),
                onDiagnosticCapture = { captures.add(it) },
                onFailure = { throw it },
            )
        try {
            val key = Any()
            coordinator.attach(key)
            val session = coordinator.startDiagnostics(false, null, recordSystem = true)
            coordinator.request(key) { release.await() }
            runCurrent()
            coordinator.stopDiagnostics(session.id)
            assertEquals(1, captures.size)
            assertFalse(captures.single().systemRecording!!.sealed)
            assertFalse(coordinator.snapshot().cancelling)
            release.complete(Unit)
            runCurrent()
            assertEquals(2, captures.size)
            assertTrue(captures.last().systemRecording!!.sealed)
            coordinator.startDiagnostics(false, null)
            assertEquals(session.id, captures.last().session.id)
        } finally {
            release.complete(Unit)
            coordinator.close()
        }
    }

    @Test
    fun allSevenCapturesSurviveAndFailureCanBeRetriedWithoutPartialJson() {
        val directory = Files.createTempDirectory(TestFiles.root.toPath(), "diagnostic-").toFile()
        try {
            var wall = 0L
            val diagnostics = MatchDiagnostics(wallTime = { wall++ })
            val store = MatchDiagnosticFileStore(directory)
            val ids =
                (0..6).map {
                    val session = diagnostics.start(MatchTimingPolicy())
                    diagnostics.stop(session.id)
                    store.save(diagnostics.export(session.id))
                    session.id
                }
            assertEquals(7, directory.listFiles()!!.size)
            assertTrue(
                directory
                    .resolve(diagnostics.export(ids.last()).session.startedAt.toString())
                    .isDirectory,
            )
            directory.listFiles()!!.forEach {
                decodeArchive(
                    it.resolve("diagnostic.json").readCapture(),
                )
            }
            val blocked = directory.resolve("blocked").apply { writeText("file") }
            val retryStore = MatchDiagnosticFileStore(blocked)
            val capture = diagnostics.export(ids.last())
            assertFails { retryStore.save(capture) }
            assertTrue(blocked.delete())
            retryStore.save(capture)
            assertEquals(
                capture,
                decodeArchive(
                    blocked.listFiles()!!.single().resolve("diagnostic.json").readCapture(),
                ),
            )
        } finally {
            directory.deleteRecursively()
        }
    }
}
