package li.gkd.selector

import li.gkd.selector.property.RegexCompileResult
import li.gkd.selector.property.compileRegex
import li.gkd.selector.property.compileWasmRegex
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WasmRegexTest {
    @Test
    fun selectorMatchingUsesRegexWasm() {
        val matchingNode = TestNode(
            key = "node",
            name = "View",
            attributes = mapOf("text" to "ABC tail"),
        )
        val nonMatchingNode = TestNode(
            key = "other",
            name = "View",
            attributes = mapOf("text" to "other"),
        )
        val selector = Selector.compile("View[text~='(?is)abc.*']").value

        assertSame(
            matchingNode,
            selector.match(matchingNode, TestNodeAdapter),
        )
        assertNull(selector.match(nonMatchingNode, TestNodeAdapter))
    }

    @Test
    fun regexWasmMatchesTheWholeInput() {
        val matchesDigits = assertIs<RegexCompileResult.Success>("\\d+".compileRegex()).matches

        assertTrue(matchesDigits("123"))
        assertFalse(matchesDigits("123a"))
    }

    @Test
    fun regexWasmPlatformDifferencesRemainExplicit() {
        val unicodeWord = assertIs<RegexCompileResult.Success>("(?U)\\w+".compileRegex())
        assertFalse(unicodeWord.matches("中文"))
        assertIs<RegexCompileResult.Failure>("\\p{javaLowerCase}+".compileRegex())
    }

    @Test
    fun regexWasmConstructionFailuresRetainDiagnostics() {
        val runtimeError = IllegalStateException("engine unavailable")

        val failure = assertIs<RegexCompileResult.Failure>(
            "pattern".compileWasmRegex { throw runtimeError },
        )
        assertTrue(failure.detail.contains("IllegalStateException"))
        assertTrue(failure.detail.contains("engine unavailable"))
    }
}
