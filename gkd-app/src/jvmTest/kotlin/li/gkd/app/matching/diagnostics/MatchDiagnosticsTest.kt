package li.gkd.app.matching.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import li.gkd.app.matching.ForegroundRuleSnapshot
import li.gkd.app.matching.MatchCoordinator
import li.gkd.app.matching.RuleAvailability
import li.gkd.app.rule.RuleRuntime
import li.gkd.app.matching.input.MatchTimingPolicy
import li.gkd.app.model.AppInfo
import li.gkd.app.rule.RuleSummaryBuilder
import li.gkd.app.rule.TopActivity
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.RawSubscriptionParser
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsItem

class MatchDiagnosticsTest {
    @Test
    fun initialConfigurationRetainsDisabledAndInvalidDefinitionsForDiagnosis() {
        val subscription = RawSubscriptionParser.parse("""{id:1,name:'Test',version:1,apps:[{id:'app.test',groups:[{key:1,name:'Invalid',rules:[{matches:'['}]}]}]}""")
        val config = li.gkd.app.subscription.SubscriptionBackupSnapshot(
            li.gkd.db.SubscriptionConfigSnapshot(subsItems = listOf(SubsItem(id = 1, order = 0, enable = false))),
            listOf(subscription))
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy(), initialConfiguration = config)
        diagnostics.stop(session.id)
        val restored = Json.decodeFromString<MatchDiagnosticCapture>(Json.encodeToString(diagnostics.export(session.id)))
        assertFalse(restored.initialConfiguration!!.configuration.subsItems.single().enable)
        assertFalse(restored.initialConfiguration.subscriptions.single().apps.single().groups.single().valid)
        val next = diagnostics.start(MatchTimingPolicy())
        assertEquals(null, diagnostics.export(next.id).initialConfiguration)
    }

    @Test
    fun systemCaptureKeepsRecentQueriesAndReportsEvictionIncludingLateCleanup() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy(), detailed = true, recordSystem = true)
        fun query(id: Long) = MatchQueryRecording(id, "rule", MatchSelectorPlan(listOf("*")),
            MatchRecordedValue("null"), emptyList(), null, null, null)
        repeat(40) {
            assertTrue(diagnostics.shouldRecordQuery(session.id))
            diagnostics.recordQuery(session.id, query(it.toLong()))
        }
        diagnostics.stop(session.id)
        assertFalse(diagnostics.shouldRecordQuery(session.id))
        // An in-flight query may complete during action cleanup, after Stop.
        diagnostics.recordQuery(session.id, query(40))
        val capture = diagnostics.export(session.id)
        assertEquals((9L..40L).toList(), capture.queryRecordings.map { it.runId })
        assertEquals(9L, capture.droppedQueryRecordings)
        assertEquals(capture, Json.decodeFromString<MatchDiagnosticCapture>(Json.encodeToString(capture)))
        val next = diagnostics.start(MatchTimingPolicy(), recordSystem = true)
        diagnostics.recordQuery(session.id, query(41))
        assertTrue(diagnostics.export(next.id).queryRecordings.isEmpty())
        assertEquals(0L, diagnostics.export(next.id).droppedQueryRecordings)
    }

    @Test
    fun liveViewReturnsLatestFilteredEventsAndKeepsSessionIdentity() {
        val diagnostics = MatchDiagnostics()
        val first = diagnostics.start(MatchTimingPolicy(), capacity = 1000)
        repeat(300) { diagnostics.entry("source", if (it % 2 == 0) "accepted" else "ignored") }
        val view = diagnostics.currentView("accepted", 3)!!
        assertEquals(first.id, view.session.id)
        assertEquals(listOf(295L, 297L, 299L), view.events.map { it.sequence })
        assertEquals(200, diagnostics.currentView()!!.events.size)
        assertEquals(300, diagnostics.export(first.id).events.size)
        diagnostics.stop(first.id)
        assertFalse(diagnostics.session(first.id).active)
        val next = diagnostics.start(MatchTimingPolicy())
        assertEquals(next.id, diagnostics.currentView()!!.session.id)
        assertTrue(diagnostics.currentView()!!.events.isEmpty())
        kotlin.test.assertFailsWith<IllegalArgumentException> { diagnostics.session(first.id) }
        // Previously sampled UI data remains an immutable snapshot after replacement.
        assertEquals(3, view.events.size)
    }

    @Test
    fun lightweightSessionAndViewFollowSystemCleanupWithoutExportingSteps() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy(), recordSystem = true)
        val active = li.gkd.app.matching.MatchScheduleCheckpoint(
            active = li.gkd.app.matching.MatchScheduledRun(1, 1, 0))
        val recorder = MatchSystemRecorder(active, 0)
        diagnostics.systemRecorder = recorder
        diagnostics.stop(session.id)
        recorder.stop(10, active)
        assertTrue(diagnostics.session(session.id).systemFinalizing)
        assertFalse(diagnostics.currentView()!!.system!!.sealed)
        val finished = active.copy(active = null)
        recorder.append(li.gkd.app.matching.MatchScheduleCommand("finish", run = 1), 20, emptyList(), finished)
        assertFalse(diagnostics.session(session.id).systemFinalizing)
        val view = diagnostics.currentView()!!
        assertEquals(1, view.system!!.steps)
        assertTrue(view.system.sealed)
        assertEquals(view.session, diagnostics.export(session.id).session)
    }

    @Test
    fun disabledObservationNeverReadsClocksAndPreservesResultsAndErrors() {
        val diagnostics = MatchDiagnostics(nanoTime = { error("Unexpected clock") }, wallTime = { error("Unexpected clock") })
        val result = Any()
        kotlin.test.assertSame(result, diagnostics.observeOperation("Child") { result })
        val failure = IllegalStateException("original")
        kotlin.test.assertSame(failure, kotlin.test.assertFailsWith<IllegalStateException> {
            diagnostics.observeOperation("Child") { throw failure }
        })
        assertEquals(null, diagnostics.beginOperation("Root"))
        diagnostics.nodeReadPressure("node_read_timeout", 1, 4)
        diagnostics.entry("event_queue", "event_queue_overflow")
        assertEquals(null, diagnostics.state.value)
    }

    /** Calendar changes affect log timestamps only, never measured durations or queue/read details. */
    @Test
    fun elapsedDiagnosticsSurviveWallClockJumpsAndExportPressure() {
        var nanos = 0L
        var wall = 1_000_000L
        val diagnostics = MatchDiagnostics(nanoTime = { nanos }, wallTime = { wall })
        val session = diagnostics.start(MatchTimingPolicy())
        val read = diagnostics.beginOperation("NodeRead")!!
        wall -= 100_000
        nanos = 100_000_000
        diagnostics.nodeReadPressure("node_read_timeout", 2, 4)
        val drop = MatchEventDrop(li.gkd.app.matching.input.MatchEventKey(li.gkd.app.matching.input.MatchEventKind.Content, "app", "Window"), 64, 300)
        diagnostics.entry("event_queue", "event_queue_overflow", eventDrop = drop)
        wall += 900_000
        nanos = 200_000_000
        diagnostics.endOperation(read, "returned")
        val capture = diagnostics.export(session.id)
        assertEquals(100_000_000L, capture.events.single { it.nodePressure != null }.nodePressure!!.oldestObservedReadNanos)
        assertEquals(1, capture.events.single { it.nodePressure != null }.nodePressure!!.observedReads)
        assertEquals(drop, capture.events.single { it.eventDrop != null }.eventDrop)
        assertEquals(200_000_000L, capture.events.last().operation!!.durationNanos)
        assertEquals(capture, Json.decodeFromString<MatchDiagnosticCapture>(Json.encodeToString(capture)))
        assertEquals(1L, capture.entries.single { it.reason == "event_queue_overflow" }.count)
    }

    @Test
    fun openCallsSurviveRingOverflowAndStopWithoutLeakingIntoNextSession() {
        var nanos = 0L
        val diagnostics = MatchDiagnostics(nanoTime = { nanos }, wallTime = { 0 })
        val session = diagnostics.start(MatchTimingPolicy(), capacity = 10)
        val open = diagnostics.beginOperation("FindText", 17)!!
        repeat(20) { diagnostics.entry("source", "ready") }
        assertEquals(listOf(open), diagnostics.export(session.id).session.openOperations)
        nanos = 5_000_000_000
        diagnostics.observeOperation("Child", 17) {
            assertEquals(2, diagnostics.state.value!!.openOperations.size)
            nanos += 12_000_000
            false
        }
        val finished = diagnostics.export(session.id).events.last().operation!!
        assertEquals(12_000_000L, finished.durationNanos)
        assertEquals("false", finished.outcome)
        diagnostics.stop(session.id)
        val stopped = diagnostics.export(session.id)
        diagnostics.endOperation(open, "returned")
        assertEquals(stopped, diagnostics.export(session.id))
        val next = diagnostics.start(MatchTimingPolicy())
        diagnostics.endOperation(open, "returned")
        assertTrue(diagnostics.export(next.id).events.isEmpty())
        assertTrue(diagnostics.state.value!!.openOperations.isEmpty())
    }

    @Test
    fun operationLimitIsExplicitAndFailuresStillPropagate() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy())
        val failure = IllegalArgumentException("original")
        kotlin.test.assertSame(failure, kotlin.test.assertFailsWith<IllegalArgumentException> {
            diagnostics.observeOperation("Refresh") { throw failure }
        })
        assertEquals(failure.toString(), diagnostics.export(session.id).events.last().operation!!.outcome)
        repeat(129) { diagnostics.beginOperation("Child") }
        assertEquals(128, diagnostics.state.value!!.openOperations.size)
        assertEquals(1L, diagnostics.state.value!!.omittedOperations)
    }

    private fun activity(version: Int = 1, blocked: Boolean = false): ForegroundRuleSnapshot {
        val raw = RawSubscriptionParser.parse("""{id:1,name:'Test',version:$version,apps:[{id:'app.test',groups:[
            {key:1,name:'Current',scopeKeys:[2],actionDelay:150,rules:[{key:1,matches:'*',actionCdKey:2}]},
            {key:2,name:'Dependency',activityIds:['Other'],rules:[{key:2,matches:'[text="$version"]'}]}
        ]}]}""")
        val summary = RuleSummaryBuilder.build(RuleRuntime(),
            listOf(UsedSubsEntry(SubsItem(1, enable = true, order = 0), raw)),
            mapOf("app.test" to AppInfo("app.test", "Test", 1, "1", false, 0, false, 0)),
            emptyList(), emptyList(), emptyList())
        return ForegroundRuleSnapshot(TopActivity("app.test", "Current"), blocked, summary)
    }

    @Test
    fun snapshotPreservesDefinitionDependenciesAndFilteredCandidatesAcrossUpdates() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy())
        val old = activity()
        diagnostics.captureRules(old, 1)
        diagnostics.captureRules(old, 2)
        diagnostics.captureRules(activity(2, blocked = true), 3)
        val capture = diagnostics.export(session.id)
        assertEquals(2, capture.ruleSnapshots.size)
        val first = capture.ruleSnapshots.first()
        assertEquals(2, first.candidates.size)
        assertEquals(1, first.selected.size)
        assertEquals(listOf("1/2/app.test/2/2"), first.groups.first().dependencies.values.single())
        assertTrue(capture.ruleSnapshots.last().selected.isEmpty())
        assertTrue(capture.ruleSnapshots.last().blockMatch)
        val restored = Json.decodeFromString<MatchDiagnosticCapture>(Json.encodeToString(capture))
        assertEquals(capture, restored)
        assertEquals(1, restored.ruleSnapshots.first().groups.first().subscriptionVersion)
        assertTrue(restored.ruleSnapshots.first().groups.first().definition.containsKey("actionDelay"))
    }

    @Test
    fun boundsDeclareLostSnapshotsAndKeepEntryTotalsAfterEventOverflow() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy(), capacity = 10)
        repeat(20) {
            diagnostics.captureRules(activity(it))
            diagnostics.entry("query_request", "matching_disabled")
        }
        val capture = diagnostics.export(session.id)
        assertEquals(16, capture.ruleSnapshots.size)
        assertEquals(4L, capture.droppedRuleSnapshots)
        assertEquals(10, capture.events.size)
        assertEquals(30L, capture.session.dropped)
        assertEquals(20L, capture.entries.single().count)
        assertTrue(diagnostics.read(session.id).gap)
        diagnostics.stop(session.id)
        val stopped = diagnostics.export(session.id)
        diagnostics.captureRules(activity(100))
        diagnostics.entry("query_request", "matching_disabled")
        assertEquals(stopped, diagnostics.export(session.id))
    }

    @Test
    fun loadingFailureAndLoadedEmptyKeepDistinctCaptureReasons() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy())
        for (availability in listOf(RuleAvailability.Loading, RuleAvailability.Failed, RuleAvailability.Ready)) {
            val snapshot = ForegroundRuleSnapshot(blockMatch = false, ruleAvailability = availability)
            diagnostics.captureRules(snapshot)
            diagnostics.entry("query_request", snapshot.emptyReason)
        }
        val capture = diagnostics.export(session.id)
        assertEquals(listOf("rules_loading", "rules_load_failed", "no_rules_for_activity"), capture.entries.map { it.reason })
        assertTrue(capture.ruleSnapshots.all { it.selected.isEmpty() })
        assertEquals(3, capture.ruleSnapshots.map { it.availability }.distinct().size)
    }

    @Test
    fun sessionStartedWhileBlockedExportsRulesWithoutWaitingForAQuery() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        try {
            coordinator.updateRules(activity(blocked = true))
            assertEquals(null, coordinator.diagnostics.state.value)
            val session = coordinator.startDiagnostics(10000, false, null)
            val capture = coordinator.diagnostics.export(session.id)
            assertTrue(capture.ruleSnapshots.single().blockMatch)
            assertEquals("service_unavailable", capture.entries.single().reason)
            val service = Any()
            coordinator.request(service) { error("Detached service must not execute") }
            runCurrent()
            assertFalse(coordinator.snapshot().queryPending)
            assertEquals(2L, coordinator.diagnostics.export(session.id).entries.single().count)
        } finally { coordinator.close() }
    }
}
