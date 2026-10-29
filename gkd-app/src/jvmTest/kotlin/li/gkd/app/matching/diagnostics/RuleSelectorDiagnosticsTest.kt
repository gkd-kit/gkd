package li.gkd.app.matching.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import li.gkd.app.development.matching.SimulatedRuleNodes
import li.gkd.app.development.matching.prepareDebugRule
import li.gkd.app.matching.MatchWork
import li.gkd.app.matching.input.MatchTimingPolicy
import li.gkd.app.model.AppInfo
import li.gkd.selector.NodeAdapter
import li.gkd.selector.Selector
import li.gkd.selector.SelectorEvaluation
import li.gkd.selector.SelectorEvaluationKind

class RuleSelectorDiagnosticsTest {
    @Test
    fun boundedValuesAndConditionsSurviveExportWithoutRetainingNodeObjects() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy(), detailed = true, ruleFilter = "rule")
        val observer = RuleSelectorDiagnostics<Int>(diagnostics, session.id, 1, "rule")
        observer.begin(Selector.compile("[text='expected']").value, "matches[0]")
        repeat(250) {
            observer.record(
                SelectorEvaluation(
                    it,
                    0,
                    0,
                    SelectorEvaluationKind.Comparison,
                    "text='expected'",
                    false,
                    "x".repeat(300),
                    "expected",
                    "=",
                ),
            )
        }
        observer.outcome("matches[0]:miss")
        val capture = diagnostics.export(session.id)
        val conditions = capture.events.mapNotNull { it.condition }
        assertEquals(201, conditions.size)
        assertEquals(SelectorEvaluationKind.LimitReached, conditions.last().kind)
        assertEquals(256, conditions.first().left?.value?.length)
        assertEquals(true, conditions.first().left?.truncated)
        assertTrue(conditions.all { it.selectorPart == "matches[0]" })
        assertFalse(observer.enabled)
        assertEquals(
            capture,
            Json.decodeFromString<MatchDiagnosticCapture>(Json.encodeToString(capture)),
        )
    }

    @Test
    fun stoppedSessionCannotLeakValuesIntoANewSession() {
        val diagnostics = MatchDiagnostics()
        val first = diagnostics.start(MatchTimingPolicy(), detailed = true, ruleFilter = "rule")
        val observer = RuleSelectorDiagnostics<Int>(diagnostics, first.id, 1, "rule")
        observer.begin(Selector.compile("[text=null]").value, "matches[0]")
        observer.record(
            SelectorEvaluation(
                1,
                0,
                0,
                SelectorEvaluationKind.Comparison,
                "text=null",
                false,
                "private",
                null,
                "=",
            ),
        )
        diagnostics.stop(first.id)
        val next = diagnostics.start(MatchTimingPolicy(), detailed = true, ruleFilter = "rule")
        observer.outcome("matches[0]:miss")
        observer.interrupted()
        assertTrue(diagnostics.export(next.id).events.isEmpty())
    }

    @Test
    fun interruptedEvaluationExportsPartialConditionsWithoutInventingAnOutcome() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy(), detailed = true, ruleFilter = "rule")
        val observer = RuleSelectorDiagnostics<Int>(diagnostics, session.id, 1, "rule")
        observer.begin(Selector.compile("[text=null][enabled=true]").value, "matches[0]")
        observer.record(
            SelectorEvaluation(
                1,
                0,
                0,
                SelectorEvaluationKind.Comparison,
                "text=null",
                true,
                null,
                null,
                "=",
            ),
        )
        observer.interrupted()
        val events = diagnostics.export(session.id).events
        assertEquals("matches[0]", events.first().condition?.selectorPart)
        assertEquals("null", events.first().condition?.left?.type)
        assertNull(events.first().condition?.left?.value)
        assertEquals("matches[0]:incomplete", events.last().reason)
    }

    @Test
    fun disabledAndOrdinaryDiagnosticsDoNotReadLabelsFormatValuesOrUseDiagnosticClock() =
        kotlinx.coroutines.test.runTest {
            val app = AppInfo("app.test", "Test", 1, "1", false, 0, false, 0)
            val simulation = MatchSimulation(app, root = MatchSimulatedNode("Root"))
            val rule =
                prepareDebugRule(
                    MatchDebugRunRequest(
                        "test",
                        """{id:1,name:'Test',version:1,
            apps:[{id:'app.test',groups:[{key:1,name:'Test',rules:[{key:1,matches:'[text=null]'}]}]}]}""",
                        "1/2/app.test/1/1",
                    ),
                    mapOf(app.id to app),
                )
            var clockReads = 0
            var attributes = 0
            var keys = 0
            val diagnostics =
                MatchDiagnostics(
                    nanoTime = {
                        clockReads++
                        0
                    },
                )
            val work = MatchWork(1, MatchTimingPolicy(), diagnostics) { true }
            val adapter =
                object : NodeAdapter<MatchSimulatedNode>() {
                    override fun getAttr(target: Any, name: String): Any? {
                        attributes++
                        return null
                    }

                    override fun getName(node: MatchSimulatedNode) = error("No name needed")

                    override fun getNodeKey(node: MatchSimulatedNode): Any {
                        keys++
                        return node
                    }

                    override fun getParent(node: MatchSimulatedNode): MatchSimulatedNode? = null

                    override fun getChildCount(node: MatchSimulatedNode) = 0

                    override fun getChild(
                        node: MatchSimulatedNode,
                        index: Int,
                    ): MatchSimulatedNode? = null
                }
            val nodes = SimulatedRuleNodes(simulation, adapter, work, diagnostics)
            nodes.match(rule, simulation.root)
            assertEquals(0, clockReads)
            assertEquals(0, keys)
            assertEquals(1, attributes)
            val ordinary = diagnostics.start(MatchTimingPolicy())
            clockReads = 0
            nodes.match(rule, simulation.root)
            assertEquals(0, clockReads)
            assertEquals(0, keys)
            assertEquals(2, attributes)
            assertTrue(diagnostics.export(ordinary.id).events.isEmpty())
            diagnostics.stop(ordinary.id)
            val detailed =
                diagnostics.start(
                    MatchTimingPolicy(),
                    detailed = true,
                    ruleFilter = rule.matchIdentity(),
                )
            nodes.match(rule, simulation.root)
            assertEquals(
                3,
                attributes,
                "Explanation must use the evaluated operand instead of reading text again",
            )
            assertEquals(0, keys, "Diagnostic labels must not invoke adapter methods")
            assertTrue(
                diagnostics.export(detailed.id).events.any {
                    it.condition?.kind == SelectorEvaluationKind.Comparison
                },
            )
            diagnostics.stop(detailed.id)
            val unified =
                diagnostics.start(MatchTimingPolicy(), detailed = true, recordSystem = true)
            nodes.match(rule, simulation.root)
            val capture = diagnostics.export(unified.id)
            assertTrue(
                capture.events.any { it.condition?.kind == SelectorEvaluationKind.Comparison },
            )
            assertEquals(1, capture.queryRecordings.size)
            diagnostics.stop(unified.id)
            keys = 0
            clockReads = 0
            nodes.match(rule, simulation.root)
            assertEquals(0, keys)
            assertEquals(0, clockReads)
        }

    @Test
    fun delayedConditionRecordCannotWriteAttributesToAnOrdinarySession() {
        val diagnostics = MatchDiagnostics()
        val original = diagnostics.start(MatchTimingPolicy(), detailed = true, ruleFilter = "rule")
        val observer = RuleSelectorDiagnostics<Int>(diagnostics, original.id, 1, "rule")
        observer.begin(Selector.compile("[text=null]").value, "matches[0]")
        observer.record(
            SelectorEvaluation(
                1,
                0,
                0,
                SelectorEvaluationKind.Comparison,
                "text=null",
                false,
                "private",
                null,
                "=",
            ),
        )
        val old = diagnostics.export(original.id).events.single()
        diagnostics.stop(original.id)
        val nextId = diagnostics.start(MatchTimingPolicy()).id
        // Simulate a record reaching the diagnostics lock after its session was replaced.
        diagnostics.record(
            old.runId,
            old.stage,
            old.reason,
            old.rule,
            condition = old.condition,
            sessionId = original.id,
        )
        assertTrue(diagnostics.export(nextId).events.isEmpty())
    }
}
