package li.gkd.app.a11y

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUpdateTriggerTest {
    private val launcherAppId = "com.miui.home"

    @Test
    fun acceptsLauncherStateAndContentEvents() {
        assertTrue(
            isLauncherAutoUpdateEvent(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                launcherAppId,
                launcherAppId,
            )
        )
        assertTrue(
            isLauncherAutoUpdateEvent(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                launcherAppId,
                launcherAppId,
            )
        )
    }

    @Test
    fun comparesPackageNamesByText() {
        val packageName: CharSequence = StringBuilder(launcherAppId)
        assertTrue(
            isLauncherAutoUpdateEvent(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                packageName,
                launcherAppId,
            )
        )
    }

    @Test
    fun rejectsOtherEventsAndPackages() {
        assertFalse(
            isLauncherAutoUpdateEvent(
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                launcherAppId,
                launcherAppId,
            )
        )
        assertFalse(
            isLauncherAutoUpdateEvent(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                "com.example.other",
                launcherAppId,
            )
        )
        assertFalse(
            isLauncherAutoUpdateEvent(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                launcherAppId,
                "",
            )
        )
    }
}