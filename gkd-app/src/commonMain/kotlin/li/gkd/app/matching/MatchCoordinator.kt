package li.gkd.app.matching

import li.gkd.app.util.monotonicTimeMillis
import java.util.WeakHashMap
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.matching.diagnostics.MatchActionOperation
import li.gkd.app.matching.diagnostics.MatchDiagnosticState
import li.gkd.app.matching.diagnostics.diagnosticIndex
import li.gkd.app.matching.diagnostics.MatchDiagnostics
import li.gkd.app.matching.diagnostics.MatchStage
import li.gkd.app.matching.diagnostics.MatchSystemRecorder
import li.gkd.app.matching.input.MatchTimingPolicy
import li.gkd.app.rule.RuleDelayKind
import li.gkd.app.model.ActionResult
import li.gkd.app.model.GkdAction
import li.gkd.app.rule.ResolvedRule

class MatchCoordinator(
    parentScope: CoroutineScope,
    private val workerContext: CoroutineContext,
    diagnostics: MatchDiagnostics? = null,
    private val nowMillis: () -> Long = ::monotonicTimeMillis,
    private val onDiagnosticCapture: (li.gkd.app.matching.diagnostics.MatchDiagnosticCapture) -> Unit = {},
    private val foregroundSnapshot: () -> li.gkd.app.matching.diagnostics.ForegroundDebugState? = { null },
    private val onFailure: (Throwable) -> Unit,
) : AutoCloseable {
    val diagnostics = diagnostics ?: MatchDiagnostics(onStoppedCapture = onDiagnosticCapture)
    private val lock = Any()
    private val owner = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + owner)
    private val machine = MatchScheduleMachine()
    private var diagnosticOwner: Job? = null
    private var pendingDiagnosticSave = false
    private var stoppedDiagnosticOwner: Job? = null
    private var diagnosticPolicy: MatchTimingPolicy? = null
    val policy: StateFlow<MatchTimingPolicy>
        field = MutableStateFlow(MatchTimingPolicy())
    private fun dispatch(command: MatchScheduleCommand): List<MatchScheduleEffect> {
        val before = machine.state
        val now = nowMillis()
        val effects = machine.apply(command, now)
        // Record before executing effects: immediate coroutine completion may reenter this lock.
        diagnostics.systemRecorder?.append(command, now, effects, machine.state)
        for (effect in effects) when (effect.type) {
            "start" -> startLocked(requireNotNull(effect.run))
            "cancel" -> {
                if (diagnostics.enabled) diagnostics.record(requireNotNull(effect.run).id, MatchStage.Cancelling, requireNotNull(effect.reason),
                    taskId = effect.run.task, generation = effect.run.generation)
                active?.cancel(CancellationException(effect.reason))
            }
            "arm" -> {
                if (effect.timer != machine.state.timer) continue
                wake?.cancel()
                wake = null
                effect.deadline?.let { deadline ->
                    val timer = scope.launch(start = CoroutineStart.LAZY) {
                        delay((deadline - nowMillis()).coerceAtLeast(0))
                        synchronized(lock) { dispatch(MatchScheduleCommand("wake", timer = effect.timer)) }
                    }
                    wake = timer
                    timer.start()
                }
            }
            "reject" -> diagnostics.entry("coordinator", requireNotNull(effect.reason))
        }
        val state = machine.state
        // New tasks also need cleanup when the command was rejected or deduplicated.
        // Read state after effects, which may synchronously finish or start another run.
        if (command.task != null || before.active?.task != state.active?.task ||
            before.pending != state.pending || before.deadlines !== state.deadlines
        ) {
            tasks.keys.retainAll(buildSet {
                state.active?.let { add(it.task) }
                state.pending?.let { add(it) }
                state.deadlines.forEach { add(it.task) }
            })
        }
        finishDiagnosticCapture()
        return effects
    }
    fun updateDiagnosticPolicy(value: MatchTimingPolicy) = synchronized(lock) {
        check(diagnostics.enabled) { "Start a diagnostic session before changing timing" }
        updatePolicy(value)
    }

    fun snapshot(): MatchEngineSnapshot = synchronized(lock) {
        val state = machine.state
        MatchEngineSnapshot(state.service != null, state.generation, state.active?.id, state.pending != null,
            state.deadlines.minOfOrNull { it.deadline }?.let { (it - nowMillis()).coerceAtLeast(0) },
            state.deadlines.count { it.rule != null }, state.policy, state.active?.cancelling == true)
    }
    private fun startLocked(run: MatchScheduledRun) {
        val task = tasks.getValue(run.task)
        val work = MatchWork(run.id, machine.state.policy, diagnostics,
            onTrackRules = { snapshot -> synchronized(lock) {
                val effects = dispatch(MatchScheduleCommand("track", run = run.id, rules = describe(snapshot)))
                effects.firstOrNull { it.type == "reject" }?.let { throw CancellationException(it.reason) }
                if (diagnostics.enabled) diagnostics.captureRules(snapshot, run.id)
            } },
            onSelectRule = { rule -> synchronized(lock) {
                val effects = dispatch(MatchScheduleCommand("select", run = run.id, rule = describe(rule)))
                effects.firstOrNull { it.type == "reject" }?.let { throw CancellationException(it.reason) }
            } },
            onAction = { rule, action, outcome -> synchronized(lock) {
                if (outcome == null && (machine.state.active?.id != run.id || machine.state.active?.cancelling != false || run.generation != machine.state.generation)) {
                    throw CancellationException("execution_context_changed")
                }
                dispatch(if (outcome == null) MatchScheduleCommand("action_start", run = run.id, rule = rule?.let(::describe) ?: manualRule,
                    action = action ?: requireNotNull(rule).let { GkdAction("", action = it.parameters.action, position = it.parameters.position, swipeArg = it.parameters.swipeArg) })
                else MatchScheduleCommand("action_end", run = run.id, outcome = outcome))
            } },
            onQuery = { rule, outcome -> synchronized(lock) {
                dispatch(MatchScheduleCommand(if (outcome == null) "query_start" else "query_end",
                    run = run.id, rule = rule?.let(::describe) ?: manualRule, queryOutcome = outcome))
            } },
        ) { synchronized(lock) { machine.state.active?.let { it.id == run.id && !it.cancelling && it.generation == machine.state.generation } == true && owner.isActive } }
        val job = scope.launch(workerContext, start = CoroutineStart.LAZY) {
            try {
                work.ensureCurrent()
                if (diagnostics.enabled) diagnostics.record(work.id, MatchStage.Query, "query_started", policy = work.policy,
                    taskId = run.task, generation = run.generation)
                task.block(work)
                work.trace(MatchStage.Completed, "work_completed")
            } catch (error: CancellationException) {
                work.trace(MatchStage.Cancelled, error.message ?: "cancelled")
                throw error
            } catch (error: Exception) {
                work.trace(MatchStage.Failed, error.toString())
                onFailure(error)
            }
        }
        active = job
        task.job = job
        job.invokeOnCompletion { error ->
            task.completed(error)
            synchronized(lock) {
                if (active === job) {
                    active = null
                    dispatch(MatchScheduleCommand("finish", run = run.id))
                }
            }
        }
        job.start()
    }
    suspend fun startDiagnostics(detailed: Boolean, ruleFilter: String?, recordQueries: Boolean = false, recordSystem: Boolean = false): MatchDiagnosticState {
        return synchronized(lock) {
            check(owner.isActive) { "Matching engine is closed" }
            finishDiagnosticCapture()
            check(!pendingDiagnosticSave) { "Previous diagnostic session is waiting for cleanup" }
            val session = diagnostics.start(policy.value, detailed, ruleFilter, recordQueries = recordQueries, recordSystem = recordSystem,
                initialRules = rules?.takeIf { it.ruleAvailability == RuleAvailability.Ready }?.ruleSummary?.diagnosticIndex())
            diagnosticOwner = SupervisorJob(owner)
            if (recordSystem) diagnostics.systemRecorder = MatchSystemRecorder(machine.state, nowMillis())
            rules?.let { diagnostics.captureRules(it) }
            if (binding == null) diagnostics.entry("coordinator", "service_unavailable")
            diagnosticPolicy = policy.value
            session
        }
    }
    /** Requests belong to this session; caller cancellation goes through execute's action cleanup. */
    fun diagnosticScope(id: String): CoroutineScope = synchronized(lock) {
        check(diagnostics.state.value?.let { it.id == id && it.active } == true) { "Diagnostic session is unavailable" }
        CoroutineScope(scope.coroutineContext + checkNotNull(diagnosticOwner))
    }

    fun stopDiagnostics(id: String) {
        val foreground = foregroundSnapshot()
        val stopped = synchronized(lock) {
            if (diagnostics.state.value?.let { it.id == id && it.active } != true) return
            diagnostics.captureRuntime(li.gkd.app.matching.diagnostics.MatchDebugSnapshot(snapshot(), foreground))
            diagnosticPolicy?.let { updatePolicy(it) }
            diagnosticPolicy = null
            diagnostics.systemRecorder?.stop(nowMillis(), machine.state)
            diagnostics.stop(id)
            pendingDiagnosticSave = true
            stoppedDiagnosticOwner = diagnosticOwner
            diagnosticOwner.also { diagnosticOwner = null }
        }
        // Cancellation can run request completion handlers. Never call them under the engine lock.
        stopped?.invokeOnCompletion { synchronized(lock) { finishDiagnosticCapture() } }
        stopped?.cancel(CancellationException("diagnostic_session_stopped"))
        synchronized(lock) {
            if (pendingDiagnosticSave) {
                // Persist the stop boundary even if a platform callback never arrives.
                diagnostics.saveStopped(id, onDiagnosticCapture)
                finishDiagnosticCapture()
            }
        }
    }
    fun exportDiagnostics(id: String): li.gkd.app.matching.diagnostics.MatchDiagnosticCapture {
        val foreground = if (diagnostics.enabled) foregroundSnapshot() else null
        return synchronized(lock) {
            if (diagnostics.state.value?.id == id) diagnostics.captureRuntime(li.gkd.app.matching.diagnostics.MatchDebugSnapshot(snapshot(), foreground))
            diagnostics.export(id)
        }
    }
    private fun finishDiagnosticCapture() {
        if (!pendingDiagnosticSave || stoppedDiagnosticOwner?.isCompleted == false || diagnostics.systemRecorder?.sealed == false) return
        pendingDiagnosticSave = false
        stoppedDiagnosticOwner = null
        diagnostics.saveStopped(checkNotNull(diagnostics.state.value).id, onDiagnosticCapture)
    }

    override fun close() {
        val session = synchronized(lock) {
            dispatch(MatchScheduleCommand("close"))
            binding = null
            diagnostics.state.value?.id
        }
        session?.let(::stopDiagnostics)
        owner.cancel()
    }
}

@kotlinx.serialization.Serializable
data class MatchEngineSnapshot(val attached: Boolean, val generation: Long, val activeRunId: Long?, val queryPending: Boolean,
    val nextWakeMillis: Long?, val ruleDeadlines: Int, val policy: MatchTimingPolicy,
    val cancelling: Boolean = false)

class MatchWork(
    val id: Long, val policy: MatchTimingPolicy, private val diagnostics: MatchDiagnostics,
    private val onTrackRules: (ForegroundRuleSnapshot) -> Unit = {},
    private val onSelectRule: (ResolvedRule) -> Unit = {},
    private val onAction: (ResolvedRule?, GkdAction?, MatchActionOutcome?) -> Unit = { _, _, _ -> },
    private val onQuery: (ResolvedRule?, MatchQueryOutcome?) -> Unit = { _, _ -> },
    private val isCurrent: () -> Boolean,
) {
    fun trackRules(snapshot: ForegroundRuleSnapshot) = onTrackRules(snapshot)
    suspend fun selectRule(rule: ResolvedRule) { onSelectRule(rule); ensureCurrent() }
    suspend fun ensureCurrent() {
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) throw CancellationException("execution_context_changed")
    }
    suspend fun performAction(rule: ResolvedRule, block: suspend () -> ActionResult): ActionResult = performAction(rule, null, block)
    suspend fun performAction(action: GkdAction, block: suspend () -> ActionResult): ActionResult = performAction(null, action, block)
    private suspend fun performAction(rule: ResolvedRule?, action: GkdAction?, block: suspend () -> ActionResult): ActionResult {
        ensureCurrent()
        onAction(rule, action, null)
        val operation = if (diagnostics.enabled) diagnostics.beginOperation("Action", id, rule?.matchIdentity() ?: "manual") else null
        val result = try {
            if (operation == null) block() else withContext(MatchActionOperation(diagnostics, operation)) { block() }
        } catch (error: Throwable) {
            diagnostics.endOperation(operation, error.toString())
            onAction(rule, action, MatchActionOutcome(if (error is CancellationException) "cancelled" else "failed",
                errorType = error.javaClass.name, message = error.message))
            throw error
        }
        diagnostics.endOperation(operation, if (result.result) "success" else "failed_result")
        onAction(rule, action, MatchActionOutcome("returned", result))
        return result
    }
    suspend fun <N : Any> match(rule: ResolvedRule?, block: suspend () -> N?): N? {
        onQuery(rule, null)
        val operation = if (diagnostics.enabled) diagnostics.beginOperation("Query", id, rule?.matchIdentity() ?: "manual") else null
        val result = try { block() } catch (error: Throwable) {
            diagnostics.endOperation(operation, error.toString())
            onQuery(rule, MatchQueryOutcome(failure = error.javaClass.name))
            throw error
        }
        diagnostics.endOperation(operation, if (result == null) "not_matched" else "matched")
        onQuery(rule, MatchQueryOutcome(matched = result != null))
        return result
    }
    fun trace(stage: MatchStage, reason: String, rule: ResolvedRule? = null, action: ActionResult? = null) {
        if (diagnostics.enabled) diagnostics.record(id, stage, reason, rule?.matchIdentity(), action = action)
    }
}
