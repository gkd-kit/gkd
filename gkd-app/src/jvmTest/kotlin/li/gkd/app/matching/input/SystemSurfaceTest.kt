package li.gkd.app.matching.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemSurfaceTest {
    private val app = ForegroundIdentity("video", "Main")
    private val systemUi = ForegroundIdentity("com.android.systemui")
    private val policy = MatchTimingPolicy()

    @Test
    fun stalledNodesCannotRestoreUnderlyingAppAndBriefCoverStartsExactlyOneVisit() {
        val tracker = ForegroundTracker(app)
        tracker.observe(ForegroundSource.TaskStack, app, policy, 1)
        assertEquals(systemUi, tracker.observeSurface(true, 10).pending)
        // Even after the normal foreground timeout, stale underlying nodes cannot undo coverage.
        repeat(3) { i ->
            assertEquals(
                systemUi,
                tracker.observe(ForegroundSource.Nodes, app, policy, 10000L + i * 100).pending,
            )
            assertEquals(
                systemUi,
                tracker.observe(ForegroundSource.TaskStack, app, policy, 10010L + i * 100).pending,
            )
        }
        tracker.observeSurface(false, 11000)
        tracker.observeSurface(false, 11001)
        tracker.observe(ForegroundSource.Accessibility, app, policy, 11002)
        val returned = tracker.observe(ForegroundSource.Nodes, app, policy, 11003)
        assertTrue(returned.appReentered)
        assertNull(returned.pending)
        assertFalse(tracker.observe(ForegroundSource.Nodes, app, policy, 11110).appReentered)
    }

    @Test
    fun taskCanChangeBelowShadeAndExitWaitsForCorrectFreshNodes() {
        val tracker = ForegroundTracker(app)
        tracker.observeSurface(true, 1)
        assertEquals(
            systemUi,
            tracker.observe(ForegroundSource.Nodes, systemUi, policy, 2).confirmed,
        )
        val next = ForegroundIdentity("other", "Target")
        assertNull(tracker.observe(ForegroundSource.TaskStack, next, policy, 3).pending)
        assertEquals(next, tracker.observeSurface(false, 4).pending)
        assertEquals(next, tracker.observe(ForegroundSource.Nodes, systemUi, policy, 5).pending)
        val returned = tracker.observe(ForegroundSource.Nodes, next, policy, 6)
        assertEquals(next, returned.confirmed)
        assertTrue(returned.changed)
        assertFalse(returned.appReentered)
    }

    @Test
    fun unavailableWindowListenerReleasesConstraintWithoutInventingClearState() {
        val tracker = ForegroundTracker(app)
        tracker.observeSurface(true, 1)
        tracker.observe(ForegroundSource.Nodes, systemUi, policy, 2)
        assertEquals(
            ForegroundDecisionReason.SurfaceUnavailable,
            tracker.observeSurface(null, 3).reason,
        )
        assertEquals(systemUi, tracker.snapshot().confirmed)
        tracker.observe(ForegroundSource.Accessibility, app, policy, 4)
        assertEquals(app, tracker.observe(ForegroundSource.Nodes, app, policy, 5).confirmed)
    }

    @Test
    fun surfaceInputsAndDuplicateSignalsDoNotCancelAgain() {
        val reducer = MatchInputReducer(app)
        val records =
            listOf(
                    MatchInput.SystemSurface(false),
                    MatchInput.SystemSurface(true),
                    MatchInput.SystemSurface(true),
                    MatchInput.Foreground(ForegroundSource.TaskStack, app),
                    MatchInput.SystemSurface(false),
                    MatchInput.Foreground(ForegroundSource.Nodes, app),
                    MatchInput.SystemSurface(false),
                )
                .mapIndexed { index, input ->
                    MatchInputRecord(index + 1L, index * 100L, null, policy, input)
                }
        val results = records.map {
            reducer.accept(it.input, it.policy, it.receivedAtMillis, it.sourceTimeMillis)
        }
        assertEquals(
            listOf(false, true, false, false, true, true, false),
            results.map { it.cancelWork },
        )
    }
}
