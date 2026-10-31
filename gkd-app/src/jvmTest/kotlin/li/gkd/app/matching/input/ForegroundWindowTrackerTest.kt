package li.gkd.app.matching.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForegroundWindowTrackerTest {
    private val search = ForegroundIdentity("bili", "Search")
    private val video = ForegroundIdentity("bili", "Video")

    @Test
    fun repeatedActivityConfirmationKeepsWindowButReplacementInvalidatesIt() {
        val windows = ForegroundWindowTracker()
        val main = ForegroundIdentity("app.test.share", "MainActivity")
        windows.observeState(16145, main)
        assertFalse(windows.observeRoot(16145, main.appId, main))
        // Share return regression: a second Activity event arrives after the skip node.
        assertFalse(windows.observeState(16145, main))
        assertTrue(windows.isCurrentKnownWindow(16145, main.appId))
        // A different window with the same Activity still invalidates the old target.
        assertTrue(windows.observeState(16146, main))
        assertFalse(windows.isCurrentKnownWindow(16145, main.appId))
        assertTrue(windows.observeState(16146, main.copy(activityId = "OtherActivity")))
    }

    @Test
    fun newRootCannotInheritOldActivityBeforeWindowEvent() {
        val windows = ForegroundWindowTracker()
        windows.observeState(10, video)
        assertFalse(windows.observeRoot(10, "bili", video))
        // Recorded regression: the search root arrives before either Activity callback.
        assertTrue(windows.observeRoot(20, "bili", video))
        assertEquals(ForegroundIdentity("bili"), windows.identity)
        assertFalse(windows.isCurrentKnownWindow(10, "bili"))
        assertTrue(windows.isCurrentKnownWindow(20, "bili"))
        assertFalse(windows.observeRoot(20, "bili", video))
        assertTrue(windows.observeState(20, search))
        assertTrue(windows.isCurrentKnownWindow(20, "bili"))
        assertEquals(search, windows.identity)
    }

    @Test
    fun returningToKnownSearchWindowNeverUsesVideoIdentity() {
        val windows = ForegroundWindowTracker()
        windows.observeState(20, search)
        windows.observeState(10, video)
        assertTrue(windows.observeRoot(20, "bili", video))
        assertEquals(search, windows.identity)
        assertFalse(windows.isCurrentKnownWindow(10, "bili"))
    }

    @Test
    fun dialogAndItsDismissalKeepActivityWithoutNeedingAnotherActivityEvent() {
        val windows = ForegroundWindowTracker()
        val foreground = ForegroundTracker(video)
        val policy = MatchTimingPolicy()
        windows.observeState(10, video)
        windows.observeRoot(11, "bili", video)
        assertEquals(ForegroundIdentity("bili"), windows.identity)
        windows.observeState(11, video)
        assertTrue(windows.isCurrentKnownWindow(11, "bili"))
        assertEquals(video, windows.identity)
        val dialog = foreground.observe(ForegroundSource.Nodes, windows.identity!!, policy, 100)
        assertFalse(dialog.changed)
        assertFalse(dialog.appReentered)
        assertNull(dialog.pending)
        // Dialog dismissal can expose the underlying root without a new Activity event.
        assertTrue(windows.observeRoot(10, "bili", video))
        assertEquals(video, windows.identity)
        assertTrue(windows.isCurrentKnownWindow(10, "bili"))
        val dismissed = foreground.observe(ForegroundSource.Nodes, windows.identity!!, policy, 200)
        assertFalse(dismissed.changed)
        assertFalse(dismissed.appReentered)
        assertNull(dismissed.pending)
    }

    @Test
    fun notificationShadeWithoutWindowEventIdDoesNotLoseUnderlyingWindow() {
        val windows = ForegroundWindowTracker()
        val foreground = ForegroundTracker(video)
        val policy = MatchTimingPolicy()
        windows.observeState(10, video)
        foreground.observeSurface(true, 100)
        val system = ForegroundIdentity("com.android.systemui")
        assertFalse(windows.observeState(-1, system))
        assertTrue(windows.observeRoot(30, system.appId, video))
        assertFalse(windows.isCurrentKnownWindow(10, "bili"))
        assertTrue(windows.observeRoot(10, "bili", system))
        foreground.observeSurface(false, 200)
        assertEquals(video, windows.identity)
        assertTrue(windows.isCurrentKnownWindow(10, "bili"))
        val returned = foreground.observe(ForegroundSource.Nodes, windows.identity!!, policy, 210)
        assertEquals(video, returned.confirmed)
        assertNull(returned.pending)
        assertTrue(returned.appReentered)
        assertFalse(foreground.observe(ForegroundSource.Nodes, video, policy, 220).appReentered)
    }

    @Test
    fun connectionBootstrapDoesNotCarryBindingsIntoAnotherConnection() {
        val first = ForegroundWindowTracker()
        first.observeState(10, video)
        val second = ForegroundWindowTracker()
        assertTrue(second.observeRoot(20, "bili", search))
        assertEquals(search, second.identity)
        assertNull(second.knownIdentity(10, "bili"))
    }

    @Test
    fun splashRootWithoutStateEventConfirmsPendingTask() {
        val launcher = ForegroundIdentity("launcher", "Home")
        val splash = ForegroundIdentity("smzdm", "Welcome")
        val windows = ForegroundWindowTracker()
        val foreground = ForegroundTracker(launcher)
        val policy = MatchTimingPolicy()
        windows.observeState(3, launcher)
        foreground.observe(ForegroundSource.TaskStack, splash, policy, 100)
        // Regression: state event belongs to 17944, countdown content/root to 17945.
        windows.observeState(17944, splash)
        windows.observeRoot(17945, splash.appId, launcher)
        assertEquals(ForegroundIdentity(splash.appId), windows.identity)
        val result = foreground.observe(ForegroundSource.Nodes, windows.identity!!, policy, 200)
        assertEquals(splash, result.confirmed)
        assertNull(result.pending)
        assertTrue(windows.isCurrentKnownWindow(17945, splash.appId))
        assertFalse(windows.isCurrentKnownWindow(17944, splash.appId))
    }
}
