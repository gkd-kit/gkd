package li.gkd.selector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SelectorQueryTest {
    @Test
    fun parenthesizedValuesMatchLikeUngroupedValues() {
        val node = TestNode(
            key = "node",
            name = "View",
            attributes = mapOf(
                "text" to "Confirm",
                "desc" to "Confirm",
            ),
        )
        val equivalentSources = listOf(
            "View[text=(desc)]" to "View[text=desc]",
            "View[equal((text),('Confirm'))=true]" to
                    "View[equal(text,'Confirm')=true]",
            "View[text.substring((0),(3))=('Con')]" to
                    "View[text.substring(0,3)='Con']",
        )

        equivalentSources.forEach { (grouped, plain) ->
            val expected = Selector.compile(plain).value.match(node, TestNodeAdapter)
            assertSame(node, expected, plain)
            assertSame(
                expected,
                Selector.compile(grouped).value.match(node, TestNodeAdapter),
                grouped,
            )
        }
    }

    @Test
    fun compileAndParseBindRegexMatchersToTheirExpressions() {
        val node = TestNode(
            key = "node",
            name = "View",
            attributes = mapOf("text" to "Alpha42"),
        )
        val matchingSources = listOf(
            "[text~='Alpha[0-9]+']",
            "[text!~='Beta.*']",
        )
        val mismatchingSources = listOf(
            "[text~='Beta.*']",
            "[text!~='Alpha[0-9]+']",
        )

        matchingSources.forEach { source ->
            assertSame(
                node,
                Selector.compile(source).value.match(node, TestNodeAdapter),
                "compile: $source",
            )
            assertSame(
                node,
                Selector.parse(source).value.match(node, TestNodeAdapter),
                "parse: $source",
            )
        }
        mismatchingSources.forEach { source ->
            assertNull(
                Selector.compile(source).value.match(node, TestNodeAdapter),
                "compile: $source",
            )
            assertNull(
                Selector.parse(source).value.match(node, TestNodeAdapter),
                "parse: $source",
            )
        }
    }

    @Test
    fun propertyNamesLiteralsAndEveryComparisonOperatorMatch() {
        val node = TestNode(
            key = "node",
            name = "android.widget.TextView",
            attributes = mapOf(
                "text" to "Alpha42",
                "enabled" to true,
                "count" to 42,
                "empty" to "",
            ),
        )
        val cases = listOf(
            "TextView",
            "android.widget.TextView",
            "*",
            "[missing=null]",
            "[enabled=true]",
            "[count=42]",
            "[text='Alpha42']",
            "[text!='Beta']",
            "[count>41]",
            "[count>=42]",
            "[count<43]",
            "[count<=42]",
            "[text^='Alpha']",
            "[text!^='Beta']",
            "[text*='ha4']",
            "[text!*='zzz']",
            "[text$='42']",
            "[text!$='41']",
            "[text~='[A-Z][a-z]+\\\\d+']",
            "[text!~='Beta.*']",
            "[empty='']",
        )

        cases.forEach { source ->
            assertSame(node, compileSelector(source).match(node, TestNodeAdapter), source)
        }

        val mismatches = listOf(
            "Button",
            "[missing!=null]",
            "[enabled=false]",
            "[count=41]",
            "[text='Beta']",
            "[count>42]",
            "[count>=43]",
            "[count<42]",
            "[count<=41]",
            "[text^='Beta']",
            "[text!^='Alpha']",
            "[text*='zzz']",
            "[text!*='ha4']",
            "[text$='41']",
            "[text!$='42']",
            "[text~='Beta.*']",
            "[text!~='Alpha.*']",
        )
        mismatches.forEach { source ->
            assertNull(compileSelector(source).match(node, TestNodeAdapter), source)
        }
    }

    @Test
    fun contextKeywordsAreHandledByTheSelectorCore() {
        val first = TestNode("first", "A", mapOf("id" to "shared"))
        val second = TestNode("second", "B", mapOf("id" to "shared"))
        val current = TestNode("current", "C")
        TestNode("root", "Root", children = listOf(first, second, current))

        val selector = compileSelector(
            "@A[id=prev.id][id=getPrev(0).current.id][id=current.id] + B + C",
        )

        assertSame(first, selector.match(current, TestNodeAdapter))
    }

    @Test
    fun valueMembersCallsArgumentsAndBooleanPrecedenceMatch() {
        val node = TestNode(
            key = "node",
            name = "View",
            attributes = mapOf(
                "text" to "Confirm",
                "count" to 6,
                "enabled" to true,
            ),
        )
        val cases = listOf(
            "View[text.length=7]",
            "View[text.substring(0,3)='Con']",
            "View[count.plus(1)=7]",
            "View[count.times(2).minus(5)=7]",
            "View[count.toString()='6']",
            "View[count.toString(16)='6']",
            "View[enabled.not()=false]",
            "View[enabled.ifElse(text,count)='Confirm']",
            "View[text.at(-1)='m']",
            "View[equal(text.substring(0,3),'Con')=true]",
            "View[notEqual(count.plus(1),8)=true]",
            "View[count=5||text.length=7&&enabled=true]",
            "View[(count=5||text.length=7)&&enabled=true]",
            "View[!(count=5)]",
        )

        cases.forEach { source ->
            assertSame(node, compileSelector(source).match(node, TestNodeAdapter), source)
        }
    }

    @Test
    fun valueEvaluationPreservesNullPropagationAndLazyBooleanMethods() {
        val enabled = TestNode(
            key = "enabled",
            name = "View",
            attributes = mapOf("enabled" to true, "text" to "yes"),
        )
        val disabled = TestNode(
            key = "disabled",
            name = "View",
            attributes = mapOf("enabled" to false, "text" to "no"),
        )

        val enabledCases = listOf(
            "View[equal(missing,null)=true]",
            "View[notEqual(missing,null)=false]",
            "View[parent.text=null]",
            "View[text.substring(missing)=null]",
            "View[enabled.or(unknown())=true]",
            "View[enabled.ifElse(text,unknown())='yes']",
        )
        enabledCases.forEach { source ->
            assertSame(enabled, compileSelector(source).match(enabled, TestNodeAdapter), source)
        }

        val disabledCases = listOf(
            "View[enabled.and(unknown())=false]",
            "View[enabled.ifElse(unknown(),text)='no']",
        )
        disabledCases.forEach { source ->
            assertSame(disabled, compileSelector(source).match(disabled, TestNodeAdapter), source)
        }

        val invalidCalls = listOf(
            "View[enabled.not(true)=true]",
            "View[enabled.and(1)=true]",
            "View[text.substring(-1)=text]",
            "View[text.substring(2,1)=text]",
            "View[text.get(99)=text]",
            "View[text.length.div(0)=1]",
        )
        invalidCalls.forEach { source ->
            assertNull(compileSelector(source).match(enabled, TestNodeAdapter), source)
        }
    }

    @Test
    fun everyRelationshipDirectionRangeAndTargetMarkerMatch() {
        val root = testTree()
        val cases = listOf(
            MatchCase("TextView[text='Title'] + Button[text='Confirm']", "confirm", "confirm"),
            MatchCase("@TextView[text='Title'] + Button[text='Confirm']", "confirm", "title"),
            MatchCase("Button[text='Confirm'] - TextView[text='Title']", "title", "title"),
            MatchCase("@Button[text='Confirm'] - TextView[text='Title']", "title", "confirm"),
            MatchCase("Root >n TextView[text='Alpha42']", "alpha", "alpha"),
            MatchCase("TextView[text='Alpha42'] < Card", "card1", "card1"),
            MatchCase("TextView[text='Beta7'] <<n FrameLayout[vid='content']", "content", "content"),
            MatchCase("Root TextView[text='Beta7']", "beta", "beta"),
            MatchCase("@TextView[text='Title'] +2 Button[text='Cancel']", "cancel", "title"),
            MatchCase("@TextView[text='Title'] +(1,2) Button[text='Cancel']", "cancel", "title"),
            MatchCase("@TextView[text='Title'] +(2n) Button[text='Cancel']", "cancel", "title"),
            MatchCase(
                "TextView[text='Alpha42'] -> @Card > TextView[text='Alpha42']",
                "alpha",
                "card1",
            ),
        )

        cases.forEach { case ->
            val start = root.find(case.start)
            val actual = compileSelector(case.source)
                .match(start, TestNodeAdapter)
            assertEquals(case.expected, actual?.key, case.source)
        }
    }

    @Test
    fun relationshipSearchBacktracksAndReportsOnlyTheSuccessfulFlatPath() {
        val root = testTree()
        val alpha = root.find("alpha")
        val source = "@Root >n Card > TextView[text='Alpha42']"
        val selector = Selector.parse(source).value
        val result = assertNotNull(
            selector.matchWithTrace(alpha, TestNodeAdapter),
        )

        assertEquals("root", result.target.key)
        assertEquals(
            listOf("alpha > card1", "card1 >2 root"),
            result.units.single().steps.map {
                "${it.source.key} ${it.formattedRelation} ${it.target.key}"
            },
        )
        assertEquals(source, result.units.single().range?.let { source.substring(it.start, it.end) })
        assertEquals(
            listOf(">", ">n"),
            result.units.single().steps.map { step ->
                step.relationRange?.let { source.substring(it.start, it.end) }
            },
        )
    }

    @Test
    fun traceUnitRangeExcludesSelectorPaddingAndGrouping() {
        val button = TestNode("button", "Button")
        val root = TestNode("root", "Root", children = listOf(button))
        val source = "  (@Root > Button)  "
        val parsed = assertIs<SelectorParseResult.Success>(Selector.parse(source))
        val result = assertNotNull(
            parsed.value.matchWithTrace(button, TestNodeAdapter),
        )

        assertSame(root, result.target)
        val unitRange = assertNotNull(result.units.single().range)
        assertEquals("@Root > Button", source.substring(unitRange.start, unitRange.end))
        val selectorPosition = parsed.positions.single {
            it.kind == SelectorPositionKind.Selector
        }
        assertEquals(source, source.substring(selectorPosition.start, selectorPosition.end))
    }

    @Test
    fun traceUsesStableNodeKeysWhenAdaptersReturnFreshWrappers() {
        data class Record(
            val id: Int,
            val name: String,
            val parentId: Int?,
            val childIds: List<Int>,
        )

        class FreshNode(val id: Int)

        val records = mapOf(
            0 to Record(0, "Root", null, listOf(1)),
            1 to Record(1, "Leaf", 0, emptyList()),
        )
        var ancestorTraversalCount = 0
        val adapter = object : NodeAdapter<FreshNode>() {
            override fun getAttr(target: Any, name: String): Any? = null

            override fun getName(node: FreshNode): String = records.getValue(node.id).name

            override fun getChildCount(node: FreshNode): Int =
                records.getValue(node.id).childIds.size

            override fun getChild(node: FreshNode, index: Int): FreshNode? = records
                .getValue(node.id)
                .childIds
                .getOrNull(index)
                ?.let(::FreshNode)

            override fun getParent(node: FreshNode): FreshNode? =
                records.getValue(node.id).parentId?.let(::FreshNode)

            override fun getNodeKey(node: FreshNode): Any = node.id

            override fun traverseAncestors(
                node: FreshNode,
                relationExpression: li.gkd.selector.relation.RelationExpression,
            ): Sequence<TraversalCandidate<FreshNode>> {
                ancestorTraversalCount++
                return super.traverseAncestors(node, relationExpression)
            }
        }
        val source = "@Root > Leaf"
        val match = assertNotNull(
            Selector.parse(source).value.matchWithTrace(
                FreshNode(1),
                adapter,
            ),
        )

        assertEquals(0, match.target.id)
        assertEquals(1, ancestorTraversalCount)
        assertEquals(1, match.units.single().steps.single().source.id)
        assertEquals(0, match.units.single().steps.single().target.id)
        assertEquals(">", match.units.single().steps.single().formattedRelation)
    }

    @Test
    fun tupleAndPolynomialRangesSelectOnlyTheirDeclaredOffsets() {
        val root = TestNode(
            key = "root",
            name = "Root",
            children = listOf(
                TestNode("distance-7", "Candidate", mapOf("id" to "distance-7")),
                TestNode("distance-6", "Candidate", mapOf("id" to "distance-6")),
                TestNode("distance-5", "Other"),
                TestNode("distance-4", "Other"),
                TestNode("distance-3", "Other"),
                TestNode("distance-2", "Other"),
                TestNode("distance-1", "Other"),
                TestNode("current", "Current"),
            ),
        )
        val current = root.find("current")

        assertEquals(
            "distance-7",
            compileSelector("@Candidate[id='distance-7'] +(-2n+9) Current")
                .match(current, TestNodeAdapter)?.key,
        )
        assertNull(
            compileSelector("Candidate[id='distance-6'] +(-2n+9) Current")
                .match(current, TestNodeAdapter),
        )
        assertEquals(
            "distance-6",
            compileSelector("@Candidate[id='distance-6'] +(2,4,6) Current")
                .match(current, TestNodeAdapter)?.key,
        )
        assertNull(
            compileSelector("Candidate[id='distance-7'] +(2,4,6) Current")
                .match(current, TestNodeAdapter),
        )
    }

    @Test
    fun selectorBooleanOperatorsPreserveShortCircuitTargets() {
        val root = testTree()
        val confirm = root.find("confirm")

        val orSelector = compileSelector(
            "(@TextView[text='Title'] + Button[text='Confirm']) || (Button[text='Confirm'])",
        )
        assertEquals("title", orSelector.match(confirm, TestNodeAdapter)?.key)

        val andSelector = compileSelector(
            "(@TextView[text='Title'] + Button[text='Confirm']) && (Button[text='Confirm'])",
        )
        assertEquals("confirm", andSelector.match(confirm, TestNodeAdapter)?.key)

        assertSame(
            confirm,
            compileSelector("!(Button[text='Cancel'])")
                .match(confirm, TestNodeAdapter),
        )
        assertNull(
            compileSelector("!(Button[text='Confirm'])")
                .match(confirm, TestNodeAdapter),
        )
    }

    @Test
    fun traceKeepsOnlySuccessfulLogicalBranches() {
        val node = TestNode("button", "Button", mapOf("id" to "b"))
        val orSource = "(Button[id='x']) || (Button[id='b'])"
        val orTrace = assertNotNull(
            Selector.parse(orSource)
                .value
                .matchWithTrace(node, TestNodeAdapter),
        )
        val successfulRange = assertNotNull(orTrace.units.single().range)
        assertEquals("Button[id='b']", orSource.substring(successfulRange.start, successfulRange.end))

        val notTrace = assertNotNull(
            Selector.parse("!(Button[id='x'])")
                .value
                .matchWithTrace(node, TestNodeAdapter),
        )
        assertTrue(notTrace.units.isEmpty())

        val compiledTrace = assertNotNull(
            Selector.compile("Button[id='b']")
                .value
                .matchWithTrace(node, TestNodeAdapter),
        )
        assertNull(compiledTrace.units.single().range)
    }

    @Test
    fun rootMatchingAndFastQueryHaveBehavioralParity() {
        val root = testTree()
        assertSame(root, compileSelector("[parent=null]").match(root, TestNodeAdapter))
        assertNull(compileSelector("[parent=null]").match(root.find("header"), TestNodeAdapter))

        val content = root.find("content")
        val selector = compileSelector("TextView[text='Beta7'] <<n FrameLayout[vid='content']")
        val regular = selector.match(content, TestNodeAdapter, MatchOptions(fastQuery = false))
        val fast = selector.match(content, TestNodeAdapter, MatchOptions(fastQuery = true))
        assertSame(regular, fast)
        assertSame(content, fast)
    }

    @Test
    fun fastQueryDoesNotRestrictAnOrBranchWithoutFastQuery() {
        val button = TestNode("button", "Button")
        val view = TestNode("view", "View", mapOf("id" to "x"))
        val root = TestNode("root", "Root", children = listOf(button, view))
        val selector = compileSelector("(Button) || (View[id='x'])")

        assertTrue(selector.fastQueryList.isEmpty())
        assertSame(
            button,
            TestNodeAdapter.querySelector(root, selector, MatchOptions(fastQuery = false)),
        )
        assertSame(
            button,
            TestNodeAdapter.querySelector(root, selector, MatchOptions(fastQuery = true)),
        )
    }

    @Test
    fun fastQueryCombinationFollowsBooleanCandidateSemantics() {
        assertEquals(
            listOf(FastQuery.Id("b"), FastQuery.Id("x")),
            compileSelector("(Button[id='b']) || (View[id='x'])").fastQueryList,
        )
        assertTrue(
            compileSelector("(Button) || (View[id='x'])").fastQueryList.isEmpty(),
        )
        assertEquals(
            listOf(FastQuery.Id("x")),
            compileSelector("(Button) && (View[id='x'])").fastQueryList,
        )
        assertTrue(
            compileSelector("!(View[id='x'])").fastQueryList.isEmpty(),
        )
    }

    @Test
    fun defaultFastQueryHookPreservesQueryResults() {
        val button = TestNode("button", "Button", mapOf("id" to "b"))
        val view = TestNode("view", "View", mapOf("id" to "x"))
        val root = TestNode("root", "Root", children = listOf(button, view))
        val selector = compileSelector("(Button[id='b']) || (View[id='x'])")

        assertEquals(
            listOf(button, view),
            DefaultFastQueryNodeAdapter.querySelectorAll(
                root,
                selector,
                MatchOptions(fastQuery = true),
            ),
        )
    }

    @Test
    fun defaultFastQueryUsesCharSequenceContentSemantics() {
        val text = StringBuilder("Confirm")
        val button = TestNode("button", "Button", mapOf("text" to text))
        val root = TestNode("root", "Root", children = listOf(button))
        val selector = compileSelector("Button[text='Confirm']")

        assertSame(
            button,
            DefaultFastQueryNodeAdapter.querySelector(
                root,
                selector,
                MatchOptions(fastQuery = false),
            ),
        )
        assertSame(
            button,
            DefaultFastQueryNodeAdapter.querySelector(
                root,
                selector,
                MatchOptions(fastQuery = true),
            ),
        )
    }

    @Test
    fun querySelectorStopsLazyFastQueryAfterFirstMatch() {
        val first = TestNode("first", "Button", mapOf("id" to "first"))
        val second = TestNode("second", "Button", mapOf("id" to "first"))
        val root = TestNode("root", "Root", children = listOf(first, second))
        LazyFastQueryNodeAdapter.yieldedCount = 0

        assertSame(
            first,
            LazyFastQueryNodeAdapter.querySelector(
                root,
                compileSelector("Button[id='first']"),
                MatchOptions(fastQuery = true),
            ),
        )
        assertEquals(1, LazyFastQueryNodeAdapter.yieldedCount)
    }

    @Test
    fun fastQueryMayChangeOrderAndStopsBeforeLaterQueries() {
        val depthFirst = TestNode("depth-first", "View", mapOf("id" to "x"))
        val queryFirst = TestNode("query-first", "Button", mapOf("id" to "b"))
        val root = TestNode("root", "Root", children = listOf(depthFirst, queryFirst))
        val selector = compileSelector("(Button[id='b']) || (View[id='x'])")

        assertSame(
            depthFirst,
            QueryOrderedFastQueryNodeAdapter.querySelector(
                root,
                selector,
                MatchOptions(fastQuery = false),
            ),
        )

        QueryOrderedFastQueryNodeAdapter.reset()
        assertSame(
            queryFirst,
            QueryOrderedFastQueryNodeAdapter.querySelector(
                root,
                selector,
                MatchOptions(fastQuery = true),
            ),
        )
        assertEquals(1, QueryOrderedFastQueryNodeAdapter.queryCount)

        QueryOrderedFastQueryNodeAdapter.reset()
        assertEquals(
            listOf(queryFirst, depthFirst),
            QueryOrderedFastQueryNodeAdapter.querySelectorAll(
                root,
                selector,
                MatchOptions(fastQuery = true),
            ),
        )
        assertEquals(2, QueryOrderedFastQueryNodeAdapter.queryCount)
    }

    @Test
    fun fastQueryAndTraceChooseTheSameInternalDescendant() {
        val first = TestNode("first", "Button", mapOf("id" to "x"))
        val second = TestNode("second", "Button", mapOf("id" to "x"))
        val root = TestNode("root", "Root", children = listOf(first, second))
        val selector = Selector.parse("@Button[id='x'] <<n Root").value
        val options = MatchOptions(fastQuery = true)

        assertSame(second, selector.match(root, ReverseFastQueryNodeAdapter, options))
        val trace = assertNotNull(
            selector.matchWithTrace(root, ReverseFastQueryNodeAdapter, options),
        )
        assertSame(second, trace.target)
        assertEquals("<<2", trace.units.single().steps.single().formattedRelation)
    }

    @Test
    fun fastQueryResultsAreDeduplicatedAcrossQueries() {
        val shared = TestNode(
            key = "shared",
            name = "Button",
            attributes = mapOf("id" to "shared", "text" to "shared"),
        )
        val root = TestNode("root", "Root", children = listOf(shared))
        val selector = compileSelector("(Button[id='shared']) || (Button[text='shared'])")

        QueryOrderedFastQueryNodeAdapter.reset()
        assertEquals(
            listOf(shared),
            QueryOrderedFastQueryNodeAdapter.querySelectorAll(
                root,
                selector,
                MatchOptions(fastQuery = true),
            ),
        )
        assertEquals(2, QueryOrderedFastQueryNodeAdapter.queryCount)
    }

    @Test
    fun queryAllHelpersDeduplicateLogicalTargets() {
        val first = TestNode("first", "Button")
        val second = TestNode("second", "Button")
        val root = TestNode("root", "Root", children = listOf(first, second))
        val selector = Selector.parse("@Root >n Button").value

        assertEquals(listOf(root), TestNodeAdapter.querySelectorAll(root, selector))
        assertEquals(
            listOf(root),
            TestNodeAdapter.querySelectorAllWithTrace(root, selector).map { it.target },
        )
    }

    @Test
    fun selectorSlowFlagUsesTheTopLevelCandidatePlan() {
        val selector = compileSelector("(Button) && (Button[id='x'])")

        assertFalse(selector.isSlow(MatchOptions(fastQuery = true)))
        assertTrue(selector.isSlow(MatchOptions(fastQuery = false)))
    }

    @Test
    fun fastQueryNeverTreatsTheSourceNodeAsItsOwnDescendant() {
        val leaf = TestNode("leaf", "Button", mapOf("text" to "Confirm"))
        val singleSelector = compileSelector("Button[text='Confirm']")
        val descendantSelector = compileSelector(
            "@Button[text='Confirm'] <<n Button[text='Confirm']",
        )

        assertNull(
            InclusiveFastQueryNodeAdapter.querySelector(
                leaf,
                singleSelector,
                MatchOptions(fastQuery = true),
            ),
        )
        assertNull(
            descendantSelector.match(
                leaf,
                InclusiveFastQueryNodeAdapter,
                MatchOptions(fastQuery = true),
            ),
        )

        val child = TestNode("child", "Button", mapOf("text" to "Confirm"))
        val root = TestNode(
            "root",
            "Button",
            mapOf("text" to "Confirm"),
            children = listOf(child),
        )
        assertSame(
            child,
            InclusiveFastQueryNodeAdapter.querySelector(
                root,
                singleSelector,
                MatchOptions(fastQuery = true),
            ),
        )
        assertSame(
            child,
            descendantSelector.match(
                root,
                InclusiveFastQueryNodeAdapter,
                MatchOptions(fastQuery = true),
            ),
        )
    }

    @Test
    fun cyclicAncestorTraversalStopsAtRepeatedNodeKey() {
        val adapter = GraphNodeAdapter(
            names = mapOf(0 to "View", 1 to "A", 2 to "B"),
            parentIds = mapOf(0 to 1, 1 to 2, 2 to 1),
        )

        assertNull(
            compileSelector("Never >n View")
                .match(GraphNode(0), adapter),
        )
        assertEquals(3, adapter.parentReadCount)
    }

    @Test
    fun cyclicDescendantTraversalVisitsEachNodeKeyOnce() {
        val adapter = GraphNodeAdapter(
            names = mapOf(0 to "Root", 1 to "View"),
            childIds = mapOf(0 to listOf(1), 1 to listOf(0)),
        )

        assertTrue(
            adapter.querySelectorAll(
                GraphNode(0),
                compileSelector("Never"),
            ).isEmpty(),
        )
        assertEquals(2, adapter.childReadCount)
    }

    @Test
    fun rootConstraintUsesRootLookupWithoutChangingTraceOffsets() {
        val adapter = RootLookupNodeAdapter()
        val selector = compileSelector("@[parent=null] >n View")

        assertEquals(0, selector.match(GraphNode(2), adapter)?.id)
        assertEquals(1, adapter.rootReadCount)
        assertEquals(0, adapter.ancestorTraversalCount)

        val trace = assertNotNull(selector.matchWithTrace(GraphNode(2), adapter))
        assertEquals(0, trace.target.id)
        assertEquals(1, trace.units.single().steps.single().offset)
        assertEquals(2, adapter.rootReadCount)
        assertEquals(1, adapter.ancestorTraversalCount)
    }

    @Test
    fun tracePreservesOffsetsWhenAnEarlierAllowedChildIsMissing() {
        class SparseNode(val key: String, val name: String)

        val root = SparseNode("root", "Root")
        val target = SparseNode("target", "Target")
        val adapter = object : NodeAdapter<SparseNode>() {
            override fun getAttr(target: Any, name: String): Any? = null

            override fun getName(node: SparseNode): String = node.name

            override fun getChildCount(node: SparseNode): Int = if (node === root) 4 else 0

            override fun getChild(node: SparseNode, index: Int): SparseNode? =
                target.takeIf { node === root && index == 3 }

            override fun getParent(node: SparseNode): SparseNode? = root.takeIf { node === target }

            override fun getNodeKey(node: SparseNode): Any = node.key
        }
        val trace = assertNotNull(
            Selector.parse("@Target <(2,4) Root").value.matchWithTrace(root, adapter),
        )

        assertSame(target, trace.target)
        assertEquals(3, trace.units.single().steps.single().offset)
        assertEquals("<4", trace.units.single().steps.single().formattedRelation)
    }

    @Test
    fun currentNodePropertiesDoNotDisableFailedStateCaching() {
        var root = TestNode("leaf", "N")
        val leaf = root
        repeat(23) { depth ->
            root = TestNode("node-$depth", "N", children = listOf(root))
        }
        val selector = compileSelector("Never[missing=null]" + " >n N".repeat(11))

        TestNodeAdapter.resetCounters()
        assertNull(selector.match(leaf, TestNodeAdapter))
        assertTrue(
            TestNodeAdapter.parentCallCount < 10_000,
            "Expected cached search, parent calls=${TestNodeAdapter.parentCallCount}",
        )
    }

    @Test
    fun queryHelpersReturnDepthFirstResults() {
        val root = testTree()
        val selector = compileSelector("TextView")

        assertEquals(
            listOf("title", "alpha", "beta"),
            TestNodeAdapter.querySelectorAll(root, selector).map { it.key }.toList(),
        )
        assertEquals("title", TestNodeAdapter.querySelector(root, selector)?.key)
    }

    @Test
    fun deepExpressionsAndPathsMatchWithoutJvmStackRecursion() {
        val node = TestNode("node", "View", mapOf("x" to 1))
        val propertySource = "View[" + List(5_000) { "x=1" }.joinToString("&&") + "]"
        val propertySelector = compileSelector(propertySource)
        assertSame(
            node,
            propertySelector.match(node, TestNodeAdapter),
        )
        assertEquals(propertySelector.toString(), compileSelector(propertySelector.toString()).toString())

        val selectorSource = List(5_000) { "(View[x=1])" }.joinToString(" || ")
        val logicalSelector = compileSelector(selectorSource)
        assertSame(
            node,
            logicalSelector.match(node, TestNodeAdapter),
        )
        assertEquals(logicalSelector.toString(), compileSelector(logicalSelector.toString()).toString())
        assertEquals(
            1,
            assertNotNull(
                logicalSelector.matchWithTrace(node, TestNodeAdapter),
            ).units.size,
        )

        var deepest = TestNode("path-0", "N")
        repeat(1_999) { index ->
            deepest = TestNode("path-${index + 1}", "N", children = listOf(deepest))
        }
        val leaf = deepest.find("path-0")
        val pathSelector = compileSelector(List(2_000) { "N" }.joinToString(" "))
        assertSame(leaf, pathSelector.match(leaf, TestNodeAdapter))
        assertTrue(pathSelector.isSlow(MatchOptions.default))
        assertEquals(
            1_999,
            assertNotNull(
                pathSelector.matchWithTrace(leaf, TestNodeAdapter),
            ).units.single().steps.size,
        )

        val nestedCallSource = "View[x=" + "identity(".repeat(3_000) +
                "1" + ")".repeat(3_000) + "]"
        val nestedCallSelector = compileSelector(nestedCallSource)
        assertSame(node, nestedCallSelector.match(node, TestNodeAdapter))
        assertEquals(
            nestedCallSelector.toString(),
            compileSelector(nestedCallSelector.toString()).toString(),
        )
    }

    private data class MatchCase(
        val source: String,
        val start: String,
        val expected: String,
    )

    @Test
    fun transformRuntimeFailuresAreNotWrappedAsSelectorErrors() {
        val runtimeError = IllegalStateException("adapter unavailable")
        val node = TestNode("node", "View")
        val adapter = object : NodeAdapter<TestNode>() {
            override fun getAttr(target: Any, name: String): Any = throw runtimeError

            override fun getName(node: TestNode): String = node.name

            override fun getChildCount(node: TestNode): Int = node.children.size

            override fun getChild(node: TestNode, index: Int): TestNode? =
                node.children.getOrNull(index)

            override fun getParent(node: TestNode): TestNode? = node.parent

            override fun getNodeKey(node: TestNode): Any = node
        }

        assertSame(
            runtimeError,
            assertFails {
                compileSelector("View[x=1]").match(node, adapter)
            },
        )
    }
}

private abstract class DelegatingTestNodeAdapter : NodeAdapter<TestNode>() {
    override fun getAttr(target: Any, name: String): Any? = TestNodeAdapter.getAttr(target, name)

    override fun getInvoke(target: Any, name: String, args: List<Any>): Any? =
        TestNodeAdapter.getInvoke(target, name, args)

    override fun getName(node: TestNode): String = TestNodeAdapter.getName(node)

    override fun getChildCount(node: TestNode): Int = TestNodeAdapter.getChildCount(node)

    override fun getChild(node: TestNode, index: Int): TestNode? =
        TestNodeAdapter.getChild(node, index)

    override fun getParent(node: TestNode): TestNode? = TestNodeAdapter.getParent(node)

    override fun getNodeKey(node: TestNode): Any = TestNodeAdapter.getNodeKey(node)
}

private object DefaultFastQueryNodeAdapter : DelegatingTestNodeAdapter()

private object LazyFastQueryNodeAdapter : DelegatingTestNodeAdapter() {
    var yieldedCount = 0

    override fun getFastQueryDescendants(
        node: TestNode,
        fastQueryList: List<FastQuery>,
    ): Sequence<TestNode> = sequence {
        for (candidate in getDescendants(node)) {
            yieldedCount++
            yield(candidate)
        }
    }
}

private object QueryOrderedFastQueryNodeAdapter : DelegatingTestNodeAdapter() {
    var queryCount = 0
        private set

    fun reset() {
        queryCount = 0
    }

    override fun getFastQueryDescendants(
        node: TestNode,
        fastQueryList: List<FastQuery>,
    ): Sequence<TestNode> = sequence {
        val yieldedKeys = mutableSetOf<Any>()
        for (fastQuery in fastQueryList) {
            queryCount++
            for (candidate in getDescendants(node)) {
                if (
                    fastQuery.acceptValue(candidate.attributes[fastQuery.attributeName]) &&
                    yieldedKeys.add(getNodeKey(candidate))
                ) {
                    yield(candidate)
                }
            }
        }
    }
}

private object ReverseFastQueryNodeAdapter : DelegatingTestNodeAdapter() {
    override fun getFastQueryDescendants(
        node: TestNode,
        fastQueryList: List<FastQuery>,
    ): Sequence<TestNode> = getDescendants(node).toList().asReversed().asSequence()
}

private object InclusiveFastQueryNodeAdapter : DelegatingTestNodeAdapter() {
    override fun getFastQueryDescendants(
        node: TestNode,
        fastQueryList: List<FastQuery>,
    ): Sequence<TestNode> = sequence {
        yield(node)
        yieldAll(TestNodeAdapter.getFastQueryDescendants(node, fastQueryList))
    }
}

private class GraphNode(val id: Int)

private class GraphNodeAdapter(
    private val names: Map<Int, String>,
    private val parentIds: Map<Int, Int> = emptyMap(),
    private val childIds: Map<Int, List<Int>> = emptyMap(),
) : NodeAdapter<GraphNode>() {
    var parentReadCount = 0
        private set

    var childReadCount = 0
        private set

    override fun getAttr(target: Any, name: String): Any? = when (name) {
        "parent" -> getParent(target as GraphNode)
        else -> null
    }

    override fun getName(node: GraphNode): String = names.getValue(node.id)

    override fun getChildCount(node: GraphNode): Int = childIds[node.id].orEmpty().size

    override fun getChild(node: GraphNode, index: Int): GraphNode? {
        check(++childReadCount <= 32) { "Cyclic descendant traversal did not terminate" }
        return childIds[node.id]?.getOrNull(index)?.let(::GraphNode)
    }

    override fun getParent(node: GraphNode): GraphNode? {
        check(++parentReadCount <= 32) { "Cyclic ancestor traversal did not terminate" }
        return parentIds[node.id]?.let(::GraphNode)
    }

    override fun getNodeKey(node: GraphNode): Any = node.id
}

private class RootLookupNodeAdapter : NodeAdapter<GraphNode>() {
    var rootReadCount = 0
        private set

    var ancestorTraversalCount = 0
        private set

    override fun getAttr(target: Any, name: String): Any? = when (name) {
        "parent" -> getParent(target as GraphNode)
        else -> null
    }

    override fun getName(node: GraphNode): String = when (node.id) {
        2 -> "View"
        else -> "Node"
    }

    override fun getChildCount(node: GraphNode): Int = 0

    override fun getChild(node: GraphNode, index: Int): GraphNode? = null

    override fun getParent(node: GraphNode): GraphNode? = when (node.id) {
        2 -> GraphNode(1)
        1 -> GraphNode(0)
        else -> null
    }

    override fun getNodeKey(node: GraphNode): Any = node.id

    override fun getRoot(node: GraphNode): GraphNode {
        rootReadCount++
        return GraphNode(0)
    }

    override fun traverseAncestors(
        node: GraphNode,
        relationExpression: li.gkd.selector.relation.RelationExpression,
    ): Sequence<TraversalCandidate<GraphNode>> {
        ancestorTraversalCount++
        return super.traverseAncestors(node, relationExpression)
    }
}
