package li.gkd.app.matching.diagnostics

import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import li.gkd.app.matching.MatchCoordinator
import li.gkd.app.matching.MatchEngineSnapshot
import li.gkd.app.matching.input.MatchTimingPolicy
import li.gkd.app.testing.TestFiles

class MatchDiagnosticFileStoreTest {
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
            assertEquals(file.length(), record.sizeBytes)
            assertEquals(capture, Json.decodeFromString<MatchDiagnosticCapture>(file.readText()))
            val attachment = file.parentFile.resolve("attachments/evidence.txt")
            attachment.parentFile.mkdirs()
            attachment.writeText("evidence")
            assertEquals(
                file.length() + attachment.length(),
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
            val restored = Json.decodeFromString<MatchDiagnosticCapture>(file.readText())
            assertEquals(diagnostics.export(session.id), restored)
            assertEquals(runtime, restored.runtime)
            assertEquals(stopped.runtimeCapturedAt, restored.runtimeCapturedAt)
            assertEquals(10L, restored.session.stoppedAt)
            assertEquals(listOf(operation), restored.session.operationsAtStop)
            assertTrue(restored.session.openOperations.isEmpty())
            assertEquals(30_000_000L, restored.events.last().operation!!.durationNanos)
            assertEquals(MatchDebugRunPhase.Cancelled, restored.runs.single().phase)
            assertEquals(restored.runs, diagnostics.requests(session.id))
            assertFailsWith<IllegalArgumentException> { diagnostics.requests("unknown-session") }
            assertTrue(file.readText().contains("appVersion"))
            assertTrue(file.readText().contains("platform"))
            assertTrue(
                file.readText().contains("\"droppedQueryRecordings\":0"),
                "Diagnostic files must include defaults just like HTTP",
            )
            assertTrue(file.readText().contains("\"foreground\":null"))
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
                    Json.decodeFromString<MatchDiagnosticCapture>(
                        directory.listFiles()!!.single().resolve("diagnostic.json").readText(),
                    )
                assertFalse(unfinished.session.active)
                assertFalse(unfinished.systemRecording!!.sealed)
                assertTrue(unfinished.session.systemFinalizing)
                assertNotNull(unfinished.systemRecording.final.active)
                release.complete(Unit)
                runCurrent()
                assertEquals(2, captures.size)
                val restored =
                    Json.decodeFromString<MatchDiagnosticCapture>(
                        directory.listFiles()!!.single().resolve("diagnostic.json").readText(),
                    )
                assertEquals(captures.last(), restored)
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
                Json.decodeFromString<MatchDiagnosticCapture>(
                    it.resolve("diagnostic.json").readText(),
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
                Json.decodeFromString<MatchDiagnosticCapture>(
                    blocked.listFiles()!!.single().resolve("diagnostic.json").readText(),
                ),
            )
        } finally {
            directory.deleteRecursively()
        }
    }
}
