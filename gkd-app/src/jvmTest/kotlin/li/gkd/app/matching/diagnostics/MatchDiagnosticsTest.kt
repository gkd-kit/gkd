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
import li.gkd.app.matching.MatchScheduleCheckpoint
import li.gkd.app.matching.MatchScheduleCommand
import li.gkd.app.matching.MatchScheduleCommandType
import li.gkd.app.matching.MatchScheduledRun
import li.gkd.app.matching.MatchWork
import li.gkd.app.matching.RuleAvailability
import li.gkd.app.matching.input.MatchEventKey
import li.gkd.app.matching.input.MatchEventKind
import li.gkd.app.matching.input.MatchTimingPolicy
import li.gkd.app.matching.query.MatchNodeReadFailure
import li.gkd.app.model.ActionResult
import li.gkd.app.model.AppInfo
import li.gkd.app.model.GkdAction
import li.gkd.app.rule.RuleRuntime
import li.gkd.app.rule.RuleSummaryBuilder
import li.gkd.app.rule.TopActivity
import li.gkd.app.subscription.RawSubscriptionParser
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsItem

class MatchDiagnosticsTest {
    @Test
    fun boundaryTimelineSurvivesQueryOverflowAndReportsItsOwnLoss() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy(), capacity = 10)
        diagnostics.record(
            0,
            MatchStage.Root,
            "page",
            pageObservation = MatchPageObservation(1, "app", "Login", true),
        )
        repeat(30) { diagnostics.record(1, MatchStage.Selector, "matches[0]:miss") }
        val capture = diagnostics.export(session.id)
        assertTrue(capture.droppedKeyEvents > 0)
        assertEquals(true, capture.boundaryEvents.single().pageObservation?.firstConfirmation)
        assertEquals(0L, capture.droppedBoundaryEvents)
        repeat(12) {
            diagnostics.record(
                0,
                MatchStage.Root,
                "update",
                pageObservation = MatchPageObservation(1, "app", "Login", false),
            )
        }
        diagnostics.stop(session.id)
        val stopped = diagnostics.export(session.id)
        assertEquals(10, stopped.boundaryEvents.size)
        assertEquals(3L, stopped.droppedBoundaryEvents)
        assertEquals(
            stopped,
            Json.decodeFromString<MatchDiagnosticCapture>(Json.encodeToString(stopped)),
        )
        val next = diagnostics.start(MatchTimingPolicy())
        assertTrue(diagnostics.export(next.id).boundaryEvents.isEmpty())
        assertEquals(stopped.boundaryEvents, diagnostics.export(session.id).boundaryEvents)
    }

    @Test
    fun windowAndActionEvidenceSurvivesDetailEvictionAndExport() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy(), capacity = 10)
        // An undefined event window must remain distinguishable from a real target window.
        val event = MatchWindowEvent(32, -1, 0, 0, 100, "app", "Search")
        val target = MatchActionTarget(7, "app", "more", -100, 20, 0, 120, true)
        val task = MatchTaskObservation(9, "onTaskStackChanged", "app", "Search")
        diagnostics.record(0, MatchStage.Root, "accessibility_event_received", windowEvent = event)
        diagnostics.record(12, MatchStage.Action, "action_target", "rule", actionTarget = target)
        diagnostics.record(0, MatchStage.Root, "task_result_received", taskObservation = task)
        repeat(30) {
            diagnostics.beginOperation("Selector.attr", 12, "rule")?.let {
                diagnostics.endOperation(it, "null")
            }
        }
        diagnostics.stop(session.id)
        val capture =
            Json.decodeFromString<MatchDiagnosticCapture>(
                Json.encodeToString(diagnostics.export(session.id)),
            )
        assertTrue(capture.session.dropped > 0)
        assertEquals(event, capture.keyEvents.single { it.windowEvent != null }.windowEvent)
        val action = capture.keyEvents.single { it.actionTarget != null }
        assertEquals(target, action.actionTarget)
        assertEquals(12L, action.runId)
        assertEquals("rule", action.rule)
        assertEquals(task, capture.keyEvents.single { it.taskObservation != null }.taskObservation)
    }

    @Test
    fun keyTimelineSurvivesDetailedComparisonEvictionAndKeepsReadAssociation() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy(), capacity = 10)
        val origin =
            MatchNodeProvenance(
                1,
                2,
                li.gkd.app.settings.AutomatorMode.A11y,
                MatchNodeSource.Root,
                MatchNodeReadReason.Event,
                3,
                "app",
                "Login",
                4,
                100,
            )
        val connection =
            MatchServiceConnection(MatchServiceEvent.Connected, origin.service, origin.connectionId)
        diagnostics.record(0, MatchStage.Idle, "service connected", serviceConnection = connection)
        diagnostics.record(0, MatchStage.Root, "node_acquired", node = origin)
        val observer = RuleSelectorDiagnostics<String>(diagnostics, session.id, 10, "rule")
        // Detail traffic must not evict the only evidence of the service/page node read.
        repeat(50) {
            diagnostics.beginOperation("Selector.attr", 10, "rule")?.let {
                diagnostics.endOperation(it, "null")
            }
        }
        diagnostics.record(
            10,
            MatchStage.Root,
            "node_used",
            "rule",
            node = origin.copy(ageMillis = 300),
            nodeRole = MatchNodeRole.Source,
        )
        observer.outcome("matches[0]:miss")
        diagnostics.record(
            0,
            MatchStage.Root,
            "node_acquired",
            node = origin.copy(readId = 5, acquiredAtMillis = 500),
        )
        diagnostics.record(
            11,
            MatchStage.Root,
            "node_used",
            "rule",
            node = origin.copy(readId = 5, acquiredAtMillis = 500),
            nodeRole = MatchNodeRole.Source,
        )
        diagnostics.record(11, MatchStage.Selector, "matched", "rule")
        diagnostics.stop(session.id)
        val capture = diagnostics.export(session.id)
        assertTrue(capture.session.dropped > 0)
        assertEquals(0L, capture.droppedKeyEvents)
        assertEquals(connection, capture.keyEvents.first().serviceConnection)
        assertTrue(capture.events.none { it.node?.readId == 1L && it.reason == "node_acquired" })
        assertEquals(listOf(1L, 1L, 5L, 5L), capture.keyEvents.mapNotNull { it.node?.readId })
        assertTrue(capture.keyEvents.any { it.reason == "matches[0]:miss" })
        assertEquals(
            capture,
            Json.decodeFromString<MatchDiagnosticCapture>(Json.encodeToString(capture)),
        )
    }

    @Test
    fun keyTimelineReportsItsOwnEvictionAndRetainsLateQueryCompletion() {
        val diagnostics = MatchDiagnostics()
        val first = diagnostics.start(MatchTimingPolicy(), capacity = 10)
        repeat(12) { diagnostics.record(it.toLong(), MatchStage.Query, "query_started") }
        val operation = diagnostics.beginOperation("Query", 12)!!
        diagnostics.stop(first.id)
        diagnostics.start(MatchTimingPolicy())
        diagnostics.endOperation(operation, "not_matched")
        val capture = diagnostics.export(first.id)
        assertEquals(10, capture.keyEvents.size)
        assertEquals(4L, capture.droppedKeyEvents)
        assertEquals("not_matched", capture.keyEvents.last().operation?.outcome)
    }

    @Test
    fun operationTotalsSurviveDetailEvictionAndDoNotRetainQueryEvidence() {
        var nanos = 0L
        val diagnostics = MatchDiagnostics(nanoTime = { nanos })
        val session = diagnostics.start(MatchTimingPolicy(), capacity = 10)
        repeat(40) { index ->
            val operation =
                diagnostics.beginOperation(
                    "SelectorQuery",
                    index.toLong(),
                    "rule",
                    recording = MatchQueryEvidence(),
                )
            nanos += (index + 1) * 1_000L
            diagnostics.endOperation(operation, "not_matched")
        }
        repeat(20) { diagnostics.entry("input", "accepted") }
        diagnostics.stop(session.id)
        val capture = diagnostics.export(session.id)
        val stats = capture.operationStatistics!!
        assertEquals(40L, stats.completed)
        assertEquals(0L, stats.omitted)
        assertEquals(820_000L, stats.totals.single().totalDurationNanos)
        assertEquals(40_000L, stats.totals.single().slowest.durationNanos)
        assertEquals(39L, stats.totals.single().slowest.runId)
        assertEquals(null, stats.totals.single().slowest.recording)
        assertTrue(capture.events.none { it.operation != null })
        assertEquals(
            capture,
            Json.decodeFromString<MatchDiagnosticCapture>(Json.encodeToString(capture)),
        )
    }

    @Test
    fun operationStatisticsBoundsRuleGroupsButKeepsCountingExistingGroups() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy())
        repeat(130) {
            diagnostics.endOperation(
                diagnostics.beginOperation("Query", rule = "rule-$it"),
                "not_matched",
            )
        }
        diagnostics.endOperation(diagnostics.beginOperation("Query", rule = "rule-0"), "matched")
        val stats = diagnostics.export(session.id).operationStatistics!!
        assertEquals(131L, stats.completed)
        assertEquals(2L, stats.omitted)
        assertEquals(128, stats.totals.size)
        assertEquals(2L, stats.totals.single { it.rule == "rule-0" }.count)
    }

    @Test
    fun workKeepsResultsAndRecordsOperationsOnlyWhileCaptureIsEnabled() = runTest {
        val diagnostics = MatchDiagnostics()
        val work = MatchWork(42, MatchTimingPolicy(), diagnostics) { true }
        val rule = activity().currentRules.first()
        val result = ActionResult("click", true)
        assertEquals("before", work.match(rule) { "before" })
        assertEquals(result, work.performAction(rule) { result })
        val session = diagnostics.start(MatchTimingPolicy())
        assertEquals("during", work.match(rule) { "during" })
        assertEquals(result, work.performAction(rule) { result })
        diagnostics.stop(session.id)
        assertEquals("after", work.match(rule) { "after" })
        assertEquals(result, work.performAction(rule) { result })
        val operations =
            diagnostics
                .export(session.id)
                .events
                .mapNotNull { it.operation }
                .filter { it.durationNanos != null }
        assertEquals(listOf("Query", "Action"), operations.map { it.name })
        assertTrue(operations.all { it.runId == 42L && it.rule == rule.matchIdentity() })
        assertEquals(listOf("matched", "success"), operations.map { it.outcome })
    }

    @Test
    fun stoppedStreamDrainsAllPagesBeforeClosing() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy())
        repeat(12) { diagnostics.entry("input", "accepted") }
        diagnostics.stop(session.id)
        val first = diagnostics.read(session.id, limit = 5)
        assertTrue(first.trackingComplete)
        assertTrue(first.hasMore)
        val second = diagnostics.read(session.id, first.cursor, 5)
        assertTrue(second.hasMore)
        val last = diagnostics.read(session.id, second.cursor, 5)
        assertTrue(last.trackingComplete)
        assertFalse(last.hasMore)
        assertEquals(
            (1L..12L).toList(),
            (first.events + second.events + last.events).map { it.sequence },
        )
    }

    @Test
    fun lateCallsKeepOriginalClockAndRemainReadableAcrossCompletedSessionHistory() {
        var nanos = 1_000L
        val saved = mutableListOf<MatchDiagnosticCapture>()
        val diagnostics =
            MatchDiagnostics(nanoTime = { nanos }, onStoppedCapture = { saved.add(it) })
        val first = diagnostics.start(MatchTimingPolicy())
        val operation = diagnostics.beginOperation("NodeRead:Root", 7)!!
        diagnostics.stop(first.id)
        val before = diagnostics.read(first.id)
        assertFalse(before.active)
        assertFalse(before.trackingComplete)
        repeat(7) {
            nanos += 1_000
            val interim = diagnostics.start(MatchTimingPolicy())
            diagnostics.stop(interim.id)
        }
        val next = diagnostics.start(MatchTimingPolicy())
        nanos = 20_000
        diagnostics.endOperation(operation, "returned")
        val after = diagnostics.read(first.id, before.cursor)
        assertTrue(after.trackingComplete)
        assertFalse(after.hasMore)
        assertEquals("operation_finished_after_stop", after.events.single().reason)
        assertEquals(19_000L, after.events.single().operation!!.durationNanos)
        assertEquals(first.id, saved.single().session.id)
        assertEquals(1L, saved.single().operationStatistics!!.completed)
        assertEquals(
            19_000L,
            saved.single().operationStatistics!!.totals.single().totalDurationNanos,
        )
        diagnostics.endOperation(operation, "returned")
        assertEquals(1L, diagnostics.export(first.id).operationStatistics!!.completed)
        assertEquals(0L, diagnostics.export(next.id).operationStatistics!!.completed)
        assertTrue(diagnostics.export(next.id).events.isEmpty())
    }

    @kotlin.test.Test
    fun actionApiTimingKeepsItsParentAcrossDispatcherChanges() =
        kotlinx.coroutines.test.runTest {
            val clock = java.util.concurrent.atomic.AtomicLong(0)
            val diagnostics = MatchDiagnostics(nanoTime = clock::get, wallTime = { 0 })
            val session = diagnostics.start(MatchTimingPolicy())
            val work = MatchWork(42, MatchTimingPolicy(), diagnostics) { true }
            val result =
                work.performAction(GkdAction("*", action = "click")) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        observeActionOperation("PlatformClick") {
                            clock.addAndGet(5_000_000)
                            ActionResult("click", true)
                        }
                    }
                }
            assertTrue(result.result)
            val operations =
                diagnostics
                    .export(session.id)
                    .events
                    .mapNotNull { it.operation }
                    .filter { it.durationNanos != null }
            val action = operations.single { it.name == "Action" }
            val api = operations.single { it.name == "PlatformClick" }
            assertEquals(action.id, api.parentOperationId)
            assertEquals(42L, api.runId)
            assertEquals("manual", api.rule)
            assertEquals(5_000_000L, api.durationNanos)
        }

    @Test
    fun initialIndexContainsEnabledRuleIdsWithoutSubscriptionDefinitions() = runTest {
        val coordinator =
            MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        try {
            val snapshot = activity()
            coordinator.updateRules(snapshot)
            val session = coordinator.startDiagnostics(false, null)
            val capture = coordinator.exportDiagnostics(session.id)
            assertEquals(snapshot.ruleSummary.diagnosticIndex(), capture.initialRules)
            assertEquals(3, capture.initialRules!!.single().rules.size)
            assertEquals(1, capture.initialRules.single().subscriptionVersion)
            assertFalse(Json.encodeToString(capture).contains("initialConfiguration"))
            coordinator.stopDiagnostics(session.id)
            assertEquals(
                capture.initialRules,
                coordinator.exportDiagnostics(session.id).initialRules,
            )
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun systemCaptureKeepsRecentQueriesAndReportsEvictionIncludingLateCleanup() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy(), detailed = true, recordSystem = true)
        fun query(id: Long) =
            MatchQueryRecording(
                id,
                "rule",
                MatchSelectorPlan(listOf("*")),
                MatchRecordedValue(MatchRecordedValueType.Null),
                emptyList(),
                null,
                null,
                null,
            )
        repeat(40) {
            assertTrue(diagnostics.shouldRecordQuery(session.id))
            diagnostics.registerQuery(session.id, MatchQueryEvidence(query(it.toLong())))
        }
        diagnostics.stop(session.id)
        assertFalse(diagnostics.shouldRecordQuery(session.id))
        // An in-flight query may complete during action cleanup, after Stop.
        diagnostics.registerQuery(session.id, MatchQueryEvidence(query(40)))
        val capture = diagnostics.export(session.id)
        assertEquals((9L..40L).toList(), capture.queryRecordings.map { it.runId })
        assertEquals(9L, capture.droppedQueryRecordings)
        assertEquals(
            capture,
            Json.decodeFromString<MatchDiagnosticCapture>(Json.encodeToString(capture)),
        )
        val next = diagnostics.start(MatchTimingPolicy(), recordSystem = true)
        diagnostics.registerQuery(session.id, MatchQueryEvidence(query(41)))
        assertTrue(diagnostics.export(next.id).queryRecordings.isEmpty())
        assertEquals(0L, diagnostics.export(next.id).droppedQueryRecordings)
    }

    @Test
    fun exportRetainsStoppedSessionAfterReplacement() {
        val diagnostics = MatchDiagnostics()
        val first = diagnostics.start(MatchTimingPolicy(), capacity = 1000)
        repeat(300) { diagnostics.entry("source", if (it % 2 == 0) "accepted" else "ignored") }
        val capture = diagnostics.export(first.id)
        assertEquals(300, capture.events.size)
        diagnostics.stop(first.id)
        val next = diagnostics.start(MatchTimingPolicy())
        assertTrue(diagnostics.export(next.id).events.isEmpty())
        assertTrue(diagnostics.session(first.id).trackingComplete)
        assertEquals(capture.events, diagnostics.export(first.id).events)
    }

    @Test
    fun sessionFollowsSystemCleanupAndExportPreservesSteps() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy(), recordSystem = true)
        val active =
            MatchScheduleCheckpoint(
                active = MatchScheduledRun(1, 1, 0),
            )
        val recorder = MatchSystemRecorder(active, 0)
        diagnostics.systemRecorder = recorder
        diagnostics.stop(session.id)
        recorder.stop(10, active)
        assertTrue(diagnostics.session(session.id).systemFinalizing)
        assertFalse(diagnostics.export(session.id).systemRecording!!.sealed)
        val finished = active.copy(active = null)
        recorder.append(
            MatchScheduleCommand(MatchScheduleCommandType.Finish, run = 1),
            20,
            emptyList(),
            finished,
        )
        assertFalse(diagnostics.session(session.id).systemFinalizing)
        val capture = diagnostics.export(session.id)
        assertEquals(1, capture.systemRecording!!.steps.size)
        assertTrue(capture.systemRecording.sealed)
        assertEquals(diagnostics.session(session.id), capture.session)
    }

    @Test
    fun disabledObservationNeverReadsClocksAndPreservesResultsAndErrors() {
        val diagnostics =
            MatchDiagnostics(
                nanoTime = { error("Unexpected clock") },
                wallTime = { error("Unexpected clock") },
            )
        val result = Any()
        kotlin.test.assertSame(result, diagnostics.observeOperation("Child") { result })
        val failure = IllegalStateException("original")
        kotlin.test.assertSame(
            failure,
            kotlin.test.assertFailsWith<IllegalStateException> {
                diagnostics.observeOperation("Child") { throw failure }
            },
        )
        assertEquals(null, diagnostics.beginOperation("Root"))
        diagnostics.nodeReadPressure(MatchNodeReadFailure.Timeout, 1, 4)
        diagnostics.entry("event_queue", "event_queue_overflow")
        assertEquals(null, diagnostics.state.value)
    }

    /**
     * Calendar changes affect log timestamps only, never measured durations or queue/read details.
     */
    @Test
    fun elapsedDiagnosticsSurviveWallClockJumpsAndExportPressure() {
        var nanos = 0L
        var wall = 1_000_000L
        val diagnostics = MatchDiagnostics(nanoTime = { nanos }, wallTime = { wall })
        val session = diagnostics.start(MatchTimingPolicy())
        val read = diagnostics.beginOperation("NodeRead")!!
        wall -= 100_000
        nanos = 100_000_000
        diagnostics.nodeReadPressure(MatchNodeReadFailure.Timeout, 2, 4)
        val drop = MatchEventDrop(MatchEventKey(MatchEventKind.Content, "app", "Window"), 64, 300)
        diagnostics.entry("event_queue", "event_queue_overflow", eventDrop = drop)
        wall += 900_000
        nanos = 200_000_000
        diagnostics.endOperation(read, "returned")
        val capture = diagnostics.export(session.id)
        assertEquals(
            100_000_000L,
            capture.events
                .single { it.nodePressure != null }
                .nodePressure!!
                .oldestObservedReadNanos,
        )
        assertEquals(
            1,
            capture.events.single { it.nodePressure != null }.nodePressure!!.observedReads,
        )
        assertEquals(drop, capture.events.single { it.eventDrop != null }.eventDrop)
        assertEquals(200_000_000L, capture.events.last().operation!!.durationNanos)
        assertEquals(
            capture,
            Json.decodeFromString<MatchDiagnosticCapture>(Json.encodeToString(capture)),
        )
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
        assertEquals(listOf(open), stopped.session.operationsAtStop)
        diagnostics.endOperation(open, "returned")
        val completed = diagnostics.export(session.id)
        assertTrue(completed.session.openOperations.isEmpty())
        assertEquals(stopped.session.operationsAtStop, completed.session.operationsAtStop)
        assertEquals("operation_finished_after_stop", completed.events.last().reason)
        assertEquals(open.id, completed.events.last().operation!!.id)
        assertEquals(nanos, completed.events.last().operation!!.durationNanos)
        val next = diagnostics.start(MatchTimingPolicy())
        diagnostics.endOperation(open, "returned")
        assertEquals(
            null,
            diagnostics.beginOperation(
                "OldChild",
                parentOperationId = open.id,
                sessionId = session.id,
            ),
        )
        assertEquals(
            "result",
            diagnostics.observeOperation("OldAction", sessionId = session.id) { "result" },
        )
        assertTrue(diagnostics.export(next.id).events.isEmpty())
        assertTrue(diagnostics.state.value!!.openOperations.isEmpty())
    }

    @Test
    fun operationLimitIsExplicitAndFailuresStillPropagate() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy())
        val failure = IllegalArgumentException("original")
        kotlin.test.assertSame(
            failure,
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                diagnostics.observeOperation("Refresh") { throw failure }
            },
        )
        assertEquals(
            failure.toString(),
            diagnostics.export(session.id).events.last().operation!!.outcome,
        )
        repeat(129) { diagnostics.beginOperation("Child") }
        assertEquals(128, diagnostics.state.value!!.openOperations.size)
        assertEquals(1L, diagnostics.state.value!!.omittedOperations)
    }

    private fun activity(
        version: Int = 1,
        blocked: Boolean = false,
        delay: Int = 150,
    ): ForegroundRuleSnapshot {
        val raw =
            RawSubscriptionParser.parse(
                """{id:1,name:'Test',version:$version,apps:[{id:'app.test',groups:[
            {key:1,name:'Current',scopeKeys:[2],actionDelay:$delay,rules:[{key:1,matches:'*',actionCdKey:2},
                {key:3,activityIds:['Unused'],matches:'[text="UNUSED_DEFINITION_${"x".repeat(10000)}"]'}]},
            {key:2,name:'Dependency',activityIds:['Other'],rules:[{key:2,matches:'[text="$version"]'}]}
        ]}]}""",
            )
        val summary =
            RuleSummaryBuilder.build(
                RuleRuntime(),
                listOf(UsedSubsEntry(SubsItem(1, enable = true, order = 0), raw)),
                mapOf("app.test" to AppInfo("app.test", "Test", 1, "1", false, 0, false, 0)),
                emptyList(),
                emptyList(),
                emptyList(),
            )
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
        assertEquals(3, first.candidates.size)
        assertEquals(1, first.selected.size)
        assertEquals(
            listOf("1/2/app.test/2/2"),
            capture.ruleDefinitions.getValue(first.definitions.first()).dependencies,
        )
        assertTrue(capture.ruleSnapshots.last().selected.isEmpty())
        assertTrue(capture.ruleSnapshots.last().blockMatch)
        val restored = Json.decodeFromString<MatchDiagnosticCapture>(Json.encodeToString(capture))
        assertEquals(capture, restored)
        assertEquals(
            1,
            restored.ruleDefinitions.getValue(first.definitions.first()).subscriptionVersion,
        )
        assertTrue(
            restored.ruleDefinitions
                .getValue(first.definitions.first())
                .group
                .containsKey("actionDelay"),
        )
    }

    @Test
    fun definitionsAreDeduplicatedAndChangedRulesKeepTheirOwnReferences() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy())
        diagnostics.captureRules(activity())
        diagnostics.captureRules(activity())
        val first = diagnostics.export(session.id)
        assertEquals(2, first.ruleDefinitions.size)
        assertEquals(first.ruleSnapshots[0].definitions, first.ruleSnapshots[1].definitions)
        assertTrue(first.ruleDefinitions.values.all { "rules" !in it.group })
        assertFalse(Json.encodeToString(first).contains("UNUSED_DEFINITION"))
        assertTrue(Json.encodeToString(first).length < 10000)
        val selected =
            first.ruleDefinitions.getValue(first.ruleSnapshots.first().definitions.first())
        assertEquals("150", selected.effective.getValue("actionDelay").toString())
        diagnostics.captureRules(activity(2))
        val updated = diagnostics.export(session.id)
        assertEquals(4, updated.ruleDefinitions.size)
        assertTrue(updated.ruleSnapshots.last().definitions.none { it in first.ruleDefinitions })
        assertEquals(
            setOf(1, 2),
            updated.ruleDefinitions.values.map { it.subscriptionVersion }.toSet(),
        )
        assertEquals(
            updated,
            Json.decodeFromString<MatchDiagnosticCapture>(Json.encodeToString(updated)),
        )
        repeat(16) { diagnostics.captureRules(activity(3, blocked = true)) }
        val bounded = diagnostics.export(session.id)
        assertTrue(bounded.ruleDefinitions.isEmpty())
        assertTrue(
            bounded.ruleSnapshots.all { it.definitions.isEmpty() && it.candidates.isNotEmpty() },
        )
    }

    @Test
    fun localChangesWithinSameSubscriptionVersionRetainDistinctEffectiveParameters() {
        val diagnostics = MatchDiagnostics()
        val session = diagnostics.start(MatchTimingPolicy())
        diagnostics.captureRules(activity(delay = 150))
        diagnostics.captureRules(activity(delay = 300))
        val capture = diagnostics.export(session.id)
        val selected =
            capture.ruleSnapshots.map { capture.ruleDefinitions.getValue(it.definitions.first()) }
        assertEquals(listOf(1, 1), selected.map { it.subscriptionVersion })
        assertEquals(
            listOf("150", "300"),
            selected.map { it.effective.getValue("actionDelay").toString() },
        )
        assertEquals(3, capture.ruleDefinitions.size)
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
        for (availability in
            listOf(RuleAvailability.Loading, RuleAvailability.Failed, RuleAvailability.Ready)) {
            val snapshot =
                ForegroundRuleSnapshot(blockMatch = false, ruleAvailability = availability)
            diagnostics.captureRules(snapshot)
            diagnostics.entry("query_request", snapshot.emptyReason)
        }
        val capture = diagnostics.export(session.id)
        assertEquals(
            listOf("rules_loading", "rules_load_failed", "no_rules_for_activity"),
            capture.entries.map { it.reason },
        )
        assertTrue(capture.ruleSnapshots.all { it.selected.isEmpty() })
        assertEquals(3, capture.ruleSnapshots.map { it.availability }.distinct().size)
    }

    @Test
    fun sessionStartedWhileBlockedExportsRulesWithoutWaitingForAQuery() = runTest {
        val coordinator =
            MatchCoordinator(this, StandardTestDispatcher(testScheduler), onFailure = { throw it })
        try {
            coordinator.updateRules(activity(blocked = true))
            assertEquals(null, coordinator.diagnostics.state.value)
            val session = coordinator.startDiagnostics(false, null)
            val capture = coordinator.diagnostics.export(session.id)
            assertTrue(capture.ruleSnapshots.single().blockMatch)
            assertEquals("service_unavailable", capture.entries.single().reason)
            val service = Any()
            coordinator.request(service) { error("Detached service must not execute") }
            runCurrent()
            assertFalse(coordinator.snapshot().queryPending)
            assertEquals(2L, coordinator.diagnostics.export(session.id).entries.single().count)
        } finally {
            coordinator.close()
        }
    }
}
