package li.gkd.app.matching

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import li.gkd.app.matching.action.GestureSegmentResult
import li.gkd.app.matching.action.SwipeSegment
import li.gkd.app.matching.action.continuousSwipe
import li.gkd.app.matching.diagnostics.MatchDiagnostics
import li.gkd.app.matching.diagnostics.MatchStage
import li.gkd.app.matching.input.MatchTimingPolicy

class MatchCoordinatorTest {
    @Test
    fun captureWithoutLoadedRulesDoesNotInventAnEmptyInitialIndex() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        try {
            val session = coordinator.startDiagnostics(false, null)
            assertEquals(null, coordinator.exportDiagnostics(session.id).initialRules)
            coordinator.stopDiagnostics(session.id)
            assertEquals(null, coordinator.exportDiagnostics(session.id).initialRules)
        } finally { coordinator.close() }
    }

    @Test
    fun diagnosticStopRestoresTimingAndDoesNotLoseThePolicyUsedByAQuery() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        val service = Any()
        try {
            coordinator.attach(service)
            val original = coordinator.policy.value
            val session = coordinator.startDiagnostics(false, null)
            val temporary = original.copy(rootWaitMillis = 123)
            coordinator.updatePolicy(temporary)
            coordinator.request(service) { }
            runCurrent()
            coordinator.stopDiagnostics(session.id)
            runCurrent()
            assertEquals(original, coordinator.policy.value)
            assertFalse(coordinator.diagnostics.state.value!!.active)
            val events = coordinator.diagnostics.export(session.id).events
            assertEquals(temporary, events.single { it.reason == "query_started" }.policy)
            assertEquals(original, events.last { it.reason == "policy_updated" }.policy)
        } finally { coordinator.close() }
    }

    @Test
    fun closingOldTransportDoesNotStopReplacementUiSession() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        try {
            val transportSession = coordinator.startDiagnostics(false, null)
            coordinator.stopDiagnostics(transportSession.id)
            val uiSession = coordinator.startDiagnostics(false, null)
            coordinator.stopDiagnostics(transportSession.id)
            assertEquals(uiSession.id, coordinator.diagnostics.state.value?.id)
            assertTrue(coordinator.diagnostics.state.value?.active == true)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun disconnectedCallbackWaitIsVisibleAndDoesNotReleaseTheLaneForANewService() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        val old = Any()
        val fresh = Any()
        val callback = CompletableDeferred<Unit>()
        var nextStarted = false
        try {
            coordinator.attach(old)
            coordinator.request(old) { withContext(NonCancellable) { callback.await() } }
            runCurrent()
            coordinator.detach(old)
            runCurrent()
            assertTrue(coordinator.snapshot().cancelling)
            assertFalse(coordinator.snapshot().attached)
            coordinator.attach(fresh)
            coordinator.request(fresh) { nextStarted = true }
            advanceTimeBy(10_000); runCurrent()
            assertFalse(nextStarted)
            assertTrue(coordinator.snapshot().cancelling)
            callback.complete(Unit); runCurrent()
            assertTrue(nextStarted)
            assertFalse(coordinator.snapshot().cancelling)
        } finally { callback.complete(Unit); coordinator.close() }
    }

    @Test
    fun disabledTracingDoesNotReadClocksAndBoundedHistoryReportsGaps() {
        var reads = 0
        val diagnostics = MatchDiagnostics(nanoTime = { reads++; reads.toLong() }, wallTime = { 0L })
        diagnostics.record(1, MatchStage.Query, "ignored")
        assertEquals(0, reads)
        val session = diagnostics.start(MatchTimingPolicy(), capacity = 10)
        repeat(15) { diagnostics.record(1, MatchStage.Rule, "rule_$it") }
        val page = diagnostics.read(session.id)
        assertTrue(page.gap)
        assertEquals(10, page.events.size)
        assertEquals(6L, page.events.first().sequence)
        diagnostics.stop(session.id)
        val before = reads
        diagnostics.record(1, MatchStage.Query, "ignored")
        assertEquals(before, reads)
        assertFalse(diagnostics.read(session.id).active)
    }
}
