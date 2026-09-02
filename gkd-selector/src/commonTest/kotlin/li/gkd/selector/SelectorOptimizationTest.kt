package li.gkd.selector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SelectorOptimizationTest {
    @Test
    fun previousContextOnlyDisablesAffectedPropertySelectorCaches() {
        var root = TestNode("leaf", "N")
        val leaf = root
        repeat(23) { depth ->
            root = TestNode("node-$depth", "N", children = listOf(root))
        }
        val selector = compileSelector(
            "Never[missing=null]" +
                    " >n N".repeat(9) +
                    " >n N[prev!=null] >n N",
        )

        TestNodeAdapter.resetCounters()
        assertNull(selector.match(leaf, TestNodeAdapter))
        assertTrue(
            TestNodeAdapter.parentCallCount < 10_000,
            "Expected unaffected property selectors to stay cached, parent calls=${TestNodeAdapter.parentCallCount}",
        )
    }

    @Test
    fun previousRelationOnlyDisablesAffectedPropertySelectorCaches() {
        var root = TestNode("leaf", "N")
        val leaf = root
        repeat(23) { depth ->
            root = TestNode("node-$depth", "N", children = listOf(root))
        }
        val selector = compileSelector(
            "Never[missing=null]" + " >n N".repeat(9) + " -> N > N",
        )

        TestNodeAdapter.resetCounters()
        assertNull(selector.match(leaf, TestNodeAdapter))
        assertTrue(
            TestNodeAdapter.parentCallCount < 10_000,
            "Expected property selectors before -> to stay cached, parent calls=${TestNodeAdapter.parentCallCount}",
        )
    }

    @Test
    fun customFastQueryHookPreservesTraversalAndTraceResults() {
        val view = TestNode("view", "View", mapOf("id" to "x"))
        val button = TestNode("button", "Button", mapOf("id" to "b"))
        val root = TestNode("root", "Root", children = listOf(view, button))
        val selector = compileSelector("(Button[id='b']) || (View[id='x'])")
        val regularOption = MatchOptions(fastQuery = false)
        val fastOption = MatchOptions(fastQuery = true)

        val regular = TestNodeAdapter.querySelectorAll(root, selector, regularOption)
        val fast = TestNodeAdapter.querySelectorAll(root, selector, fastOption)
        assertEquals(listOf(view, button), regular)
        assertEquals(regular, fast)
        assertSame(view, TestNodeAdapter.querySelector(root, selector, fastOption))

        val regularTraceTargets = TestNodeAdapter
            .querySelectorAllWithTrace(root, selector, regularOption)
            .map { it.target }
        val fastTraceTargets = TestNodeAdapter
            .querySelectorAllWithTrace(root, selector, fastOption)
            .map { it.target }
        assertEquals(regular, regularTraceTargets)
        assertEquals(regularTraceTargets, fastTraceTargets)

        for (node in listOf(view, button)) {
            assertSame(
                selector.match(node, TestNodeAdapter, fastOption),
                selector.matchWithTrace(node, TestNodeAdapter, fastOption)?.target,
            )
        }
    }
}
