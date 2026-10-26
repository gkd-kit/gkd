package li.gkd.app.matching.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import li.gkd.app.development.matching.MatchSimulatedNode
import li.gkd.app.development.matching.SimulatedNodeAdapter
import li.gkd.app.matching.ForegroundRuleSnapshot
import li.gkd.app.matching.MatchActionOutcome
import li.gkd.app.matching.MatchCoordinator
import li.gkd.app.matching.MatchScheduleCheckpoint
import li.gkd.app.matching.MatchScheduleCommand
import li.gkd.app.matching.MatchScheduleEffect
import li.gkd.app.matching.MatchScheduleMachine
import li.gkd.app.matching.MatchScheduleRule
import li.gkd.app.rule.RuleRuntime
import li.gkd.app.rule.RuleDelayKind
import li.gkd.app.matching.replay.MatchCaptureReplay
import li.gkd.app.matching.replay.replay
import li.gkd.app.matching.retainUnaffectedExecution
import li.gkd.app.model.ActionResult
import li.gkd.app.model.AppInfo
import li.gkd.app.model.GkdAction
import li.gkd.app.rule.RuleStatus
import li.gkd.app.rule.RuleSummary
import li.gkd.app.rule.RuleSummaryBuilder
import li.gkd.app.rule.TopActivity
import li.gkd.app.subscription.RawSubscriptionParser
import li.gkd.app.subscription.PositionError
import li.gkd.app.subscription.UsedSubsEntry
import li.gkd.db.SubsItem
import li.gkd.selector.MatchOptions
import li.gkd.selector.Selector

class MatchSystemRecordingTest {
    /** A returned coordinate failure remains a failure, with its inputs intact, through JSON and replay. */
    @Test
    fun coordinateFailureSurvivesRecordingExportAndReplay() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler),
            nowMillis = { testScheduler.currentTime }, onFailure = { throw it })
        val key = Any()
        val failure = ActionResult("clickCenter", false, coordinateError = PositionError(
            "position.x", "DIVISION_BY_ZERO", "100/width", 0, 9, inputs = mapOf("width" to 0.0),
        ))
        try {
            coordinator.attach(key)
            val session = coordinator.startDiagnostics(1000, false, null, recordSystem = true)
            coordinator.execute(key) {
                performAction(GkdAction("*", action = "clickCenter")) { failure }
            }
            runCurrent()
            coordinator.stopDiagnostics(session.id)
            val recording = coordinator.diagnostics.export(session.id).systemRecording!!.roundTrip()
            val replayed = recording.replay()
            assertTrue(replayed.verified)
            assertEquals(0, replayed.commitsAccepted)
            assertEquals("returned", replayed.actions.single().outcome!!.kind)
            assertEquals(failure, replayed.actions.single().outcome!!.result)
        } finally { coordinator.close() }
    }

    @Test
    fun manualHttpActionUsesTheSameQueryAndActionRecordingBoundary() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler), nowMillis = { testScheduler.currentTime }, onFailure = { throw it })
        val key = Any()
        try {
            coordinator.attach(key)
            val session = coordinator.startDiagnostics(1000, false, null, recordSystem = true)
            val root = MatchSimulatedNode("Root", children = listOf(MatchSimulatedNode("Button", mapOf("text" to JsonPrimitive("target")))))
            for (selectorText in listOf("[text=\"target\"][depth=1]", "[depth=0]")) {
                coordinator.execute(key) {
                    val adapter = SimulatedNodeAdapter(root, checkNotNull(currentCoroutineContext()[Job]))
                    val selector = Selector.compile(selectorText).value
                    val node = match(null) { recordManualQuery(selector, MatchOptions(), root, { root }, adapter, coordinator.diagnostics, session, id) }
                    checkNotNull(node)
                    performAction(GkdAction(selectorText, action = "click")) { ActionResult("click", true) }
                }
                runCurrent()
            }
            coordinator.stopDiagnostics(session.id)
            val capture = coordinator.diagnostics.export(session.id)
            val result = MatchCaptureReplay(capture.systemRecording!!.roundTrip(), capture.queryRecordings).replay()
            assertTrue(result.verified)
            assertEquals(2, result.queries.size)
            assertEquals(2, result.system.actions.size)
            assertEquals("[depth=0]", result.system.actions.last().action.selector)
            assertFalse(MatchCaptureReplay(capture.systemRecording, emptyList()).replay().verified)
        } finally { coordinator.close() }
    }

    @Test
    fun manualCancellationBeforeWorkerStartsAndImmediateCompletionDoNotLoseTheResult() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler), nowMillis = { testScheduler.currentTime }, onFailure = { throw it })
        val key = Any()
        try {
            coordinator.attach(key)
            val session = coordinator.startDiagnostics(1000, false, null, recordSystem = true)
            var ran = false
            val caller = launch(UnconfinedTestDispatcher(testScheduler)) { coordinator.execute(key) { ran = true } }
            caller.cancel(); runCurrent()
            assertFalse(ran)
            assertTrue(caller.isCompleted)
            val value = CompletableDeferred<Int>()
            launch { value.complete(coordinator.execute(key) { 42 }) }
            runCurrent()
            assertEquals(42, value.await())
            coordinator.stopDiagnostics(session.id)
            assertTrue(coordinator.diagnostics.export(session.id).systemRecording!!.replay().verified)
        } finally { coordinator.close() }
    }

    @Test
    fun exactRuleDependencyCancellationAndLateCommitAreReevaluated() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler), nowMillis = { testScheduler.currentTime }, onFailure = { throw it })
        val key = Any()
        val runtime = RuleRuntime()
        val old = summary(runtime, "{key:1,matches:'*'},{key:2,matches:'*'}")
        val activity = old.activity()
        var run = 0L
        try {
            coordinator.attach(key); coordinator.updateRules(activity)
            val session = coordinator.startDiagnostics(1000, false, null, recordSystem = true)
            coordinator.request(key) { run = id; trackRules(activity); selectRule(old.rules()[0]); awaitCancellation() }
            runCurrent()
            val fresh = summary(runtime, "{key:1,matches:'*'},{key:2,matches:'[text=\"unrelated\"]'}")
            fresh.retainUnaffectedExecution(old)
            coordinator.updateRules(fresh.activity())
            assertTrue(coordinator.canCommit(run, fresh.rules()[0]))
            coordinator.updateRules(summary(runtime, "{key:1,matches:'[text=\"changed\"]'}").activity())
            assertFalse(coordinator.canCommit(run, old.rules()[0]))
            runCurrent(); coordinator.stopDiagnostics(session.id)
            val tape = coordinator.diagnostics.export(session.id).systemRecording!!.roundTrip()
            assertEquals(1, tape.replay().commitsAccepted)
            assertTrue(tape.replay().verified)
            assertEquals(1, tape.steps.flatMap { it.effects }.count { it.type == "cancel" })
        } finally { coordinator.close() }
    }

    private fun summary(runtime: RuleRuntime, rules: String) = RuleSummaryBuilder.build(runtime,
        listOf(UsedSubsEntry(SubsItem(1, enable = true, order = 0), RawSubscriptionParser.parse(
            """{id:1,name:'Test',version:1,apps:[{id:'app.test',groups:[{key:1,name:'Test',rules:[$rules]}]}]}"""))),
        mapOf("app.test" to AppInfo("app.test", "Test", 1, "1", false, 0, false, 0)), emptyList(), emptyList(), emptyList())
    private fun RuleSummary.activity() = ForegroundRuleSnapshot(TopActivity("app.test"), false, this)
    private fun RuleSummary.rules() = appIdToRules.getValue("app.test")
    private fun MatchSystemRecording.roundTrip() = Json.decodeFromString<MatchSystemRecording>(Json.encodeToString(this))

    @Test
    fun cancelledActionRetainsLaneThroughCleanupAndRecordingStop() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler), nowMillis = { testScheduler.currentTime }, onFailure = { throw it })
        val key = Any()
        val release = CompletableDeferred<Unit>()
        val rule = summary(RuleRuntime(), "{key:1,matches:'*'}").rules().first()
        try {
            coordinator.attach(key)
            val session = coordinator.startDiagnostics(60_000, false, null, recordSystem = true)
            coordinator.request(key) { performAction(rule) { try { awaitCancellation() } finally { withContext(NonCancellable) { release.await() } } } }
            runCurrent()
            coordinator.invalidate("screen_off")
            repeat(100) { index -> coordinator.request(key) { assertEquals(99, index) } }
            coordinator.stopDiagnostics(session.id)
            runCurrent()
            val pending = coordinator.diagnostics.export(session.id)
            assertTrue(pending.session.systemFinalizing)
            assertFalse(pending.systemRecording!!.replay().verified)
            assertFailsWith<IllegalStateException> { coordinator.startDiagnostics(100, false, null) }
            advanceTimeBy(250)
            release.complete(Unit)
            runCurrent()
            val tape = coordinator.diagnostics.export(session.id).systemRecording!!.roundTrip()
            val result = tape.replay()
            assertTrue(result.verified)
            assertEquals(2, result.runsStarted.size)
            val action = result.actions.single()
            assertEquals("cancelled", action.outcome?.kind)
            assertEquals(250L, action.endedAtMillis!! - action.cancelledAtMillis!!)
            val finish = tape.steps.indexOfFirst { it.command?.type == "finish" }
            val end = tape.steps.indexOfFirst { it.command?.type == "action_end" }
            assertTrue(end < finish)
            assertEquals("start", tape.steps[finish].effects.single().type)
        } finally { release.complete(Unit); coordinator.close() }
    }

    @Test
    fun cleanupFailureIsAnActionFailureAndNotAFalseCancellationSuccess() = runTest {
        val failures = mutableListOf<Throwable>()
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler), nowMillis = { testScheduler.currentTime }, onFailure = { failures += it })
        val key = Any()
        val rule = summary(RuleRuntime(), "{key:1,matches:'*'}").rules().first()
        var callerFailure: Throwable? = null
        try {
            coordinator.attach(key)
            val session = coordinator.startDiagnostics(60_000, false, null, recordSystem = true)
            val caller = launch {
                try { coordinator.execute(key) { performAction(rule) {
                    try { awaitCancellation() } finally { withContext(NonCancellable) { delay(50); error("release unconfirmed") } }
                } } } catch (error: Throwable) { callerFailure = error }
            }
            runCurrent(); caller.cancel(); runCurrent(); advanceTimeBy(50); runCurrent()
            coordinator.stopDiagnostics(session.id)
            val result = coordinator.diagnostics.export(session.id).systemRecording!!.roundTrip().replay()
            assertTrue(result.verified)
            assertEquals("failed", result.actions.single().outcome?.kind)
            assertEquals("release unconfirmed", result.actions.single().outcome?.message)
            assertTrue(callerFailure is IllegalStateException)
            assertEquals(1, failures.size)
        } finally { coordinator.close() }
    }

    @Test
    fun captureCanBeginInsideAnActionWithoutInventingItsStart() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler), nowMillis = { testScheduler.currentTime }, onFailure = { throw it })
        val key = Any()
        val release = CompletableDeferred<Unit>()
        val rule = summary(RuleRuntime(), "{key:1,matches:'*'}").rules().first()
        try {
            coordinator.attach(key)
            coordinator.request(key) { performAction(rule) { release.await(); ActionResult("swipe", true) } }
            runCurrent(); advanceTimeBy(100)
            val session = coordinator.startDiagnostics(60_000, false, null, recordSystem = true)
            release.complete(Unit); runCurrent()
            coordinator.stopDiagnostics(session.id)
            val result = coordinator.diagnostics.export(session.id).systemRecording!!.roundTrip().replay()
            assertTrue(result.verified)
            assertTrue(result.actions.single().beganBeforeRecording)
            assertEquals(0, result.runsStarted.size)
            assertEquals(true, result.actions.single().outcome?.result?.result)
        } finally { release.complete(Unit); coordinator.close() }
    }

    @Test
    fun serviceChangesRuleMigrationAndPolicyTimersReplayWithTheSameWakeDecisions() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler), nowMillis = { testScheduler.currentTime }, onFailure = { throw it })
        // Equal-valued keys still represent distinct service instances.
        data class Service(val value: Int)
        val first = Service(1); val second = Service(1)
        val runtime = RuleRuntime()
        val old = summary(runtime, "{key:1,matches:'*'},{key:2,matches:'*'}")
        val ran = mutableListOf<String>()
        try {
            coordinator.attach(first)
            val session = coordinator.startDiagnostics(60_000, false, null, recordSystem = true)
            coordinator.updateRules(old.activity())
            coordinator.scheduleRule(first, old.rules()[0], RuleDelayKind.Match, 100) { ran += "retained" }
            coordinator.scheduleRule(first, old.rules()[1], RuleDelayKind.Action, 200) { error("obsolete deadline") }
            val fresh = summary(runtime, "{key:1,matches:'*'},{key:2,matches:'[text=\"new\"]'}")
            fresh.retainUnaffectedExecution(old)
            coordinator.updateRules(fresh.activity())
            coordinator.scheduleRule(first, fresh.rules()[0], RuleDelayKind.Match, 200) { error("duplicate deadline") }
            advanceTimeBy(100); runCurrent()
            coordinator.scheduleFollowUp(first) { ran += "follow" }
            coordinator.updatePolicy(coordinator.policy.value.copy(followUpMillis = 10))
            advanceTimeBy(10); runCurrent()
            coordinator.scheduleFollowUp(first) { error("old service") }
            coordinator.attach(second)
            coordinator.request(first) { error("stale service") }
            coordinator.request(second) { ran += "second" }
            runCurrent(); coordinator.stopDiagnostics(session.id)
            assertEquals(listOf("retained", "follow", "second"), ran)
            val tape = coordinator.diagnostics.export(session.id).systemRecording!!.roundTrip()
            assertTrue(tape.replay().verified)
            assertEquals(3, tape.replay().runsStarted.size)
            val wake = tape.steps.first { it.command?.type == "wake" }
            assertFailsWith<IllegalArgumentException> { tape.copy(steps = tape.steps.map { if (it == wake) it.copy(timeMillis = 99) else it }).replay() }
        } finally { coordinator.close() }
    }

    @Test
    fun tamperedOrMissingStepsAndCapacityLimitsCannotReportVerified() {
        val machine = MatchScheduleMachine()
        val recorder = MatchSystemRecorder(machine.state, 0)
        fun send(command: MatchScheduleCommand, time: Long) { val effects = machine.apply(command, time); recorder.append(command, time, effects, machine.state) }
        send(MatchScheduleCommand("attach", service = 1), 1)
        send(MatchScheduleCommand("request", service = 1, task = 1), 2)
        send(MatchScheduleCommand("finish", run = 1), 3)
        recorder.stop(4, machine.state)
        val tape = recorder.export().roundTrip()
        assertTrue(tape.replay().verified)
        assertFailsWith<IllegalArgumentException> { tape.copy(steps = tape.steps.drop(1)).replay() }
        assertFailsWith<IllegalArgumentException> { tape.copy(steps = tape.steps.map { if (it.sequence == 2L) it.copy(effects = emptyList()) else it }).replay() }
        assertFailsWith<IllegalArgumentException> { tape.copy(final = tape.final.copy(generation = 99)).replay() }
        assertFailsWith<IllegalArgumentException> { tape.copy(version = 99).replay() }
        val limited = MatchSystemRecorder(tape.initial, 0, capacity = 1)
        tape.steps.forEach { limited.append(requireNotNull(it.command), it.timeMillis, it.effects, tape.final) }
        limited.stop(4, tape.final)
        assertFalse(limited.export().replay().verified)
        assertEquals("system_recording_limit", limited.export().incomplete)
    }

    @Test
    fun cancelledActionCannotReleaseTheLaneBeforeItsPlatformCompletion() {
        val machine = MatchScheduleMachine()
        val rule = MatchScheduleRule(1, 2, "rule", false)
        machine.apply(MatchScheduleCommand("attach", service = 1), 0)
        machine.apply(MatchScheduleCommand("request", service = 1, task = 1), 0)
        machine.apply(MatchScheduleCommand("action_start", run = 1, rule = rule, action = li.gkd.app.model.GkdAction("", action = "swipe")), 1)
        machine.apply(MatchScheduleCommand("cancel", run = 1, reason = "page"), 2)
        machine.apply(MatchScheduleCommand("request", service = 1, task = 2), 3)
        assertFailsWith<IllegalArgumentException> { machine.apply(MatchScheduleCommand("finish", run = 1), 4) }
        machine.apply(MatchScheduleCommand("action_end", run = 1, outcome = MatchActionOutcome("cancelled")), 5)
        assertEquals(1L, machine.state.active?.id)
        val next = machine.apply(MatchScheduleCommand("finish", run = 1), 6).single()
        assertEquals(2L, next.run?.id)
    }

    @Test
    fun sharedCountersCooldownPrerequisitesAndHotReplacementReplayAcrossRuleInstances() {
        val recorder = MatchSystemRecorder(MatchScheduleCheckpoint(), 0)
        var time = 10_000L
        val runtime = RuleRuntime(now = { time }, observer = { recorder })
        val raw = "{key:1,matches:'*',actionMaximum:1,actionCd:100},{key:2,matches:'*',actionMaximumKey:1,actionCdKey:1},{key:3,matches:'*',preKeys:[1],actionDelay:50}"
        val old = summary(runtime, raw)
        val rules = old.rules()
        assertEquals(RuleStatus.PrerequisitePending, rules[2].status)
        assertEquals(RuleStatus.Ready, rules[0].status)
        rules[0].trigger()
        assertEquals(RuleStatus.ActionLimitReached, rules[1].status)
        assertEquals(RuleStatus.Ready, rules[2].status)
        assertTrue(rules[2].checkDelay())
        assertEquals(RuleStatus.ActionDelay, rules[2].status)
        time += 50
        assertEquals(RuleStatus.Ready, rules[2].status)
        val fresh = summary(runtime, raw)
        fresh.retainUnaffectedExecution(old)
        assertEquals(RuleStatus.ActionLimitReached, fresh.rules()[1].status)
        fresh.rules()[1].resetState(time)
        assertEquals(RuleStatus.Ready, fresh.rules()[0].status)
        recorder.stop(0, MatchScheduleCheckpoint())
        val tape = recorder.export().roundTrip()
        assertTrue(tape.replay().verified)
        val status = tape.steps.first { it.ruleState?.status == "ActionDelay" }
        assertFailsWith<IllegalArgumentException> {
            tape.copy(steps = tape.steps.map { if (it == status) it.copy(ruleState = it.ruleState!!.copy(status = "Ready")) else it }).replay()
        }
        // A recording event cannot mix a rule observation with a scheduler mutation.
        assertFailsWith<IllegalArgumentException> {
            tape.copy(steps = tape.steps.map { if (it == status) it.copy(command = MatchScheduleCommand("close")) else it }).replay()
        }
        assertFailsWith<IllegalArgumentException> {
            tape.copy(steps = tape.steps.map { if (it == status) it.copy(ruleState = null) else it }).replay()
        }
        assertFailsWith<IllegalArgumentException> {
            tape.copy(steps = tape.steps.map { if (it == status) it.copy(effects = listOf(MatchScheduleEffect("arm"))) else it }).replay()
        }
    }

    @Test
    fun stoppedRecorderDoesNotReceiveANewSessionAndOrdinaryDiagnosticsHasNoSystemTape() = runTest {
        val coordinator = MatchCoordinator(this, StandardTestDispatcher(testScheduler), nowMillis = { testScheduler.currentTime }, onFailure = { throw it })
        val key = Any()
        try {
            coordinator.attach(key)
            val first = coordinator.startDiagnostics(1000, false, null, recordSystem = true)
            coordinator.request(key) { }; runCurrent()
            coordinator.stopDiagnostics(first.id)
            val recorder = coordinator.diagnostics.systemRecorder!!
            val saved = recorder.export()
            val ordinary = coordinator.startDiagnostics(1000, false, null)
            coordinator.request(key) { }; runCurrent()
            assertEquals(null, coordinator.diagnostics.export(ordinary.id).systemRecording)
            assertEquals(saved, recorder.export())
        } finally { coordinator.close() }
    }
}
