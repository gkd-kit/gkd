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
import li.gkd.app.model.ActionResult
import li.gkd.app.model.GkdAction

class MatchCoordinatorTest {
    @Test
    fun synchronousCompletionKeepsNewlyScheduledTaskUntilItsDeadline() = runTest {
        val coordinator =
            MatchCoordinator(
                this,
                kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler),
                nowMillis = { testScheduler.currentTime },
                onFailure = { throw it },
            )
        val service = Any()
        val runs = mutableListOf<String>()
        try {
            coordinator.attach(service)
            coordinator.request(service) {
                runs += "first"
                coordinator.scheduleFollowUp(service) { runs += "follow-up" }
                coordinator.request(service) { runs += "pending" }
            }
            assertEquals(listOf("first", "pending"), runs)
            assertEquals(null, coordinator.snapshot().activeRunId)
            advanceTimeBy(coordinator.policy.value.followUpMillis)
            runCurrent()
            assertEquals(listOf("first", "pending", "follow-up"), runs)
            assertEquals(null, coordinator.snapshot().nextWakeMillis)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun captureWithoutLoadedRulesDoesNotInventAnEmptyInitialIndex() = runTest {
        val coordinator =
            MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        try {
            val session = coordinator.startDiagnostics(false, null)
            assertEquals(null, coordinator.exportDiagnostics(session.id).initialRules)
            coordinator.stopDiagnostics(session.id)
            assertEquals(null, coordinator.exportDiagnostics(session.id).initialRules)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun foregroundTransitionCancelsBeforeActionStarts() = runTest {
        for (reason in
            listOf(
                MatchInvalidationReason.PageChanged,
                MatchInvalidationReason.ForegroundConflict,
            )) {
            val coordinator =
                MatchCoordinator(
                    this,
                    StandardTestDispatcher(testScheduler),
                    onFailure = { throw it },
                )
            val service = Any()
            val ready = CompletableDeferred<Unit>()
            var actionStarted = false
            try {
                coordinator.attach(service)
                coordinator.request(service) {
                    ready.await()
                    performAction(GkdAction("*", action = "click")) {
                        actionStarted = true
                        ActionResult("click", true)
                    }
                }
                runCurrent()
                coordinator.invalidate(reason)
                ready.complete(Unit)
                runCurrent()
                assertFalse(actionStarted)
                assertEquals(null, coordinator.snapshot().activeRunId)
            } finally {
                coordinator.close()
            }
        }
    }

    @Test
    fun explicitStopStillCancelsGestureAfterForegroundTransition() = runTest {
        for (stop in
            listOf(
                MatchInvalidationReason.AutomationDisabled,
                MatchInvalidationReason.ScreenOff,
                MatchInvalidationReason.ServiceDisconnected,
            )) {
            val coordinator =
                MatchCoordinator(
                    this,
                    StandardTestDispatcher(testScheduler),
                    onFailure = { throw it },
                )
            val service = Any()
            var released = false
            try {
                coordinator.attach(service)
                coordinator.request(service) {
                    performAction(GkdAction("*", action = "swipe")) {
                        try {
                            awaitCancellation()
                        } finally {
                            released = true
                        }
                    }
                }
                runCurrent()
                coordinator.invalidate(MatchInvalidationReason.PageChanged)
                runCurrent()
                assertFalse(released)
                if (stop == MatchInvalidationReason.ServiceDisconnected) coordinator.detach(service)
                else coordinator.invalidate(stop)
                runCurrent()
                assertTrue(released)
                assertEquals(null, coordinator.snapshot().activeRunId)
            } finally {
                coordinator.close()
            }
        }
    }

    @Test
    fun cancellationWaitsForContinuousSwipeReleaseAndPropagatesCleanupFailure() = runTest {
        val failures = mutableListOf<Throwable>()
        val coordinator =
            MatchCoordinator(
                this,
                StandardTestDispatcher(testScheduler),
                onFailure = { failures += it },
            )
        val service = Any()
        val segmentFinished = CompletableDeferred<Unit>()
        val releaseFinished = CompletableDeferred<Unit>()
        var failure: Throwable? = null
        var nextStarted = false
        var submissions = 0
        try {
            coordinator.attach(service)
            val caller = launch {
                try {
                    coordinator.execute(service) {
                        continuousSwipe<SwipeSegment>(
                            0f,
                            0f,
                            100f,
                            100f,
                            1000,
                            create = { _, segment -> segment },
                            dispatch = {
                                submissions++
                                if (it.continues) {
                                    segmentFinished.await()
                                    GestureSegmentResult.Completed
                                } else {
                                    releaseFinished.await()
                                    GestureSegmentResult.Rejected
                                }
                            },
                            nowMillis = { testScheduler.currentTime },
                        )
                    }
                } catch (error: Throwable) {
                    failure = error
                }
            }
            runCurrent()
            caller.cancel()
            runCurrent()
            coordinator.request(service) { nextStarted = true }
            segmentFinished.complete(Unit)
            runCurrent()
            assertEquals(2, submissions)
            assertFalse(nextStarted)
            assertFalse(caller.isCompleted)
            releaseFinished.complete(Unit)
            runCurrent()
            assertTrue(caller.isCompleted)
            assertTrue(failure is IllegalStateException)
            assertTrue(failure?.message?.contains("release is unconfirmed") == true)
            assertEquals(1, failures.size)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun diagnosticStopRestoresTimingAndDoesNotLoseThePolicyUsedByAQuery() = runTest {
        val coordinator =
            MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        val service = Any()
        try {
            coordinator.attach(service)
            val original = coordinator.policy.value
            val session = coordinator.startDiagnostics(false, null)
            val temporary = original.copy(rootWaitMillis = 123)
            coordinator.updatePolicy(temporary)
            coordinator.request(service) {}
            runCurrent()
            coordinator.stopDiagnostics(session.id)
            runCurrent()
            assertEquals(original, coordinator.policy.value)
            assertFalse(coordinator.diagnostics.state.value!!.active)
            val events = coordinator.diagnostics.export(session.id).events
            assertEquals(temporary, events.single { it.reason == "query_started" }.policy)
            assertEquals(original, events.last { it.reason == "policy_updated" }.policy)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun closingOldTransportDoesNotStopReplacementUiSession() = runTest {
        val coordinator =
            MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
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
    fun pageCancellationWaitsForQueryCleanupBeforeStartingNextQuery() = runTest {
        val coordinator =
            MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        val service = Any()
        val released = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        try {
            coordinator.attach(service)
            coordinator.request(service) {
                events += "old_started"
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { released.await() }
                    events += "old_released"
                }
            }
            runCurrent()
            coordinator.invalidate(MatchInvalidationReason.PageChanged)
            coordinator.request(service) { events += "new_started" }
            runCurrent()
            assertEquals(listOf("old_started"), events)
            released.complete(Unit)
            runCurrent()
            assertEquals(listOf("old_started", "old_released", "new_started"), events)
        } finally {
            released.complete(Unit)
            coordinator.close()
        }
    }

    @Test
    fun contentBurstCoalescesWithoutCancellingAnActiveAction() = runTest {
        val coordinator =
            MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        val service = Any()
        val finish = CompletableDeferred<Unit>()
        val runs = mutableListOf<Int>()
        try {
            coordinator.attach(service)
            coordinator.request(service) {
                finish.await()
                runs += 0
            }
            runCurrent()
            repeat(1000) { value -> coordinator.request(service) { runs += value + 1 } }
            runCurrent()
            assertTrue(runs.isEmpty())
            finish.complete(Unit)
            runCurrent()
            assertEquals(listOf(0, 1000), runs)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun disconnectedCallbackWaitIsVisibleAndDoesNotReleaseTheLaneForANewService() = runTest {
        val coordinator =
            MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
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
            advanceTimeBy(10_000)
            runCurrent()
            assertFalse(nextStarted)
            assertTrue(coordinator.snapshot().cancelling)
            callback.complete(Unit)
            runCurrent()
            assertTrue(nextStarted)
            assertFalse(coordinator.snapshot().cancelling)
        } finally {
            callback.complete(Unit)
            coordinator.close()
        }
    }

    @Test
    fun serviceHandoffDropsOldPendingWorkAndRejectsDetachedService() = runTest {
        val coordinator =
            MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        val old = Any()
        val fresh = Any()
        val runs = mutableListOf<String>()
        try {
            coordinator.attach(old)
            coordinator.request(old) { awaitCancellation() }
            runCurrent()
            coordinator.request(old) { runs += "old_pending" }
            coordinator.attach(fresh)
            coordinator.request(old) { runs += "old_late" }
            coordinator.request(fresh) { runs += "fresh" }
            runCurrent()
            assertEquals(listOf("fresh"), runs)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun disabledTracingDoesNotReadClocksAndBoundedHistoryReportsGaps() {
        var reads = 0
        val diagnostics =
            MatchDiagnostics(
                nanoTime = {
                    reads++
                    reads.toLong()
                },
                wallTime = { 0L },
            )
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
