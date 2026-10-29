package li.gkd.app.matching

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import li.gkd.app.matching.diagnostics.MatchDebugRunPhase
import li.gkd.app.matching.diagnostics.MatchDebugRunRequest
import li.gkd.app.matching.diagnostics.MatchDebugRunState

class MatchDiagnosticLifetimeTest {
    @Test
    fun replacementWaitsForRequestTerminalPublication() = runTest {
        val coordinator =
            MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        val release = CompletableDeferred<Unit>()
        try {
            val old = coordinator.startDiagnostics(false, null)
            val state =
                MatchDebugRunState(
                    MatchDebugRunRequest("request", "{}", "rule"),
                    MatchDebugRunPhase.Running,
                )
            coordinator.diagnostics.recordRequest(old.id, state)
            val request =
                coordinator.diagnosticScope(old.id).launch {
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) { release.await() }
                        coordinator.diagnostics.recordRequest(
                            old.id,
                            state.copy(phase = MatchDebugRunPhase.Cancelled),
                        )
                    }
                }
            runCurrent()
            coordinator.stopDiagnostics(old.id)
            runCurrent()
            assertFalse(request.isCompleted)
            assertFailsWith<IllegalStateException> { coordinator.startDiagnostics(false, null) }
            assertEquals(
                MatchDebugRunPhase.Running,
                coordinator.diagnostics.requests(old.id).single().phase,
            )
            release.complete(Unit)
            runCurrent()
            assertTrue(request.isCompleted)
            val next = coordinator.startDiagnostics(false, null)
            assertEquals(
                MatchDebugRunPhase.Cancelled,
                coordinator.exportDiagnostics(old.id).runs.single().phase,
            )
            assertTrue(coordinator.diagnostics.requests(next.id).isEmpty())
        } finally {
            release.complete(Unit)
            coordinator.close()
            runCurrent()
        }
    }

    @Test
    fun captureStaysActiveUntilExplicitStopAndWaitsForActionCleanup() = runTest {
        val coordinator =
            MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        val service = Any()
        val release = CompletableDeferred<Unit>()
        var cleaning = false
        var nextStarted = false
        try {
            coordinator.attach(service)
            val session = coordinator.startDiagnostics(false, null)
            val request =
                coordinator.diagnosticScope(session.id).launch {
                    coordinator.execute(service) {
                        try {
                            awaitCancellation()
                        } finally {
                            withContext(NonCancellable) {
                                cleaning = true
                                release.await()
                            }
                        }
                    }
                }
            runCurrent()
            advanceTimeBy(3_600_000)
            runCurrent()
            assertTrue(coordinator.diagnostics.session(session.id).active)
            assertTrue(request.isActive)
            coordinator.stopDiagnostics(session.id)
            runCurrent()
            assertTrue(request.isCancelled)
            assertFalse(request.isCompleted)
            assertTrue(cleaning)
            assertTrue(coordinator.snapshot().cancelling)
            coordinator.request(service) { nextStarted = true }
            runCurrent()
            assertFalse(nextStarted)
            release.complete(Unit)
            runCurrent()
            assertTrue(request.isCompleted)
            assertTrue(nextStarted)
        } finally {
            release.complete(Unit)
            coordinator.close()
        }
    }

    @Test
    fun stoppedScopeCannotStartLateRequestsOrCancelAReplacementSession() = runTest {
        val coordinator =
            MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        try {
            val old = coordinator.startDiagnostics(false, null)
            val oldScope = coordinator.diagnosticScope(old.id)
            var entered = false
            val lazy = oldScope.launch(start = CoroutineStart.LAZY) { entered = true }
            coordinator.stopDiagnostics(old.id)
            assertFailsWith<IllegalStateException> { coordinator.diagnosticScope(old.id) }
            val late = oldScope.launch { entered = true }
            lazy.start()
            runCurrent()
            assertFalse(entered)
            assertTrue(lazy.isCancelled && late.isCancelled)

            val replacement = coordinator.startDiagnostics(false, null)
            val replacementRequest =
                coordinator.diagnosticScope(replacement.id).launch { awaitCancellation() }
            runCurrent()
            coordinator.stopDiagnostics(old.id)
            advanceTimeBy(100)
            runCurrent()
            assertTrue(replacementRequest.isActive)
            coordinator.close()
            runCurrent()
            assertTrue(replacementRequest.isCompleted)
            assertTrue(replacementRequest.isCancelled)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun stoppingObservationDoesNotCancelAutomaticMatching() = runTest {
        val coordinator =
            MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        val service = Any()
        val finish = CompletableDeferred<Unit>()
        var completed = false
        try {
            coordinator.attach(service)
            val session = coordinator.startDiagnostics(false, null)
            coordinator.request(service) {
                finish.await()
                completed = true
            }
            runCurrent()
            coordinator.stopDiagnostics(session.id)
            runCurrent()
            assertFalse(coordinator.snapshot().cancelling)
            assertTrue(coordinator.snapshot().activeRunId != null)
            finish.complete(Unit)
            runCurrent()
            assertTrue(completed)
        } finally {
            finish.complete(Unit)
            coordinator.close()
        }
    }
}
