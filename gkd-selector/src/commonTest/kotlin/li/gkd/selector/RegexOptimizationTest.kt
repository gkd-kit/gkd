package li.gkd.selector

import li.gkd.selector.property.RegexCompileResult
import li.gkd.selector.property.compilePlatformRegex
import li.gkd.selector.property.compileRegex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class RegexOptimizationTest {
    @Test
    fun optimizationPreservesPlatformUnicodeCaseSemantics() {
        val cases = listOf(
            "(?is)k.*" to "Kelvin",
            "(?is).*s.*" to "prefix ſ suffix",
            "(?is).*ä" to "prefix Ä",
            "(?is)ss.*" to "ß suffix",
            "(?is).*ss.*" to "prefix ß suffix",
            "(?is).*ss" to "prefix ß",
        )

        cases.forEach { (pattern, input) ->
            val optimized = assertIs<RegexCompileResult.Success>(pattern.compileRegex())
            val platform = assertIs<RegexCompileResult.Success>(pattern.compilePlatformRegex())
            assertEquals(
                platform.matches(input),
                optimized.matches(input),
                "$pattern / $input",
            )
        }
    }

    @Test
    fun optimizedRegexFormsPreserveMatchesAndNotMatchesBehavior() {
        data class Case(
            val pattern: String,
            val matchingText: String,
            val mismatchingText: String,
        )

        val cases = listOf(
            Case("(?is)abc.*", "AbC tail", "tail AbC"),
            Case("(?is).*abc.*", "prefix AbC suffix", "abx"),
            Case("(?is).*abc", "prefix AbC", "AbC suffix"),
            Case("(?is).*ababaca.*", "prefix ABABABACA suffix", "ABABABAXA"),
            Case("(?is)a你b好c.*", "A你B好C tail", "A他B好C tail"),
            Case("(?is).*a你b好c.*", "prefix A你B好C suffix", "A你B他C"),
            Case("(?is).*a你b好c", "prefix A你B好C", "A你B好C suffix"),
        )

        cases.forEach { case ->
            listOf(false, true).forEach { negated ->
                val operator = if (negated) "!~=" else "~="
                val source = "[text$operator'${case.pattern}']"
                val selectors = listOf(
                    Selector.compile(source).value,
                    Selector.parse(source).value,
                )
                val matchingNode = TestNode(
                    key = "matching",
                    name = "View",
                    attributes = mapOf("text" to case.matchingText),
                )
                val mismatchingNode = TestNode(
                    key = "mismatching",
                    name = "View",
                    attributes = mapOf("text" to case.mismatchingText),
                )

                selectors.forEach { selector ->
                    if (negated) {
                        assertNull(selector.match(matchingNode, TestNodeAdapter), source)
                        assertSame(
                            mismatchingNode,
                            selector.match(mismatchingNode, TestNodeAdapter),
                            source,
                        )
                    } else {
                        assertSame(
                            matchingNode,
                            selector.match(matchingNode, TestNodeAdapter),
                            source,
                        )
                        assertNull(selector.match(mismatchingNode, TestNodeAdapter), source)
                    }
                }
            }
        }
    }

    @Test
    fun emptySimpleRegexMatchesEveryText() {
        val selector = Selector.compile("[text~='(?is).*']").value
        listOf("", "anything", "中文").forEach { text ->
            val node = TestNode("node", "View", mapOf("text" to text))
            assertSame(node, selector.match(node, TestNodeAdapter), text)
        }
    }

    @Test
    fun regexMetacharactersKeepTheirRegexMeaning() {
        val node = TestNode(
            key = "node",
            name = "View",
            attributes = mapOf("text" to "a.b"),
        )
        assertSame(
            node,
            Selector.compile("[text~='(?is).*a\\\\.b']").value.match(node, TestNodeAdapter),
        )
    }
}
