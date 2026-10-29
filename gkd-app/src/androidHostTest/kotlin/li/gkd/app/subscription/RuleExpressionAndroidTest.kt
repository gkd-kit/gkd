package li.gkd.app.subscription

import android.app.Application
import android.graphics.Rect
import li.gkd.app.model.ScreenSize
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise Android coordinates and reject function calls before action execution. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 33], application = Application::class, manifest = Config.NONE)
class RuleExpressionAndroidTest {
    @Test
    fun compiledCoordinatesUseAndroidBoundsAndRestrictedMath() {
        val rect = Rect(10, 20, 110, 220)
        val bounds = RuleBounds(rect.left, rect.top, rect.right, rect.bottom)
        val expression = compileRuleExpression("width*0.5 + (20-2)/2")!!
        assertEquals(59.0, expression.evaluate(bounds, ScreenSize(1080, 2400)), 0.0)
        assertEquals(
            -4.0,
            compileRuleExpression("-2*2")!!.evaluate(bounds, ScreenSize(1080, 2400)),
            0.0,
        )
        for (source in
            listOf(
                "-2^2",
                "2^3^2",
                "2^-2",
                "pow(2,3)",
                "sin(1)",
                "pi",
                "2width",
                "min(1,,2)",
                "width*.5",
            )) {
            assertNull(source, compileRuleExpression(source))
        }
        val position = RawSubscription.Position("width/2", null, null, "height/4", null, null)
        assertEquals(60f to 170f, position.calc(bounds, ScreenSize(1080, 2400)))
    }

    @Test
    fun invalidArithmeticCannotProduceAnAndroidActionPosition() {
        val bounds = RuleBounds(0, 0, 100, 200)
        for (source in listOf("1e309-1e309", "1e39", "1e308*1e308")) {
            val error =
                assertThrows(PositionCalculationException::class.java) {
                        RawSubscription.Position(null, null, null, null, source, "10")
                            .calc(bounds, ScreenSize(1080, 2400))
                    }
                    .error
            assertEquals("NON_FINITE_COORDINATE", error.code)
        }
        assertNull(compileRuleExpression("screenwidth/2"))
        for (source in listOf("1 / 0", "1 % -0")) {
            val position = RawSubscription.Position(null, null, null, null, source, "10")
            assertTrue(position.isValid)
            val error =
                assertThrows(PositionCalculationException::class.java) {
                        position.calc(bounds, ScreenSize(100, 200))
                    }
                    .error
            assertEquals("DIVISION_BY_ZERO", error.code)
            assertEquals(2, error.startIndex)
            assertEquals(3, error.endIndex)
        }
    }

    @Test
    fun bindingErrorsAreRejectedBeforeAndroidActionsWithoutEvaluatingCoordinates() {
        for (source in
            listOf(
                "min(1,2)",
                "max(1,2)",
                "clamp(width,0,100)",
                "clamp(1,max(2,3),4)",
                "1/0 + clamp(1,2,3)",
                "screen.width",
                "unknown(width)",
            )) {
            assertNull(source, compileRuleExpression(source))
            assertFalse(
                source,
                RawSubscription.Position(null, null, null, null, source, "0").isValid,
            )
        }
        assertNotNull(compileRuleExpression("1/0 + width"))
        val bounds = RuleBounds(0, 0, 100, 200)
        assertEquals(
            0.0,
            compileRuleExpression("random-random")!!.evaluate(bounds, ScreenSize(100, 200)),
            0.0,
        )
    }
}
