package li.gkd.app.matching.query

import android.app.Application
import android.view.accessibility.AccessibilityNodeInfo
import li.gkd.app.util.monotonicTimeMillis
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 33], application = Application::class, manifest = Config.NONE)
class A11yNodeTimeTest {
    /** Cache age uses the engine's elapsed clock; missing or old timestamps are unusable. */
    @Test
    fun generatedNodeAgeSharesEngineClockAndExpiresAtTheConfiguredBoundary() {
        val node = AccessibilityNodeInfo.obtain()
        assertTrue(node.isExpired(1000))
        val before = monotonicTimeMillis()
        node.setGeneratedTime()
        val after = monotonicTimeMillis()
        assertTrue(node.extras.getLong("generatedTime") in before..after)
        assertFalse(node.isExpired(60_000))
        node.extras.putLong("generatedTime", monotonicTimeMillis() - 1001)
        assertTrue(node.isExpired(1000))
        assertFalse(node.isExpired(60_000))
        node.recycle()
    }
}
