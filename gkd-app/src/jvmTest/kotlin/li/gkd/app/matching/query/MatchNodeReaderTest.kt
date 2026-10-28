package li.gkd.app.matching.query

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertSame
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import li.gkd.app.matching.MatchCoordinator
import li.gkd.app.matching.diagnostics.MatchDiagnostics
import li.gkd.app.matching.input.MatchTimingPolicy
import kotlinx.coroutines.delay

class MatchNodeReaderTest {
    /** Old-query timeouts and capacity rejection must not become the next session's evidence. */
    @Test
    fun oldQueryPressureDoesNotEnterNewSession() {
        val diagnostics = MatchDiagnostics()
        val old = diagnostics.start(MatchTimingPolicy())
        val operation = diagnostics.beginOperation("SelectorQuery", 7)!!
        diagnostics.stop(old.id)
        val next = diagnostics.start(MatchTimingPolicy())
        val reader = MatchNodeReader(diagnostics)
        val release = CountDownLatch(1)
        val entered = CountDownLatch(4)
        try {
            li.gkd.app.matching.diagnostics.MatchNodeObservation.observe(operation,
                { li.gkd.app.matching.diagnostics.MatchRecordedValue("null") }, {
                    repeat(4) {
                        assertEquals("node_read_timeout", assertFailsWith<MatchNodeReadUnavailable> {
                            reader.read(50) { entered.countDown(); release.await() }
                        }.message)
                    }
                    assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    assertEquals("node_read_capacity", assertFailsWith<MatchNodeReadUnavailable> {
                        reader.read { error("Occupied reader must reject the fifth read") }
                    }.message)
                })
            assertTrue(diagnostics.export(next.id).events.isEmpty())
            assertTrue(diagnostics.export(next.id).entries.isEmpty())
        } finally { release.countDown(); reader.close() }
    }

    @Test
    fun physicalReadAndWaitShareRunRuleAndParentWithSeparateDurations() = runBlocking {
        val nanos = java.util.concurrent.atomic.AtomicLong(0)
        val diagnostics = MatchDiagnostics(nanoTime = nanos::get, wallTime = { 0 })
        val session = diagnostics.start(MatchTimingPolicy())
        val reader = MatchNodeReader(diagnostics)
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        try {
            val result = async { reader.query { reader.read(5000, "FindText", 42, "rule") {
                entered.complete(Unit)
                release.await()
                "found"
            } } }
            withTimeout(5000) { entered.await() }
            val open = diagnostics.export(session.id).session.openOperations
            val waiting = open.single { it.name == "FindTextWait" }
            val physical = open.single { it.name == "NodeRead:FindText" }
            assertEquals(waiting.id, physical.parentOperationId)
            assertTrue(open.all { it.runId == 42L && it.rule == "rule" })
            nanos.set(12_000_000)
            release.countDown()
            assertEquals("found", withTimeout(5000) { result.await() })
            val completed = diagnostics.export(session.id).events.mapNotNull { it.operation }.filter { it.durationNanos != null }
            assertEquals(2, completed.size)
            assertTrue(completed.all { it.durationNanos == 12_000_000L })
        } finally { release.countDown(); reader.close() }
    }
    /** Cancelled waits leave physical reads occupied and visible until their actual return. */
    @Test
    fun pressureCapturesOccupiedSlotsAndOldestPhysicalReadAfterCancellation() = runBlocking {
        val nanos = java.util.concurrent.atomic.AtomicLong(0)
        val diagnostics = MatchDiagnostics(nanoTime = nanos::get, wallTime = { 0 })
        val session = diagnostics.start(MatchTimingPolicy())
        val reader = MatchNodeReader(diagnostics)
        val release = CountDownLatch(1)
        val entered = List(4) { CompletableDeferred<Unit>() }
        try {
            val jobs = entered.map { signal -> launch {
                reader.query { reader.read(5000) { signal.complete(Unit); release.await() } }
            } }
            withTimeout(5000) { entered.forEach { it.await() }; jobs.forEach { it.cancelAndJoin() } }
            nanos.set(700_000_000)
            assertEquals("node_read_capacity", assertFailsWith<MatchNodeReadUnavailable> {
                reader.query { reader.read { error("Fifth read must not execute") } }
            }.message)
            val capture = diagnostics.export(session.id)
            val pressure = capture.events.single { it.nodePressure != null }.nodePressure!!
            assertEquals(4, pressure.activeReads)
            assertEquals(4, pressure.observedReads)
            assertEquals(700_000_000L, pressure.oldestObservedReadNanos)
            assertEquals(4, capture.session.openOperations.size)
            release.countDown()
            withTimeout(5000) { while (diagnostics.state.value!!.openOperations.isNotEmpty()) delay(1) }
            assertEquals(4, diagnostics.export(session.id).events.count { it.operation?.outcome == "returned" })
        } finally { release.countDown(); reader.close() }
    }

    /** Diagnostic-disabled reads must not create operations or consult diagnostic clocks. */
    @Test
    fun disabledReaderDiagnosticsPreserveNormalAndTimeoutPathsWithoutClockReads() = runBlocking {
        val diagnostics = MatchDiagnostics(nanoTime = { error("Diagnostic clock read") }, wallTime = { error("Wall clock read") })
        val reader = MatchNodeReader(diagnostics)
        val release = CountDownLatch(1)
        try {
            assertEquals(7, reader.query { reader.read { 7 } })
            assertEquals("node_read_timeout", assertFailsWith<MatchNodeReadUnavailable> {
                reader.query { reader.read(20) { release.await() } }
            }.message)
            assertEquals(null, diagnostics.state.value)
        } finally { release.countDown(); reader.close() }
    }

    @Test
    fun timeoutEmitsPressureAndLeavesReadOpenUntilPhysicalCompletion() = runBlocking {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy())
        val reader = MatchNodeReader(diagnostics)
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        try {
            val job = async { reader.query { reader.read(100) { entered.complete(Unit); release.await() } } }
            withTimeout(5000) { entered.await() }
            assertFailsWith<MatchNodeReadUnavailable> { job.await() }
            val pressure = diagnostics.export(session.id).events.single { it.reason == "node_read_timeout" }.nodePressure!!
            assertEquals(1, pressure.activeReads)
            assertEquals(1, pressure.observedReads)
            assertTrue(pressure.oldestObservedReadNanos!! > 0)
        } finally { release.countDown(); reader.close() }
    }


}
