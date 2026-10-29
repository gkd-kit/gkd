package li.gkd.app.matching.diagnostics

import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import li.gkd.app.development.matching.SimulatedNodeAdapter
import li.gkd.app.development.matching.SimulatedRuleNodes
import li.gkd.app.development.matching.prepareDebugRule
import li.gkd.app.matching.MatchWork
import li.gkd.app.matching.input.MatchTimingPolicy
import li.gkd.app.matching.query.MatchNodeReadUnavailable
import li.gkd.app.matching.query.MatchNodeReader
import li.gkd.app.matching.query.matchRuleSelectorsUsing
import li.gkd.app.matching.query.querySingleSelector
import li.gkd.app.model.AppInfo
import li.gkd.selector.MatchOptions
import li.gkd.selector.NodeAdapter
import li.gkd.selector.Selector
import li.gkd.selector.SelectorEvaluation
import li.gkd.selector.SelectorObserver

class MatchQueryRecordingTest {
    @Test
    fun timedOutPhysicalReadStillUpdatesOldQueryAfterSessionReplacement() = runBlocking {
        val diagnostics = MatchDiagnostics()
        val session =
            diagnostics.start(MatchTimingPolicy(), ruleFilter = "manual", recordQueries = true)
        val reader = MatchNodeReader(diagnostics)
        val release = java.util.concurrent.CountDownLatch(1)
        val adapter =
            object : NodeAdapter<String>() {
                override fun getAttr(target: Any, name: String): Any? =
                    reader.read(timeoutMillis = 100, name = "Text", runId = 1) {
                        release.await()
                        "target"
                    }

                override fun getName(node: String) = node

                override fun getNodeKey(node: String): Any = node

                override fun getChildCount(node: String) = 0

                override fun getChild(node: String, index: Int): String? = null

                override fun getParent(node: String): String? = null
            }
        try {
            assertFailsWith<MatchNodeReadUnavailable> {
                recordManualQuery(
                    Selector.compile("[text='target']").value,
                    MatchOptions(),
                    "node",
                    { "node" },
                    adapter,
                    diagnostics,
                    session,
                    1,
                )
            }
            diagnostics.stop(session.id)
            val stopped = diagnostics.export(session.id)
            assertEquals("cancelled", stopped.queryRecordings.single().outcome)
            assertTrue(stopped.session.openOperations.any { it.name == "NodeRead:Text" })
            val next = diagnostics.start(MatchTimingPolicy())
            release.countDown()
            kotlinx.coroutines.withTimeout(5000) {
                while (diagnostics.export(session.id).session.openOperations.isNotEmpty()) kotlinx
                    .coroutines
                    .delay(10)
            }
            val finished = diagnostics.export(session.id)
            val physical =
                finished.queryRecordings.single().operations.single { it.name == "NodeRead:Text" }
            assertEquals("returned", physical.outcome)
            assertNotNull(physical.durationNanos)
            assertEquals(
                stopped.queryRecordings.single().durationNanos,
                finished.queryRecordings.single().durationNanos,
            )
            assertTrue(
                finished.events.any {
                    it.reason == "operation_finished_after_stop" && it.operation?.id == physical.id
                },
            )
            assertTrue(diagnostics.export(next.id).queryRecordings.isEmpty())
        } finally {
            release.countDown()
            reader.close()
        }
    }

    @Test
    fun cancellationKeepsIndependentQueryTerminalWithoutAFalseMatch() {
        val diagnostics = MatchDiagnostics()
        val session =
            diagnostics.start(MatchTimingPolicy(), ruleFilter = "manual", recordQueries = true)
        val error = object : CancellationException("cancelled while reading") {}
        val adapter =
            object : NodeAdapter<String>() {
                override fun getAttr(target: Any, name: String): Any? = throw error

                override fun getName(node: String) = node

                override fun getNodeKey(node: String): Any = node

                override fun getChildCount(node: String) = 0

                override fun getChild(node: String, index: Int): String? = null

                override fun getParent(node: String): String? = null
            }
        assertSame(
            error,
            assertFailsWith<CancellationException> {
                recordManualQuery(
                    Selector.compile("[text='target']").value,
                    MatchOptions(),
                    "node",
                    { "node" },
                    adapter,
                    diagnostics,
                    session,
                    1,
                )
            },
        )
        repeat(2100) { diagnostics.record(0, MatchStage.Idle, "noise") }
        val recording = diagnostics.export(session.id).queryRecordings.single()
        assertTrue(recording.completed)
        assertEquals("cancelled", recording.outcome)
        assertEquals(error.javaClass.name, recording.failure)
        assertNotNull(recording.durationNanos)
        assertEquals(MatchRecordedValueType.Null, recording.result?.type)
    }

    @Test
    fun blockedQueryExportsCompletedReadsAndFinishesIntoOldSessionAfterReplacement() = runBlocking {
        val saved = java.util.Collections.synchronizedList(mutableListOf<MatchDiagnosticCapture>())
        val diagnostics = MatchDiagnostics(onStoppedCapture = { saved.add(it) })
        val session =
            diagnostics.start(MatchTimingPolicy(), ruleFilter = "manual", recordQueries = true)
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val node = Any()
        val reader = MatchNodeReader(diagnostics)
        val adapter =
            object : NodeAdapter<Any>() {
                override fun getAttr(target: Any, name: String): Any? =
                    when (name) {
                        "text" -> "ready"
                        "id" -> {
                            entered.countDown()
                            release.await()
                            "target"
                        }
                        "enabled" ->
                            reader.read(name = "Enabled", runId = 7, rule = "manual") { true }
                        else -> null
                    }

                override fun getName(node: Any) = "Node"

                override fun getNodeKey(node: Any) = node

                override fun getChildCount(node: Any) = 0

                override fun getChild(node: Any, index: Int): Any? = null

                override fun getParent(node: Any): Any? = null
            }
        val worker =
            async(Dispatchers.Default) {
                recordManualQuery(
                    Selector.compile("[text='ready'][id='target'][enabled=true]").value,
                    MatchOptions(),
                    node,
                    { node },
                    adapter,
                    diagnostics,
                    session,
                    7,
                )
            }
        try {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(saved.isEmpty(), "Active sampling must not write files")
            diagnostics.stop(session.id)
            val stopped = diagnostics.export(session.id)
            val partial = stopped.queryRecordings.single()
            assertFalse(partial.completed)
            assertNotNull(partial.startedAt)
            assertNull(partial.durationNanos)
            assertNull(partial.endedAt)
            assertNull(partial.result)
            assertEquals(
                "ready",
                partial.calls.single { it.operation == MatchNodeOperation.Attr }.result!!.value,
            )
            assertFalse(stopped.session.trackingComplete)
            val next = diagnostics.start(MatchTimingPolicy())
            release.countDown()
            assertSame(node, worker.await())
            val finished = diagnostics.export(session.id)
            assertTrue(finished.session.trackingComplete)
            assertTrue(finished.queryRecordings.single().completed)
            assertNotNull(finished.queryRecordings.single().durationNanos)
            assertEquals("matched", finished.queryRecordings.single().outcome)
            assertEquals(
                3,
                finished.queryRecordings.single().calls.count {
                    it.operation == MatchNodeOperation.Attr
                },
            )
            assertNotNull(finished.queryRecordings.single().result)
            assertFalse(partial.completed, "Previously exported partial snapshots stay immutable")
            assertEquals(
                "ready",
                partial.calls.single { it.operation == MatchNodeOperation.Attr }.result!!.value,
            )
            assertTrue(saved.all { it.session.id == session.id })
            assertEquals(finished, saved.last())
            assertTrue(diagnostics.export(next.id).events.isEmpty())
            assertTrue(diagnostics.export(next.id).queryRecordings.isEmpty())
        } finally {
            release.countDown()
            worker.join()
            reader.close()
        }
    }

    @Test
    fun selectorCallLinksPhysicalReadArgumentsAndRestoresContextAfterFailure() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy())
        val query = diagnostics.beginOperation("SelectorQuery", 42, "rule")!!
        val reader = MatchNodeReader(diagnostics)
        val node = Any()
        val failure = IllegalStateException("platform failure")
        val delegate =
            object : NodeAdapter<Any>() {
                override fun getAttr(target: Any, name: String): Any? = error("unused")

                override fun getName(node: Any) = "Node"

                override fun getNodeKey(node: Any): Any = node

                override fun getChildCount(node: Any) = 0

                override fun getParent(node: Any): Any? = null

                override fun getChild(node: Any, index: Int): Any? {
                    val observation = assertNotNull(MatchNodeObservation.current())
                    return reader.read(
                        name = "Child",
                        runId = 42,
                        rule = "rule",
                        arguments = listOf(observation.value(node), observation.value(index)),
                    ) {
                        assertNull(
                            MatchNodeObservation.current(),
                            "Worker must not retain selector objects",
                        )
                        throw failure
                    }
                }
            }
        try {
            val recording = RecordingNodeAdapter(delegate, diagnostics, query)
            assertSame(
                failure,
                assertFailsWith<IllegalStateException> { recording.getChild(node, 3) },
            )
            assertNull(MatchNodeObservation.current())
            val operations =
                diagnostics
                    .export(session.id)
                    .events
                    .mapNotNull { it.operation }
                    .filter { it.durationNanos != null }
            val selector = operations.single { it.name == "Selector.child" }
            val wait = operations.single { it.name == "ChildWait" }
            val physical = operations.single { it.name == "NodeRead:Child" }
            assertEquals(query.id, selector.parentOperationId)
            assertEquals(selector.id, wait.parentOperationId)
            assertEquals(wait.id, physical.parentOperationId)
            assertEquals(selector.arguments, wait.arguments)
            assertEquals(wait.arguments, physical.arguments)
            assertEquals(MatchRecordedValueType.Reference, physical.arguments.first().type)
            assertEquals("3", physical.arguments.last().value)
            assertEquals(failure.toString(), physical.outcome)
        } finally {
            reader.close()
        }
    }

    @Test
    fun nodeCallsRetainDurationArgumentsFailureAndOpenOperationLink() {
        var clock = 0L
        val diagnostics = MatchDiagnostics(nanoTime = { clock }, wallTime = { 0 })
        val session =
            diagnostics.start(MatchTimingPolicy(), recordQueries = true, ruleFilter = "rule")
        val query = diagnostics.beginOperation("SelectorQuery", 42, "rule")!!
        val failure = IllegalStateException("node unavailable")
        val adapter =
            object : NodeAdapter<String>() {
                override fun getAttr(target: Any, name: String): Any? {
                    val open = diagnostics.state.value!!.openOperations.last()
                    assertEquals(query.id, open.parentOperationId)
                    assertEquals(42L, open.runId)
                    assertEquals("text", open.arguments.last().value)
                    clock += 7_000_000
                    if (target == "failed") throw failure
                    return "target"
                }

                override fun getName(node: String) = node

                override fun getNodeKey(node: String): Any = node

                override fun getChildCount(node: String) = 0

                override fun getChild(node: String, index: Int): String? = null

                override fun getParent(node: String): String? = null
            }
        val recording = RecordingNodeAdapter(adapter, diagnostics, query, nanoTime = { clock })
        recording.begin(
            42,
            "rule",
            MatchSelectorPlan(listOf("[text='target']")),
            recording.value("node"),
        )
        assertEquals("target", recording.getAttr("node", "text"))
        assertSame(
            failure,
            assertFailsWith<IllegalStateException> { recording.getAttr("failed", "text") },
        )
        recording.finish(null, failure.javaClass.name)
        val data = recording.evidence.snapshot()
        assertEquals(listOf(0L, 7_000_000L), data.calls.map { it.startedNanos })
        assertEquals(listOf(7_000_000L, 7_000_000L), data.calls.map { it.durationNanos })
        assertEquals("node unavailable", data.calls.last().failureMessage)
        assertEquals(query.id, data.operationId)
        assertEquals(14_000_000L, data.durationNanos)
        assertEquals("failed", data.outcome)
        assertNotNull(data.startedAt)
        assertNotNull(data.endedAt)
        val traced = diagnostics.export(session.id).events.mapNotNull { it.operation }
        assertFalse(
            traced.any { it.id == data.calls.first().operationId },
            "Successful selector access is stored only in calls",
        )
        assertTrue(
            traced.any { it.id == data.calls.last().operationId },
            "Failure remains visible in events",
        )
        diagnostics.registerQuery(session.id, recording.evidence)
        repeat(2100) { diagnostics.record(42, MatchStage.Idle, "noise") }
        val retained = diagnostics.export(session.id).queryRecordings.single()
        assertEquals(data, retained, "Query timing and result survive trace ring overwrite")
        assertEquals(data, Json.decodeFromString<MatchQueryRecording>(Json.encodeToString(data)))
    }

    private fun <N : Any> matchSelectorPlan(
        plan: MatchSelectorPlan,
        node: N,
        root: () -> N?,
        query: (N, Selector) -> N?,
    ): N? =
        matchRuleSelectorsUsing(
            plan.matchRoot,
            plan.anyMatches.map { Selector.compile(it).value },
            plan.matches.map { Selector.compile(it).value },
            plan.excludeMatches.map { Selector.compile(it).value },
            plan.excludeAllMatches.map { Selector.compile(it).value },
            node,
            root,
            { target, selector, _, _ -> query(target, selector) },
            null,
        )

    private class Node(
        val id: Int,
        val name: String,
        val text: String?,
        val children: List<Node> = emptyList(),
    )

    private inner class Fixture {
        val a = Node(2, "Button", "target")
        val b = Node(3, "Label", "other")
        val root = Node(1, "Root", null, listOf(a, b))
        val reads = mutableListOf<String>()
        var failText = false
        val adapter =
            object : NodeAdapter<Node>() {
                override fun getAttr(target: Any, name: String): Any? {
                    val node = target as Node
                    reads.add("${node.id}:$name")
                    if (failText && name == "text") throw CancellationException("cancelled")
                    return when (name) {
                        "text" -> node.text
                        "parent" -> if (node === root) null else root
                        "index" -> if (node === a) 0 else 1
                        else -> null
                    }
                }

                override fun getInvoke(target: Any, name: String, args: List<Any>): Any? {
                    reads.add("invoke:$name:$args")
                    return if (name == "getChild")
                        (target as Node).children.getOrNull(args[0] as Int)
                    else null
                }

                override fun getName(node: Node) = node.name.also { reads.add("name:${node.id}") }

                override fun getChildCount(node: Node) =
                    node.children.size.also { reads.add("count:${node.id}") }

                override fun getChild(node: Node, index: Int) =
                    node.children.getOrNull(index).also { reads.add("child:${node.id}:$index") }

                override fun getParent(node: Node) =
                    (if (node === root) null else root).also { reads.add("parent:${node.id}") }

                override fun getNodeKey(node: Node): Any =
                    node.id.also { reads.add("key:${node.id}") }
            }

        fun evaluate(
            plan: MatchSelectorPlan,
            nodes: NodeAdapter<Node>,
            rootRead: () -> Node? = { root },
        ): Node? =
            matchSelectorPlan(plan, root, rootRead) { node, selector ->
                val options = MatchOptions(plan.fastQuery)
                if (plan.rootOnlySelectors && selector.isMatchRoot)
                    rootRead()?.let { selector.match(it, nodes, options) }
                else
                    selector.match(node, nodes, options)
                        ?: nodes.querySelector(node, selector, options)
            }

        fun record(plan: MatchSelectorPlan): Pair<Node?, MatchQueryRecording> {
            val recorder = RecordingNodeAdapter(adapter)
            val source = recorder.value(root)
            recorder.begin(1, "rule", plan, source)
            var target: Node? = null
            var failure: String? = null
            try {
                target = evaluate(plan, recorder) { recorder.root { root } }
            } catch (error: Exception) {
                failure = error.javaClass.name
            }
            recorder.finish(target, failure)
            return target to recorder.evidence.snapshot()
        }
    }

    @Test
    fun actualReadsAndResultsStayIdenticalAcrossLazyFastRelationAndLogicalQueries() {
        val selectors =
            listOf(
                "[text='target']",
                "[text='missing' && index=0]",
                "Button",
                "[parent.name='Root']",
                "[getChild(0).text='target']",
                "Button < Root",
                "Root > Button",
                "Button + Label",
                "Label - Button",
                "@Button < Root",
                "([text='target']) || ([text='other'])",
                "[text!='target']",
                "[text~= 'tar.*']",
            )
        for (selector in selectors) for (fast in listOf(false, true)) {
            val fixture = Fixture()
            val plan = MatchSelectorPlan(listOf(selector), fastQuery = fast)
            val expected = fixture.evaluate(plan, fixture.adapter)
            val reads = fixture.reads.toList()
            fixture.reads.clear()
            val (actual, recording) = fixture.record(plan)
            assertNull(recording.failure, selector)
            assertSame(expected, actual, selector)
            assertEquals(
                reads,
                fixture.reads,
                "Recording must not read ahead or query again: $selector / $fast",
            )
            val copy = Json.decodeFromString<MatchQueryRecording>(Json.encodeToString(recording))
            assertEquals(recording, copy, selector)
            assertEquals(reads, fixture.reads, "Export must not access live nodes")
        }
    }

    @Test
    fun allSelectorGroupsAndRootProviderUseTheProductionCombination() {
        val plans =
            listOf(
                MatchSelectorPlan(
                    listOf("[text='target']"),
                    matchRoot = true,
                    rootOnlySelectors = true,
                ),
                MatchSelectorPlan(
                    emptyList(),
                    anyMatches = listOf("[text='missing']", "[text='target']"),
                ),
                MatchSelectorPlan(listOf("Button"), excludeMatches = listOf("Label")),
                MatchSelectorPlan(
                    listOf("Button"),
                    excludeAllMatches = listOf("[text='missing']", "Label"),
                ),
                MatchSelectorPlan(listOf("[parent=null]"), rootOnlySelectors = true),
            )
        plans.forEach { plan ->
            val fixture = Fixture()
            val (target, capture) = fixture.record(plan)
            assertNull(capture.failure)
            assertEquals(target != null, (capture.result?.type != MatchRecordedValueType.Null))
        }
    }

    @Test
    fun rootOnlySelectorsKeepAndroidRootReadsSeparateFromSimulatedSubtreeQueries() {
        val fixture = Fixture()
        val selector = Selector.compile("[parent=null]").value
        var rootReads = 0
        val root = {
            rootReads++
            fixture.root
        }
        assertSame(
            fixture.root,
            querySingleSelector(
                fixture.a,
                selector,
                root,
                fixture.adapter,
                MatchOptions.default,
                rootOnlySelectors = true,
            ),
        )
        assertEquals(1, rootReads)
        assertFalse(fixture.reads.any { it.startsWith("count:") || it.startsWith("child:") })
        fixture.reads.clear()
        assertNull(
            querySingleSelector(
                fixture.a,
                selector,
                root,
                fixture.adapter,
                MatchOptions.default,
                rootOnlySelectors = false,
            ),
        )
        assertEquals(1, rootReads, "Subtree queries do not request the Android root")
        fixture.reads.clear()
        assertNull(
            querySingleSelector(
                fixture.a,
                selector,
                { null },
                fixture.adapter,
                MatchOptions.default,
                rootOnlySelectors = true,
            ),
        )
        assertTrue(fixture.reads.isEmpty(), "A missing root never falls back to the source subtree")
        val rootMiss = Selector.compile("[parent=null][text='target']").value
        assertNull(
            querySingleSelector(
                fixture.a,
                rootMiss,
                root,
                fixture.adapter,
                MatchOptions.default,
                rootOnlySelectors = true,
            ),
        )
        assertFalse(
            fixture.reads.any { it.startsWith("count:") || it.startsWith("child:") },
            "A root-only miss must not traverse descendants",
        )
    }

    @Test
    fun ordinaryAndExplainedSingleQueriesKeepSelfFirstAndTheSameLazyReadOrder() {
        val fixture = Fixture()
        val selector = Selector.compile("[text='target']").value
        val observer =
            object : SelectorObserver<Node> {
                override val enabled = true
                var conditions = 0

                override fun record(event: SelectorEvaluation<Node>) {
                    conditions++
                }
            }
        for (source in listOf(fixture.a, fixture.root)) {
            fixture.reads.clear()
            val root = { error("A non-root selector must not read the root provider") }
            assertSame(
                fixture.a,
                querySingleSelector(
                    source,
                    selector,
                    root,
                    fixture.adapter,
                    MatchOptions.default,
                    rootOnlySelectors = true,
                ),
            )
            val reads = fixture.reads.toList()
            if (source === fixture.a)
                assertFalse(reads.any { it.startsWith("count:") || it.startsWith("child:") })
            assertFalse(
                "3:text" in reads,
                "The first hit must short-circuit later descendant evaluations",
            )
            fixture.reads.clear()
            assertSame(
                fixture.a,
                querySingleSelector(
                    source,
                    selector,
                    root,
                    fixture.adapter,
                    MatchOptions.default,
                    rootOnlySelectors = true,
                    observer = observer,
                ),
            )
            assertEquals(reads, fixture.reads, "Explanation must not repeat or reorder node reads")
        }
        assertTrue(observer.conditions > 0)
    }

    @Test
    fun manualRecordingPreservesRootOnlyMissesAndAbsentRoots() {
        val fixture = Fixture()
        for ((source, expected) in
            listOf(
                "[parent=null]" to fixture.root,
                "[parent=null][text='target']" to null,
                "[text='target']" to fixture.a,
            )) {
            val diagnostics = MatchDiagnostics()
            val session = diagnostics.start(MatchTimingPolicy(), recordSystem = true)
            val target =
                recordManualQuery(
                    Selector.compile(source).value,
                    MatchOptions.default,
                    fixture.a,
                    { fixture.root },
                    fixture.adapter,
                    diagnostics,
                    session,
                    1,
                )
            assertSame(expected, target)
            val recording = diagnostics.export(session.id).queryRecordings.single()
            assertEquals(target != null, (recording.result?.type != MatchRecordedValueType.Null))
        }
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy(), recordSystem = true)
        assertNull(
            recordManualQuery(
                Selector.compile("[parent=null]").value,
                MatchOptions.default,
                fixture.a,
                { null },
                fixture.adapter,
                diagnostics,
                session,
                2,
            ),
        )
        val recording = diagnostics.export(session.id).queryRecordings.single()
        assertEquals(listOf(MatchNodeOperation.QueryRoot), recording.calls.map { it.operation })
        assertEquals(MatchRecordedValueType.Null, recording.result?.type)
    }

    @Test
    fun recordedCancellationRemainsAFailureWithoutExecutingUnvisitedReads() {
        val fixture = Fixture().apply { failText = true }
        val (_, capture) = fixture.record(MatchSelectorPlan(listOf("[text='target']")))
        assertEquals(1, fixture.reads.size)
        assertEquals(CancellationException::class.java.name, capture.failure)
        assertEquals("cancelled", capture.calls.single().failureMessage)
    }

    @Test
    fun recordingLimitsDoNotInterruptTheRealQueryAndAreReported() {
        val fixture = Fixture()
        val recorder = RecordingNodeAdapter(fixture.adapter)
        val source = recorder.value(fixture.root)
        recorder.begin(1, "rule", MatchSelectorPlan(listOf("[text=null]")), source)
        repeat(10_100) { recorder.getAttr(fixture.root, "text") }
        assertEquals(10_100, fixture.reads.size)
        recorder.finish(fixture.root, null)
        val capture = recorder.evidence.snapshot()
        assertEquals("recording_limit", capture.incomplete)
        assertTrue(
            capture.calls.size in 1..10_000,
            "Either the call or payload budget can end recording",
        )
        val values = RecordingNodeAdapter(fixture.adapter)
        values.value("x".repeat(20_000))
        assertEquals("value_limit", values.incomplete)
    }

    @Test
    fun objectEqualityAndLogicalKeysSurviveDistinctPlatformInstances() {
        data class Node(val id: Int)
        val a = Node(1)
        val alias = Node(1)
        val adapter =
            object : NodeAdapter<Node>() {
                override fun getAttr(target: Any, name: String): Any =
                    if (name == "left") a else alias

                override fun getName(node: Node) = "Node"

                override fun getNodeKey(node: Node): Any = Node(node.id)

                override fun getChildCount(node: Node) = 0

                override fun getChild(node: Node, index: Int): Node? = null

                override fun getParent(node: Node): Node? = null
            }
        val recorder = RecordingNodeAdapter(adapter)
        val source = recorder.value(a)
        val plan = MatchSelectorPlan(listOf("[left=right]"))
        recorder.begin(1, "rule", plan, source)
        val target =
            matchSelectorPlan(plan, a, { null }) { node, selector ->
                selector.match(node, recorder)
            }
        assertSame(a, target)
        recorder.finish(target, null)
        assertEquals(source, recorder.evidence.snapshot().result)
    }

    @Test
    fun recordingIsExplicitBoundedAndNeverLeaksIntoTheNextSession() =
        kotlinx.coroutines.test.runTest {
            val app = AppInfo("app.test", "Test", 1, "1", false, 0, false, 0)
            val simulation =
                MatchSimulation(
                    app,
                    root = MatchSimulatedNode("Button", mapOf("text" to JsonPrimitive("target"))),
                )
            val rule =
                prepareDebugRule(
                    MatchDebugRunRequest(
                        "test",
                        """{id:1,name:'Test',version:1,
            apps:[{id:'app.test',groups:[{key:1,name:'Test',rules:[{key:1,matches:'[text="target"]'}]}]}]}""",
                        "1/2/app.test/1/1",
                    ),
                    mapOf(app.id to app),
                )
            val diagnostics = MatchDiagnostics()
            val job = Job()
            try {
                val nodes =
                    SimulatedRuleNodes(
                        simulation,
                        SimulatedNodeAdapter(simulation.root, job),
                        MatchWork(1, MatchTimingPolicy(), diagnostics) { true },
                        diagnostics,
                    )
                val ordinary = diagnostics.start(MatchTimingPolicy())
                assertSame(simulation.root, nodes.match(rule, simulation.root))
                assertTrue(diagnostics.export(ordinary.id).queryRecordings.isEmpty())
                diagnostics.stop(ordinary.id)
                assertFailsWith<IllegalArgumentException> {
                    diagnostics.start(MatchTimingPolicy(), recordQueries = true)
                }
                val session =
                    diagnostics.start(
                        MatchTimingPolicy(),
                        detailed = true,
                        ruleFilter = rule.matchIdentity(),
                        recordQueries = true,
                    )
                repeat(10) { assertSame(simulation.root, nodes.match(rule, simulation.root)) }
                val capture = diagnostics.export(session.id)
                assertEquals(8, capture.queryRecordings.size)
                assertEquals(2, capture.droppedQueryRecordings)
                assertTrue(
                    capture.queryRecordings.all {
                        it.failure == null && it.incomplete == null && it.calls.isNotEmpty()
                    },
                )
                diagnostics.stop(session.id)
                val next = diagnostics.start(MatchTimingPolicy())
                diagnostics.registerQuery(
                    session.id,
                    MatchQueryEvidence(capture.queryRecordings.first()),
                )
                assertTrue(diagnostics.export(next.id).queryRecordings.isEmpty())
            } finally {
                job.cancel()
            }
        }
}
