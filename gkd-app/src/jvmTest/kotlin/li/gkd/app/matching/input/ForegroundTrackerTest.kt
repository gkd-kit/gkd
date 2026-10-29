package li.gkd.app.matching.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForegroundTrackerTest {
    @Test
    fun leaveAndReturnWithoutIntermediateNodesStartsOneNewVisitInBothModes() {
        // No launcher node is ever returned: accessibility-only and task-stack inputs
        // must both preserve the visit, while waiting for fresh app nodes to act.
        for (source in listOf(ForegroundSource.Accessibility, ForegroundSource.TaskStack)) {
            val app = ForegroundIdentity("video", "Splash")
            val policy = MatchTimingPolicy()
            val tracker = ForegroundTracker(app)
            tracker.observe(source, ForegroundIdentity("launcher", "Home"), policy, 100)
            val returning = tracker.observe(source, app, policy, 200)
            assertEquals(app, returning.pending)
            assertFalse(returning.appReentered)
            val confirmed =
                tracker.observe(ForegroundSource.Nodes, ForegroundIdentity("video"), policy, 210)
            assertTrue(confirmed.appReentered)
            assertEquals(ForegroundDecisionReason.AppReentered, confirmed.reason)
            assertNull(confirmed.pending)
            assertEquals(app, confirmed.confirmed)
            assertFalse(tracker.observe(source, app, policy, 220).appReentered)
            assertFalse(
                tracker
                    .observe(ForegroundSource.Nodes, ForegroundIdentity("video"), policy, 230)
                    .appReentered,
            )
        }
    }

    @Test
    fun activityChangesAndUnconfirmedNodeNoiseDoNotCreateAppVisits() {
        val policy = MatchTimingPolicy()
        for (source in listOf(ForegroundSource.Accessibility, ForegroundSource.TaskStack)) {
            val tracker = ForegroundTracker(ForegroundIdentity("video", "Splash"))
            tracker.observe(source, ForegroundIdentity("video", "Player"), policy, 100)
            tracker.observe(source, ForegroundIdentity("video", "Splash"), policy, 200)
            assertFalse(
                tracker
                    .observe(ForegroundSource.Nodes, ForegroundIdentity("video"), policy, 210)
                    .appReentered,
            )
            tracker.observe(ForegroundSource.Nodes, ForegroundIdentity("noise"), policy, 220)
            tracker.observe(source, ForegroundIdentity("video", "Splash"), policy, 230)
            assertFalse(
                tracker
                    .observe(ForegroundSource.Nodes, ForegroundIdentity("video"), policy, 240)
                    .appReentered,
            )
        }
    }

    @Test
    fun overlayNodesRemainAuthoritativeOverRepeatedUnderlyingTask() {
        // Notification shade/keyguard and launcher feeds must not require package equality.
        for (surface in listOf("com.android.systemui", "launcher.feed")) {
            var time = 0L
            val tracker = ForegroundTracker(ForegroundIdentity("app", "Main"), { time })
            val policy = MatchTimingPolicy()
            val task = ForegroundIdentity("app", "Main")
            tracker.observe(ForegroundSource.TaskStack, task, policy)
            tracker.observe(ForegroundSource.Nodes, ForegroundIdentity(surface), policy)
            time += policy.foregroundRetryMillis
            val overlay =
                tracker.observe(ForegroundSource.Nodes, ForegroundIdentity(surface), policy)
            assertEquals(surface, overlay.confirmed.appId)
            assertEquals(ForegroundDecisionReason.SurfaceConfirmed, overlay.reason)
            val repeatedTask = tracker.observe(ForegroundSource.TaskStack, task, policy)
            assertNull(repeatedTask.pending)
            assertEquals(surface, repeatedTask.confirmed.appId)
            assertEquals(task, repeatedTask.task)
            tracker.observe(ForegroundSource.Nodes, ForegroundIdentity("app"), policy)
            time += policy.foregroundRetryMillis
            val returned =
                tracker.observe(ForegroundSource.Nodes, ForegroundIdentity("app"), policy)
            assertEquals(task, returned.confirmed)
            assertNull(returned.pending)
        }
    }

    @Test
    fun newTaskDuringOverlayStillRequiresCorroboratingNodes() {
        var time = 0L
        val tracker = ForegroundTracker(ForegroundIdentity("app"), { time })
        val policy = MatchTimingPolicy()
        tracker.observe(ForegroundSource.TaskStack, ForegroundIdentity("app"), policy)
        tracker.observe(ForegroundSource.Nodes, ForegroundIdentity("systemui"), policy)
        time += policy.foregroundRetryMillis
        tracker.observe(ForegroundSource.Nodes, ForegroundIdentity("systemui"), policy)
        val task = ForegroundIdentity("other", "Other")
        assertEquals(task, tracker.observe(ForegroundSource.TaskStack, task, policy).pending)
        assertEquals("systemui", tracker.snapshot().confirmed.appId)
        assertEquals(
            task,
            tracker.observe(ForegroundSource.Nodes, ForegroundIdentity("other"), policy).confirmed,
        )
    }

    @Test
    fun taskCallbackDoesNotAuthorizeActionsBeforeNodesCatchUp() {
        var time = 0L
        val tracker = ForegroundTracker(ForegroundIdentity("a"), nowMillis = { time })
        val policy = MatchTimingPolicy()
        assertNotNull(
            tracker
                .observe(ForegroundSource.TaskStack, ForegroundIdentity("b", "B"), policy)
                .pending,
        )
        assertFalse(
            tracker.observe(ForegroundSource.Nodes, ForegroundIdentity("a"), policy).changed,
        )
        time += 100
        val result = tracker.observe(ForegroundSource.Nodes, ForegroundIdentity("b"), policy)
        assertTrue(result.changed)
        assertEquals(ForegroundIdentity("b", "B"), result.confirmed)
        assertNull(result.pending)
    }

    @Test
    fun nodesCanConfirmBeforeCallbackAndLateOldCallbackCannotRollBackImmediately() {
        var time = 0L
        val tracker = ForegroundTracker(ForegroundIdentity("a"), nowMillis = { time })
        val policy = MatchTimingPolicy()
        assertFalse(
            tracker.observe(ForegroundSource.Nodes, ForegroundIdentity("b"), policy).changed,
        )
        time = 100
        assertTrue(tracker.observe(ForegroundSource.Nodes, ForegroundIdentity("b"), policy).changed)
        assertFalse(
            tracker.observe(ForegroundSource.TaskStack, ForegroundIdentity("a"), policy).changed,
        )
        time = 3200
        val result = tracker.observe(ForegroundSource.Nodes, ForegroundIdentity("b"), policy)
        assertEquals("b", result.confirmed.appId)
        assertNull(result.pending)
    }
}
