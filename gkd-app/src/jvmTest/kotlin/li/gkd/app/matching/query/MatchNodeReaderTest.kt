package li.gkd.app.matching.query

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import li.gkd.app.matching.MatchCoordinator
import li.gkd.app.matching.MatchInvalidationReason
import li.gkd.app.matching.diagnostics.MatchDiagnostics
import li.gkd.app.matching.diagnostics.MatchNodeObservation
import li.gkd.app.matching.diagnostics.MatchRecordedValue
import li.gkd.app.matching.diagnostics.MatchRecordedValueType
import li.gkd.app.matching.input.MatchTimingPolicy
import li.gkd.app.model.ActionResult
import li.gkd.app.model.GkdAction

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
            MatchNodeObservation.observe(
                operation,
                { MatchRecordedValue(MatchRecordedValueType.Null) },
                {
                    repeat(4) {
                        assertEquals(
                            MatchNodeReadFailure.Timeout,
                            assertFailsWith<MatchNodeReadUnavailable> {
                                    reader.read(50) {
                                        entered.countDown()
                                        release.await()
                                    }
                                }
                                .reason,
                        )
                    }
                    assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    assertEquals(
                        MatchNodeReadFailure.CapacityExceeded,
                        assertFailsWith<MatchNodeReadUnavailable> {
                                reader.read { error("Occupied reader must reject the fifth read") }
                            }
                            .reason,
                    )
                },
            )
            assertTrue(diagnostics.export(next.id).events.isEmpty())
            assertTrue(diagnostics.export(next.id).entries.isEmpty())
        } finally {
            release.countDown()
            reader.close()
        }
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
            val result = async {
                reader.query {
                    reader.read(5000, "FindText", 42, "rule") {
                        entered.complete(Unit)
                        release.await()
                        "found"
                    }
                }
            }
            withTimeout(5000) { entered.await() }
            val open = diagnostics.export(session.id).session.openOperations
            val waiting = open.single { it.name == "FindTextWait" }
            val physical = open.single { it.name == "NodeRead:FindText" }
            assertEquals(waiting.id, physical.parentOperationId)
            assertTrue(open.all { it.runId == 42L && it.rule == "rule" })
            nanos.set(12_000_000)
            release.countDown()
            assertEquals("found", withTimeout(5000) { result.await() })
            val completed =
                diagnostics
                    .export(session.id)
                    .events
                    .mapNotNull { it.operation }
                    .filter { it.durationNanos != null }
            assertEquals(2, completed.size)
            assertTrue(completed.all { it.durationNanos == 12_000_000L })
        } finally {
            release.countDown()
            reader.close()
        }
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
            val jobs = entered.map { signal ->
                launch {
                    reader.query {
                        reader.read(5000) {
                            signal.complete(Unit)
                            release.await()
                        }
                    }
                }
            }
            withTimeout(5000) {
                entered.forEach { it.await() }
                jobs.forEach { it.cancelAndJoin() }
            }
            nanos.set(700_000_000)
            assertEquals(
                MatchNodeReadFailure.CapacityExceeded,
                assertFailsWith<MatchNodeReadUnavailable> {
                        reader.query { reader.read { error("Fifth read must not execute") } }
                    }
                    .reason,
            )
            val capture = diagnostics.export(session.id)
            val pressure = capture.events.single { it.nodePressure != null }.nodePressure!!
            assertEquals(4, pressure.activeReads)
            assertEquals(4, pressure.observedReads)
            assertEquals(700_000_000L, pressure.oldestObservedReadNanos)
            assertEquals(4, capture.session.openOperations.size)
            release.countDown()
            withTimeout(5000) {
                while (diagnostics.state.value!!.openOperations.isNotEmpty()) delay(1)
            }
            assertEquals(
                4,
                diagnostics.export(session.id).events.count { it.operation?.outcome == "returned" },
            )
        } finally {
            release.countDown()
            reader.close()
        }
    }

    /** Diagnostic-disabled reads must not create operations or consult diagnostic clocks. */
    @Test
    fun disabledReaderDiagnosticsPreserveNormalAndTimeoutPathsWithoutClockReads() = runBlocking {
        val diagnostics =
            MatchDiagnostics(
                nanoTime = { error("Diagnostic clock read") },
                wallTime = { error("Wall clock read") },
            )
        val reader = MatchNodeReader(diagnostics)
        val release = CountDownLatch(1)
        try {
            assertEquals(7, reader.query { reader.read { 7 } })
            assertEquals(
                MatchNodeReadFailure.Timeout,
                assertFailsWith<MatchNodeReadUnavailable> {
                        reader.query { reader.read(20) { release.await() } }
                    }
                    .reason,
            )
            assertEquals(null, diagnostics.state.value)
        } finally {
            release.countDown()
            reader.close()
        }
    }

    @Test
    fun timeoutEmitsPressureAndLeavesReadOpenUntilPhysicalCompletion() = runBlocking {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy())
        val reader = MatchNodeReader(diagnostics)
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        try {
            val job = async {
                reader.query {
                    reader.read(100) {
                        entered.complete(Unit)
                        release.await()
                    }
                }
            }
            withTimeout(5000) { entered.await() }
            assertFailsWith<MatchNodeReadUnavailable> { job.await() }
            val pressure =
                diagnostics
                    .export(session.id)
                    .events
                    .single { it.reason == "node_read_timeout" }
                    .nodePressure!!
            assertEquals(1, pressure.activeReads)
            assertEquals(1, pressure.observedReads)
            assertTrue(pressure.oldestObservedReadNanos!! > 0)
        } finally {
            release.countDown()
            reader.close()
        }
    }

    @Test
    fun timeoutReleasesWaiterWithoutCommittingLateResultsOrStoppingPhysicalRead() = runBlocking {
        val reader = MatchNodeReader()
        val release = CountDownLatch(1)
        val entered = CompletableDeferred<Unit>()
        val returned = CompletableDeferred<Unit>()
        val committed = AtomicBoolean(false)
        try {
            val waiting = async {
                reader.query {
                    reader.read(100) {
                        entered.complete(Unit)
                        release.await()
                        returned.complete(Unit)
                        "old page"
                    }
                    committed.set(true)
                }
            }
            withTimeout(5000) { entered.await() }
            assertEquals(
                MatchNodeReadFailure.Timeout,
                assertFailsWith<MatchNodeReadUnavailable> { waiting.await() }.reason,
            )
            assertFalse(returned.isCompleted)
            assertEquals("new page", reader.query { reader.read { "new page" } })
            release.countDown()
            withTimeout(5000) { returned.await() }
            assertFalse(committed.get())
        } finally {
            release.countDown()
            reader.close()
        }
    }

    @Test
    fun blockedRootAndSourceLeaveCapacityForSerialMatching() = runBlocking {
        val reader = MatchNodeReader()
        val release = CountDownLatch(1)
        val entered = List(2) { CompletableDeferred<Unit>() }
        val coordinator = MatchCoordinator(this, Dispatchers.Default, onFailure = { throw it })
        val service = Any()
        val actionStarted = CompletableDeferred<Unit>()
        val actionRelease = CompletableDeferred<Unit>()
        val nextMatched = CompletableDeferred<Unit>()
        val waiting = entered.map { started ->
            async {
                reader.query {
                    reader.read(5000) {
                        started.complete(Unit)
                        release.await()
                    }
                }
            }
        }
        try {
            withTimeout(5000) { entered.forEach { it.await() } }
            coordinator.attach(service)
            coordinator.request(service) {
                assertEquals("source target", reader.query { reader.read { "source target" } })
                performAction(GkdAction("*", action = "click")) {
                    actionStarted.complete(Unit)
                    actionRelease.await()
                    ActionResult("click", true)
                }
            }
            withTimeout(5000) { actionStarted.await() }
            coordinator.request(service) { nextMatched.complete(Unit) }
            assertFalse(nextMatched.isCompleted)
            actionRelease.complete(Unit)
            withTimeout(5000) { nextMatched.await() }
            assertTrue(waiting.none { it.isCompleted })
        } finally {
            actionRelease.complete(Unit)
            release.countDown()
            waiting.forEach { it.cancelAndJoin() }
            coordinator.close()
            reader.close()
        }
    }

    @Test
    fun pageChangeFreesSerialMatcherBeforeOldPlatformReadReturns() = runBlocking {
        val reader = MatchNodeReader()
        val release = CountDownLatch(1)
        val entered = CompletableDeferred<Unit>()
        val returned = CompletableDeferred<Unit>()
        val next = CompletableDeferred<Int>()
        val committedOldResult = AtomicBoolean(false)
        val coordinator =
            MatchCoordinator(
                this,
                Dispatchers.Default,
                onFailure = { next.completeExceptionally(it) },
            )
        val service = Any()
        try {
            coordinator.attach(service)
            coordinator.request(service) {
                reader.query {
                    reader.read {
                        entered.complete(Unit)
                        release.await() // Remains blocked after cancelling the matching task.
                        returned.complete(Unit)
                        1
                    }
                    committedOldResult.set(true)
                }
            }
            withTimeout(5000) { entered.await() }
            coordinator.invalidate(MatchInvalidationReason.PageChanged)
            coordinator.request(service) { next.complete(reader.query { reader.read { 2 } }) }
            assertEquals(2, withTimeout(5000) { next.await() })
            assertFalse(returned.isCompleted)
            assertFalse(committedOldResult.get())
            release.countDown()
            withTimeout(5000) { returned.await() }
            assertFalse(committedOldResult.get())
        } finally {
            release.countDown()
            coordinator.close()
            reader.close()
        }
    }

    @Test
    fun cancelledReadsKeepTheirCapacityUntilPhysicalReturn() = runBlocking {
        val reader = MatchNodeReader()
        val release = CountDownLatch(1)
        val entered = List(4) { CompletableDeferred<Unit>() }
        val returned = List(4) { CompletableDeferred<Unit>() }
        try {
            val jobs = entered.mapIndexed { index, signal ->
                launch {
                    reader.query {
                        reader.read {
                            signal.complete(Unit)
                            release.await()
                            returned[index].complete(Unit)
                        }
                    }
                }
            }
            withTimeout(5000) {
                entered.forEach { it.await() }
                jobs.forEach { it.cancelAndJoin() }
            }
            val excess = async {
                reader.query { reader.read { error("Must not submit a fifth physical read") } }
            }
            assertEquals(
                MatchNodeReadFailure.CapacityExceeded,
                assertFailsWith<MatchNodeReadUnavailable> { excess.await() }.reason,
            )
            assertFalse(returned.any { it.isCompleted })
            release.countDown()
            withTimeout(5000) { returned.forEach { it.await() } }
        } finally {
            release.countDown()
            reader.close()
        }
    }

    @Test
    fun returnValuesFailuresAndClosedReaderAreNotReportedAsNoMatch() = runBlocking {
        val reader = MatchNodeReader()
        try {
            assertEquals(null, reader.query { reader.read<Any?> { null } })
            val error = IllegalArgumentException("platform error")
            val propagated =
                assertFailsWith<IllegalArgumentException> {
                    reader.query {
                        // Verify the executor unwraps its exception; coroutine stack recovery may
                        // copy it later.
                        assertSame(
                            error,
                            assertFailsWith<IllegalArgumentException> {
                                reader.read { throw error }
                            },
                        )
                        throw error
                    }
                }
            assertEquals(error.message, propagated.message)
        } finally {
            reader.close()
        }
        assertEquals(
            MatchNodeReadFailure.ReaderClosed,
            assertFailsWith<MatchNodeReadUnavailable> { reader.query { reader.read { 1 } } }.reason,
        )
    }
}
