package li.gkd.app.matching.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Application
import android.graphics.Path
import android.os.Handler
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [26, 33],
    application = Application::class,
    manifest = Config.NONE,
    shadows = [A11yGesturesTest.ServiceShadow::class],
)
class A11yGesturesTest {
    @Implements(AccessibilityService::class)
    class ServiceShadow {
        val info = AccessibilityServiceInfo()
        var submissions = 0

        @Implementation fun getServiceInfo() = info

        @Implementation
        fun dispatchGesture(
            gesture: GestureDescription,
            callback: AccessibilityService.GestureResultCallback?,
            handler: Handler?,
        ): Boolean {
            submissions++
            callback?.onCompleted(gesture)
            return true
        }
    }

    private class Service : AccessibilityService() {
        override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

        override fun onInterrupt() {}
    }

    private fun tap() =
        GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    Path().apply { moveTo(10f, 20f) },
                    0,
                    100,
                ),
            )
            .build()

    /**
     * A no-gesture service must reject both action paths before calling the platform dispatcher.
     */
    @Test
    fun serviceWithoutGestureCapabilityNeverSubmitsTapOrSwipe() = runBlocking {
        val service = Service()
        val shadow = Shadow.extract<ServiceShadow>(service)
        assertFalse(service.awaitGesture(tap()))
        assertFalse(service.awaitSwipe(10f, 20f, 30f, 40f, 500))
        assertEquals(0, shadow.submissions)
    }

    /**
     * The ordinary service retains its gesture fallback when the required capability is present.
     */
    @Test
    fun gestureCapableServiceStillDispatchesAndAwaitsCompletion() = runBlocking {
        val service = Service()
        val shadow = Shadow.extract<ServiceShadow>(service)
        ReflectionHelpers.setField(
            shadow.info,
            "mCapabilities",
            AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES,
        )
        assertTrue(service.awaitGesture(tap()))
        assertEquals(1, shadow.submissions)
        assertTrue(service.awaitSwipe(10f, 20f, 30f, 40f, 500))
        assertTrue(shadow.submissions > 1)
    }
}
