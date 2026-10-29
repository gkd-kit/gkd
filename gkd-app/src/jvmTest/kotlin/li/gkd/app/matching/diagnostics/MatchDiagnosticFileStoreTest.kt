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
    fun directoryUsesStartTimeAndKeepsAttachmentsWithoutCapacityCleanup() {
        val directory =
            Files.createTempDirectory(TestFiles.root.toPath(), "diagnostic-directory-").toFile()
        try {
            val store = MatchDiagnosticFileStore(directory)
            val diagnostics = MatchDiagnostics(wallTime = { 0L })
            val session = diagnostics.start(MatchTimingPolicy())
            assertFails { store.save(diagnostics.export(session.id)) }
            assertTrue(directory.listFiles()!!.isEmpty())
            diagnostics.stop(session.id)
            val capture = diagnostics.export(session.id)
            store.save(capture)
            val saved = directory.resolve("19700101000000Z-${session.id.take(8)}")
            val record = store.records().single()
            assertEquals(saved, record.file)
            assertEquals(0L, record.startedAt)
            assertEquals(session.id.take(8), record.id)
            assertEquals(saved.resolve("diagnostic.json").length(), record.sizeBytes)
            val attachment = saved.resolve("attachments/evidence.txt")
            assertTrue(attachment.parentFile.mkdirs())
            attachment.writeText("evidence")
            store.save(capture)
            assertEquals(listOf(saved), directory.listFiles()!!.toList())
            assertEquals("evidence", attachment.readText())
            assertEquals(
                capture,
                Json.decodeFromString<MatchDiagnosticCapture>(
                    saved.resolve("diagnostic.json").readText(),
                ),
            )
            // Saving another capture must not remove older records or large attachments.
            java.io.RandomAccessFile(attachment, "rw").use { it.setLength(33L * 1024 * 1024) }
            val legacy = directory.resolve("matching-old.json").apply { writeText("{}") }
            store.save(capture)
            assertTrue(legacy.exists())
            assertTrue(attachment.exists())
            val next = diagnostics.start(MatchTimingPolicy())
            diagnostics.stop(next.id)
            store.save(diagnostics.export(next.id))
            assertTrue(saved.exists())
            assertEquals(
                2,
                store.records().size,
                "Root-level legacy JSON is not a capture directory",
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun deletesDirectoriesWithoutLateWriteResurrection() {
        val directory =
            Files.createTempDirectory(TestFiles.root.toPath(), "diagnostic-actions-").toFile()
        try {
            val store = MatchDiagnosticFileStore(directory)
            val diagnostics = MatchDiagnostics()
            val captures =
                (0..2).map {
                    val session = diagnostics.start(MatchTimingPolicy())
                    diagnostics.stop(session.id)
                    diagnostics.export(session.id).also(store::save)
                }
            val records = store.records()
            records.first().file.resolve("attachment.txt").writeText("evidence")
            store.delete(records.take(2))
            assertEquals(listOf(records.last()), store.records())
            captures.forEach(store::save)
            assertEquals(
                listOf(records.last()),
                store.records(),
                "Late stopped-capture updates must not recreate deleted records",
            )
            store.delete(listOf(records.last()))
            assertTrue(store.records().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun sizeIncludesDiagnosticAndNestedAttachmentsWithoutReadingText() {
        val directory =
            Files.createTempDirectory(TestFiles.root.toPath(), "diagnostic-size-").toFile()
        try {
            val captureDirectory = directory.resolve("19700101000000Z-12345678").apply { mkdirs() }
            val file =
                captureDirectory.resolve("diagnostic.json").apply { writeText("节点".repeat(40_000)) }
            val attachment = captureDirectory.resolve("attachments/data.bin")
            attachment.parentFile.mkdirs()
            attachment.writeBytes(ByteArray(1234))
            val record = MatchDiagnosticFileStore(directory).records().single()
            assertEquals(file.length() + attachment.length(), record.sizeBytes)
            attachment.appendBytes(ByteArray(10))
            assertEquals(
                file.length() + attachment.length(),
                MatchDiagnosticFileStore(directory).records().single().sizeBytes,
            )
        } finally {
            directory.deleteRecursively()
        }
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
            val diagnostics = MatchDiagnostics()
            val store = MatchDiagnosticFileStore(directory)
            val ids =
                (0..6).map {
                    val session = diagnostics.start(MatchTimingPolicy())
                    diagnostics.stop(session.id)
                    store.save(diagnostics.export(session.id))
                    session.id
                }
            assertEquals(7, directory.listFiles()!!.size)
            assertTrue(directory.listFiles()!!.any { it.name.endsWith(ids.last().take(8)) })
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
