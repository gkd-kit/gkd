package li.gkd.selector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class RecordingObserver : SelectorObserver<TestNode> {
    override var enabled = true
    val events = mutableListOf<SelectorEvaluation<TestNode>>()

    override fun record(event: SelectorEvaluation<TestNode>) {
        events.add(event)
    }
}

private class CountingAdapter(
    private val beforeRead: (Any, String) -> Unit = { _, _ -> },
) : NodeAdapter<TestNode>() {
    val reads = mutableListOf<String>()

    override fun getAttr(target: Any, name: String): Any? {
        beforeRead(target, name)
        reads.add("attr:$target:$name")
        return TestNodeAdapter.getAttr(target, name)
    }

    override fun getName(node: TestNode): String? {
        reads.add("name:${node.key}")
        return node.name
    }

    override fun getChildCount(node: TestNode): Int {
        reads.add("count:${node.key}")
        return node.children.size
    }

    override fun getChild(node: TestNode, index: Int): TestNode? {
        reads.add("child:${node.key}:$index")
        return node.children.getOrNull(index)
    }

    override fun getParent(node: TestNode): TestNode? {
        reads.add("parent:${node.key}")
        return node.parent
    }

    override fun getNodeKey(node: TestNode): Any {
        reads.add("key:${node.key}")
        return node.key
    }
}

class SelectorExplanationTest {
    @Test
    fun comparisonValuesAreReadOnceAndShortCircuitedAttributesStayUnread() {
        val node =
            TestNode(
                "button",
                "android.widget.Button",
                mapOf("text" to "Close", "clickable" to false),
            )
        val observer = RecordingObserver()
        val adapter = CountingAdapter()
        assertNull(
            compileSelector("Button[text='Skip' && clickable=true]")
                .match(node, adapter, observer = observer),
        )
        val comparison = observer.events.single { it.kind == SelectorEvaluationKind.Comparison }
        assertEquals("Close", comparison.left)
        assertEquals("Skip", comparison.right)
        assertEquals(false, comparison.matched)
        assertEquals("=", comparison.operator)
        assertTrue(
            observer.events.any {
                it.kind == SelectorEvaluationKind.Skipped &&
                    it.expression == "&&: right operand skipped" &&
                    it.matched == null
            },
        )
        assertEquals(listOf("name:button", "attr:button:text"), adapter.reads)
    }

    @Test
    fun logicalNegationAndMissingValuesDoNotMisreportOverallFailure() {
        val node = TestNode("button", "Button", mapOf("text" to "Close"))
        for (source in
            listOf("[!(text='Skip') && missing=null]", "!([text='Skip'])", "[missing!~='.*']")) {
            val observer = RecordingObserver()
            val selector = compileSelector(source)
            assertSame(
                selector.match(node, TestNodeAdapter),
                selector.match(node, TestNodeAdapter, observer = observer),
                source,
            )
            assertTrue(
                observer.events.any {
                    it.kind == SelectorEvaluationKind.Comparison && it.matched == false
                },
                source,
            )
        }
        val observer = RecordingObserver()
        compileSelector("[missing!~='.*']").match(node, TestNodeAdapter, observer = observer)
        assertNull(observer.events.single().left)
        assertEquals(".*", observer.events.single().right)
    }

    @Test
    fun normalAndExplainedQueriesReadTheSameNodesInTheSameOrder() {
        val root = testTree()
        val sources =
            listOf(
                "Button[text='Confirm'][clickable=true]",
                "[width>100 && text!='missing']",
                "[text~='Alpha[0-9]+']",
                "[text!~='Beta.*']",
                "[missing=null]",
                "[text.substring(0,3)='Con']",
                "[text=desc || clickable=true]",
                "(Button[text='Confirm']) || (ImageView[desc='logo'])",
                "!(Button[text='Confirm'])",
                "(Button[text='Confirm']) && ([clickable=false])",
                "@LinearLayout > Button[text='Confirm']",
                "Never >n Button",
                "[prev!=null] >n Button",
                "[getPrev(0).text='Confirm'] >n Button",
            )
        for (fast in listOf(false, true)) for (source in sources) {
            val selector = compileSelector(source)
            val normal = CountingAdapter()
            val explained = CountingAdapter()
            val observer = RecordingObserver()
            val options = MatchOptions(fastQuery = fast)
            val expected = normal.querySelector(root, selector, options)
            val result = explained.querySelector(root, selector, options, observer)
            assertSame(expected, result, "$source fast=$fast")
            assertEquals(normal.reads, explained.reads, "$source fast=$fast")
            normal.reads.clear()
            explained.reads.clear()
            observer.events.clear()
            assertEquals(
                normal.querySelectorAll(root, selector, options),
                explained.querySelectorAll(root, selector, options, observer),
                "$source fast=$fast all",
            )
            assertEquals(normal.reads, explained.reads, "$source fast=$fast all")
        }
    }

    @Test
    fun relationFailureCacheAndEmptyFastCandidatesAreExplainedWithoutExtraTraversal() {
        val leaf = TestNode("leaf", "N")
        var root = leaf
        repeat(6) { root = TestNode("n$it", "N", children = listOf(root)) }
        val selector = compileSelector("Never >n N >n N >n N")
        val normal = CountingAdapter()
        val explained = CountingAdapter()
        val observer = RecordingObserver()
        assertNull(selector.match(leaf, normal))
        assertNull(selector.match(leaf, explained, observer = observer))
        assertEquals(normal.reads, explained.reads)
        assertTrue(observer.events.any { it.kind == SelectorEvaluationKind.RelationExhausted })
        assertTrue(observer.events.any { it.kind == SelectorEvaluationKind.CachedFailure })
        val empty = RecordingObserver()
        assertNull(
            explained.querySelector(
                root,
                compileSelector("[text='absent']"),
                MatchOptions(true),
                empty,
            ),
        )
        assertEquals(
            listOf(SelectorEvaluationKind.FastQueryScope, SelectorEvaluationKind.NoCandidates),
            empty.events.map { it.kind },
        )
        empty.events.forEach { event ->
            assertNull(event.unit)
            assertNull(event.property)
        }
    }

    @Test
    fun observerCanStopRecordingWithoutChangingTheRemainingEvaluation() {
        val node =
            TestNode("node", "View", mapOf("text" to "Close", "clickable" to true, "desc" to "x"))
        for ((source, matches) in
            listOf(
                "[text='Close'][clickable=true]" to true,
                "[text='Close' && desc~='x']" to true,
                "[text='Close' && desc~='y']" to false,
            )) {
            val events = mutableListOf<SelectorEvaluation<TestNode>>()
            val observer =
                object : SelectorObserver<TestNode> {
                    override val enabled
                        get() = events.isEmpty()

                    override fun record(event: SelectorEvaluation<TestNode>) {
                        events.add(event)
                    }
                }
            val selector = compileSelector(source)
            val normal = CountingAdapter()
            val explained = CountingAdapter()
            assertSame(if (matches) node else null, selector.match(node, normal), source)
            assertSame(
                if (matches) node else null,
                selector.match(node, explained, observer = observer),
                source,
            )
            assertEquals(normal.reads, explained.reads, source)
            assertEquals(1, events.size)
            assertFalse(observer.enabled)
        }
    }

    @Test
    fun initiallyDisabledObserverPreservesQueryReadsWithoutEvents() {
        val root = testTree()
        for (fast in listOf(false, true)) {
            val selector = compileSelector("Button[text='Confirm'][clickable=true]")
            val normal = CountingAdapter()
            val disabled = CountingAdapter()
            val observer = RecordingObserver().apply { enabled = false }
            val options = MatchOptions(fastQuery = fast)
            assertEquals(
                normal.querySelectorAll(root, selector, options),
                disabled.querySelectorAll(root, selector, options, observer),
            )
            assertEquals(normal.reads, disabled.reads)
            assertTrue(observer.events.isEmpty())
        }
    }

    @Test
    fun shortCircuitedOperandsNeverExecuteThrowingReads() {
        val node = TestNode("node", "View", mapOf("text" to "Close"))
        for ((source, matches) in
            listOf(
                "[text='Other' && forbidden=true]" to false,
                "[text='Close' || forbidden=true]" to true,
                "([text='Other']) && ([forbidden=true])" to false,
                "([text='Close']) || ([forbidden=true])" to true,
            )) {
            val selector = compileSelector(source)
            val normal = CountingAdapter { _, name -> check(name != "forbidden") }
            val explained = CountingAdapter { _, name -> check(name != "forbidden") }
            assertSame(if (matches) node else null, selector.match(node, normal), source)
            assertSame(
                if (matches) node else null,
                selector.match(node, explained, observer = RecordingObserver()),
                source,
            )
            assertEquals(normal.reads, explained.reads, source)
        }
    }

    @Test
    fun firstMatchDoesNotReadLaterCandidatesForExplanation() {
        val first = TestNode("first", "View", mapOf("text" to "Close"))
        val later = TestNode("later", "View", mapOf("text" to "Close"))
        val root = TestNode("root", "Root", children = listOf(first, later))
        val selector = compileSelector("[text='Close']")
        for (fast in listOf(false, true)) {
            val normal = CountingAdapter { target, _ -> check(target !== later) }
            val explained = CountingAdapter { target, _ -> check(target !== later) }
            val options = MatchOptions(fastQuery = fast)
            assertSame(first, normal.querySelector(root, selector, options))
            assertSame(first, explained.querySelector(root, selector, options, RecordingObserver()))
            assertEquals(normal.reads, explained.reads)
        }
    }

    @Test
    fun longLogicalChainsKeepDiagnosticTextLinearAndReadsEquivalent() {
        val node = TestNode("node", "View", mapOf("text" to "Close"))
        val count = 1_000
        for (operator in listOf("&&", "||")) {
            val comparison = if (operator == "&&") "text='Close'" else "text='Other'"
            val selector =
                compileSelector("[${List(count) { comparison }.joinToString(" $operator ")}]")
            val normal = CountingAdapter()
            val explained = CountingAdapter()
            val observer = RecordingObserver()
            assertSame(if (operator == "&&") node else null, selector.match(node, normal))
            assertSame(
                if (operator == "&&") node else null,
                selector.match(node, explained, observer = observer),
            )
            assertEquals(normal.reads, explained.reads)
            assertEquals(
                count,
                observer.events.count { it.kind == SelectorEvaluationKind.Comparison },
            )
            val logical = observer.events.filter { it.kind == SelectorEvaluationKind.Logical }
            assertEquals(count - 1, logical.size)
            assertTrue(logical.all { it.expression == operator })
        }
    }
}
