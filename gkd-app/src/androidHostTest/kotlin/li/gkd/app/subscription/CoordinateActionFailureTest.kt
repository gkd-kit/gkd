package li.gkd.app.subscription

import android.app.Application
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.runBlocking
import li.gkd.app.matching.action.ActionPerformer
import li.gkd.app.model.GkdAction
import li.gkd.app.model.ScreenSize
import li.gkd.app.util.ScreenUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Only the platform screen dimensions are simulated; real performers must stop before injection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [26, 33],
    application = Application::class,
    manifest = Config.NONE,
    shadows = [CoordinateActionFailureTest.ScreenShadow::class],
    instrumentedPackages = ["li.gkd.app.util"],
)
class CoordinateActionFailureTest {
    @Implements(value = ScreenUtils::class, isInAndroidSdk = false)
    class ScreenShadow {
        @Implementation fun getSize(): ScreenSize = ScreenSize(1080, 2400)

        @Implementation fun contains(x: Float, y: Float) = x in 0f..1080f && y in 0f..2400f
    }

    @Test
    fun clicksAndLongClicksDoNotFallBackAfterCoordinateFailure() = runBlocking {
        val node = AccessibilityNodeInfo.obtain().apply { setBoundsInScreen(Rect(0, 0, 100, 200)) }
        val position = RawSubscription.Position(null, null, null, null, "random/(width-width)", "0")
        for (performer in listOf(ActionPerformer.ClickCenter, ActionPerformer.LongClickCenter)) {
            val result =
                performer.perform(node, GkdAction("*", position = position), captureInputs = true)
            assertFalse(result.result)
            assertNull(result.position)
            assertEquals("position.x", result.coordinateError?.field)
            assertEquals("DIVISION_BY_ZERO", result.coordinateError?.code)
            assertEquals(100.0, result.coordinateError!!.inputs!!.getValue("width"), 0.0)
        }
    }

    @Test
    fun configuredSwipeEndFailureDoesNotBecomeStationaryGesture() = runBlocking {
        val node = AccessibilityNodeInfo.obtain().apply { setBoundsInScreen(Rect(0, 0, 100, 200)) }
        val group =
            RawSubscriptionParser.parse(
                    """{id:1,name:'T',version:1,globalGroups:[{key:1,name:'G',rules:[{swipeArg:{start:{x:10,y:20},end:{x:'100/(height-height)',y:30},duration:500}}]}]}""",
                )
                .globalGroups
                .single()
        assertTrue(group.valid)
        val result = ActionPerformer.Swipe.perform(node, group.rules.single())
        assertFalse(result.result)
        assertEquals("swipeArg.end.x", result.coordinateError?.field)
        assertEquals("DIVISION_BY_ZERO", result.coordinateError?.code)
        assertNull(result.coordinateError?.inputs)
    }
}
