package li.gkd.app.matching.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import li.gkd.app.matching.diagnostics.MatchDiagnostics

class MatchInputsTest {
    private fun MatchInputReducer.acceptRecord(record: MatchInputRecord) =
        accept(record.input, record.policy, record.receivedAtMillis, record.sourceTimeMillis)

    @Test
    fun returningToTheSameActivityInvalidatesOldReadsBeforeNodeConfirmation() {
        for (source in listOf(ForegroundSource.Accessibility, ForegroundSource.TaskStack)) {
            val app = ForegroundIdentity("video", "Splash")
            val reducer = MatchInputReducer(app)
            val policy = MatchTimingPolicy()
            fun observe(sequence: Long, from: ForegroundSource, identity: ForegroundIdentity) =
                reducer.acceptRecord(
                    MatchInputRecord(
                        sequence,
                        sequence * 100,
                        null,
                        policy,
                        MatchInput.Foreground(from, identity),
                    ),
                )
            assertTrue(observe(1, source, ForegroundIdentity("launcher", "Home")).cancelWork)
            assertTrue(observe(2, source, app).cancelWork)
            assertFalse(observe(3, source, app).cancelWork)
            val confirmed = observe(4, ForegroundSource.Nodes, ForegroundIdentity("video"))
            assertTrue(confirmed.cancelWork)
            assertTrue(checkNotNull(confirmed.foreground).appReentered)
        }
    }

    @Test
    fun sameAppActivityTransitionAndLateTaskTimeout() {
        val initial = ForegroundIdentity("app.a", "First")
        val reducer = MatchInputReducer(initial)
        val policy = MatchTimingPolicy(foregroundRetryMillis = 10, foregroundWaitMillis = 100)
        fun record(
            sequence: Long,
            time: Long,
            source: ForegroundSource,
            app: String,
            activity: String? = null,
        ) =
            MatchInputRecord(
                sequence,
                time,
                null,
                policy,
                MatchInput.Foreground(source, ForegroundIdentity(app, activity)),
            )
        val records =
            listOf(
                record(1, 10, ForegroundSource.Accessibility, "app.a", "Second"),
                record(2, 20, ForegroundSource.Nodes, "app.a"),
                record(3, 30, ForegroundSource.TaskStack, "old.app", "Old"),
                record(4, 140, ForegroundSource.Nodes, "app.a"),
            )
        val results = records.map { reducer.acceptRecord(it) }
        assertEquals(ForegroundIdentity("app.a", "Second"), results[1].foreground?.confirmed)
        assertEquals("stale_observation_discarded", results.last().reason)
        assertEquals(ForegroundIdentity("app.a", "Second"), results.last().foreground?.confirmed)
    }

    @Test
    fun diagnosticsCaptureCheckpointOnlyWhenEnabledAndMarkTruncation() {
        var clock = 0L
        val diagnostics = MatchDiagnostics()
        val stream =
            MatchInputStream(
                ForegroundIdentity("app.a"),
                { clock++ },
                { diagnostics.enabled },
                diagnostics::recordInput,
            )
        stream.accept(MatchInput.ServiceChanged, MatchTimingPolicy())
        assertEquals(null, diagnostics.state.value)
        val session = diagnostics.start(MatchTimingPolicy(), capacity = 10)
        repeat(12) { stream.accept(MatchInput.Screen(true), MatchTimingPolicy()) }
        val (state, events) = diagnostics.export(session.id)
        assertEquals(1L, state.inputCheckpoint?.sequence)
        assertEquals(2L, state.dropped)
        assertEquals(10, events.size)
        diagnostics.stop(session.id)
        stream.accept(MatchInput.Screen(false), MatchTimingPolicy())
        assertEquals(events, diagnostics.export(session.id).events)
    }

    private val context = MatchEventContext(true, "self", true, "ime", "app.a", 10000, 10000)

    @Test
    fun platformEventPathPreservesDecisionsAndCaptureAcrossDiagnosticToggles() {
        var now = 1000L
        var capturing = false
        val expected =
            mutableListOf<Triple<MatchInputCheckpoint, MatchInputRecord, MatchInputDecision>>()
        val actual =
            mutableListOf<Triple<MatchInputCheckpoint, MatchInputRecord, MatchInputDecision>>()
        val reference =
            MatchInputStream(
                ForegroundIdentity("app.a"),
                { now },
                { true },
                { before, input, decision ->
                    expected += Triple(before, input, decision)
                },
            )
        val platform =
            MatchInputStream(
                ForegroundIdentity("app.a"),
                { now },
                { capturing },
                { before, input, decision ->
                    assertTrue(capturing, "Disabled diagnostics must not emit records")
                    actual += Triple(before, input, decision)
                },
            )
        val inputs =
            listOf(
                event("self") to 1L,
                event("ime") to 2L,
                event().copy(primaryDisplay = false) to 3L,
                event(context = context.copy(interactive = false)) to 4L,
                event() to 100L,
                event() to 110L,
                event().copy(key = MatchEventKey(MatchEventKind.WindowState, "app.a", "Window")) to
                    90L,
                event().copy(key = MatchEventKey(MatchEventKind.WindowState, "app.a", "Window")) to
                    120L,
                MatchInput.ServiceChanged to null,
                event() to 1L,
                MatchInput.Foreground(
                    ForegroundSource.TaskStack,
                    ForegroundIdentity("app.b", "B"),
                ) to null,
                MatchInput.Screen(false) to null,
            )
        val policy = MatchTimingPolicy()
        inputs.forEachIndexed { index, (input, sourceTime) ->
            now += 10
            capturing = index in 4..6 || index >= 9
            val decision = reference.accept(input, policy, sourceTime)
            if (input is MatchInput.Accessibility) {
                val c = input.context
                assertEquals(
                    decision.accepted,
                    platform.acceptAccessibility(
                        input.key,
                        c.interactive,
                        c.ownAppId,
                        c.ownVisible,
                        c.imeAppId,
                        c.confirmedAppId,
                        c.pageAgeMillis,
                        c.actionAgeMillis,
                        input.primaryDisplay,
                        input.recordCount,
                        input.action,
                        input.fullScreen,
                        policy,
                        sourceTime,
                    ),
                )
            } else assertEquals(decision, platform.accept(input, policy, sourceTime))
            assertEquals(reference.foregroundSnapshot(), platform.foregroundSnapshot())
            if (capturing) assertEquals(expected.last(), actual.last())
        }
        assertEquals(
            listOf(
                "own_app_event",
                "input_method_event",
                "secondary_display",
                "screen_off_content",
                "event_accepted",
                "content_merged",
                "stale_accessibility_event",
                "event_accepted",
                "service_changed",
                "event_accepted",
                "awaiting_nodes",
                "screen_off",
            ),
            expected.map { it.third.reason },
        )
        assertEquals(listOf(5L, 6L, 7L, 10L, 11L, 12L), actual.map { it.second.sequence })
        val records = actual.map { it.second }
        assertEquals(
            records,
            Json.decodeFromString<List<MatchInputRecord>>(Json.encodeToString(records)),
        )
    }

    private fun event(app: String = "app.a", context: MatchEventContext = this.context) =
        MatchInput.Accessibility(MatchEventKey(MatchEventKind.Content, app, "Window"), context)

    @Test
    fun diagnosticSnapshotKeepsPreCaptureTimingAndPendingForegroundWithoutRestoringExecution() {
        var now = 1000L
        val diagnostics = MatchDiagnostics()
        val stream =
            MatchInputStream(
                ForegroundIdentity("app.a"),
                { now },
                { diagnostics.enabled },
                diagnostics::recordInput,
            )
        val policy = MatchTimingPolicy()
        now += 10
        stream.accept(event(), policy, 500)
        now += 10
        stream.accept(
            MatchInput.Foreground(ForegroundSource.TaskStack, ForegroundIdentity("app.b", "B")),
            policy,
        )
        val session = diagnostics.start(policy)
        now += 10
        stream.accept(MatchInput.Screen(true), policy)
        val before = checkNotNull(diagnostics.export(session.id).session.inputCheckpoint)
        assertEquals(2L, before.sequence)
        assertEquals(20L, before.receivedAtMillis)
        assertEquals(10L, before.lastContentAt)
        assertEquals(500L, before.lastEventSourceTime)
        assertEquals(ForegroundIdentity("app.b", "B"), before.foreground.pending)
        assertEquals(
            before,
            Json.decodeFromString<MatchInputCheckpoint>(Json.encodeToString(before)),
        )
        now += 10
        stream.accept(
            MatchInput.Foreground(ForegroundSource.Nodes, ForegroundIdentity("app.b")),
            policy,
        )
        assertEquals("app.b", stream.foregroundSnapshot().confirmed.appId)
        assertEquals(before, diagnostics.export(session.id).session.inputCheckpoint)
        assertEquals("app.a", before.foreground.confirmed.appId)
    }

    @Test
    fun diagnosticsPreserveTimingChangesAndClockDomains() {
        var now = 1000L
        val records = mutableListOf<MatchInputRecord>()
        val expected = mutableListOf<MatchInputDecision>()
        val stream =
            MatchInputStream(
                ForegroundIdentity("app.a"),
                { now },
                { true },
                { _, record, result ->
                    records += record
                    expected += result
                },
            )
        var policy = MatchTimingPolicy()
        fun send(input: MatchInput, time: Long? = null) {
            stream.accept(input, policy, time)
        }
        send(event(), 100)
        now += 20
        send(event(), 110)
        policy = policy.copy(contentMergeMillis = 10)
        send(event(), 120)
        now += 20
        send(event(), 90)
        send(
            MatchInput.Foreground(ForegroundSource.TaskStack, ForegroundIdentity("app.b", "B")),
            999999,
        )
        send(MatchInput.Foreground(ForegroundSource.Nodes, ForegroundIdentity("app.b")), -50)
        send(MatchInput.Screen(false))
        send(event(context = context.copy(interactive = false)), 130)
        send(MatchInput.ServiceChanged)
        send(event(), 1)
        assertEquals(
            records,
            Json.decodeFromString<List<MatchInputRecord>>(Json.encodeToString(records)),
        )
        assertEquals(
            listOf(
                "event_accepted",
                "content_merged",
                "event_accepted",
                "stale_accessibility_event",
                "awaiting_nodes",
                "nodes_confirmed",
                "screen_off",
                "screen_off_content",
                "service_changed",
                "event_accepted",
            ),
            expected.map { it.reason },
        )
        assertTrue(expected[6].cancelWork)
        assertTrue(expected[4].cancelWork)
    }

    @Test
    fun pendingForegroundAndOverlayConfirmationSurviveFurtherInputs() {
        val reducer = MatchInputReducer(ForegroundIdentity("app.a"))
        val policy = MatchTimingPolicy()
        reducer.acceptRecord(
            MatchInputRecord(
                1,
                10,
                null,
                policy,
                MatchInput.Foreground(ForegroundSource.TaskStack, ForegroundIdentity("app.a", "A")),
            ),
        )
        reducer.acceptRecord(
            MatchInputRecord(
                2,
                20,
                null,
                policy,
                MatchInput.Foreground(ForegroundSource.Nodes, ForegroundIdentity("overlay")),
            ),
        )
        val remaining =
            listOf(
                MatchInputRecord(
                    3,
                    120,
                    null,
                    policy,
                    MatchInput.Foreground(ForegroundSource.Nodes, ForegroundIdentity("overlay")),
                ),
                MatchInputRecord(
                    4,
                    130,
                    null,
                    policy,
                    MatchInput.Foreground(
                        ForegroundSource.TaskStack,
                        ForegroundIdentity("app.a", "A"),
                    ),
                ),
            )
        val decisions = remaining.map { reducer.acceptRecord(it) }
        assertEquals("overlay", reducer.foregroundSnapshot().confirmed.appId)
        assertEquals(
            ForegroundDecisionReason.TaskBelowConfirmedSurface,
            reducer.foregroundSnapshot().reason,
        )
        assertFalse(decisions.last().cancelWork)
    }

    @Test
    fun platformFiltersAndBoundedBatchingUseTheSameCommonInputs() {
        val reducer = MatchInputReducer(ForegroundIdentity("app.a"))
        val inputs = listOf(event("self"), event("ime"), event().copy(primaryDisplay = false))
        val results = inputs.mapIndexed { i, input ->
            reducer.acceptRecord(
                MatchInputRecord(i + 1L, i.toLong(), null, MatchTimingPolicy(), input),
            )
        }
        assertEquals(
            listOf("own_app_event", "input_method_event", "secondary_display"),
            results.map { it.reason },
        )
        assertTrue(results.none { it.accepted })
        val dropped = mutableListOf<MatchInput.Accessibility>()
        val overflow = mutableListOf<Pair<MatchInput.Accessibility, Int>>()
        val queue =
            MatchEventQueue<MatchInput.Accessibility>(3, { it.key }, dropped::add) { event, capacity
                ->
                overflow += event to capacity
            }
        val a = event()
        val b = event()
        val c = event()
        val d = event("app.b")
        listOf(a, b, c, d).forEach(queue::offer)
        assertEquals(listOf(a), dropped)
        assertTrue(queue.takeBatch(a).isEmpty())
        assertEquals(listOf(b, c), queue.takeBatch(b))
        queue.clear()
        assertEquals(listOf(a, d), dropped)
        assertEquals(
            listOf(a to 3),
            overflow,
            "Cleanup is not an overflow; dropped references must be released once",
        )
        assertFalse(queue.isNotEmpty())
    }
}
