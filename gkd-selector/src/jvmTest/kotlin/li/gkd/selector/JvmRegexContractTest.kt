package li.gkd.selector

import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JvmRegexContractTest {
    @Test
    fun documentedJVMRegexDifferencesRemainVisible() {
        val chinese = TestNode("chinese", "View", mapOf("text" to "中文"))
        assertSame(
            chinese,
            compileSelector("[text~='(?U)\\\\w+']")
                .match(chinese, TestNodeAdapter),
        )

        val ascii = TestNode("ascii", "View", mapOf("text" to "abc"))
        assertSame(
            ascii,
            compileSelector("[text~='\\\\p{javaLowerCase}+']")
                .match(ascii, TestNodeAdapter),
        )
    }

    @Test
    fun simpleIncludeOptimizationRemainsLinearForLongAsciiInput() {
        val literal = "a".repeat(60_000) + "b"
        val selector = compileSelector("[text~='(?is).*$literal.*']")
        val node = TestNode(
            key = "long-text",
            name = "View",
            attributes = mapOf("text" to "a".repeat(120_000)),
        )

        val elapsed = measureTime {
            assertNull(selector.match(node, TestNodeAdapter))
        }

        assertTrue(elapsed < 1.seconds, "Expected linear matching, elapsed=$elapsed")
    }
}
