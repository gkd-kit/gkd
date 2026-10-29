package li.gkd.app.matching

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
import li.gkd.app.matching.diagnostics.ForegroundDebugState
import li.gkd.app.matching.diagnostics.MatchActionOperation
import li.gkd.app.matching.diagnostics.MatchDebugSnapshot
import li.gkd.app.matching.diagnostics.MatchDiagnosticCapture
import li.gkd.app.matching.diagnostics.MatchDiagnosticState
import li.gkd.app.matching.diagnostics.MatchDiagnostics
import li.gkd.app.matching.diagnostics.MatchStage
import li.gkd.app.matching.diagnostics.MatchSystemRecorder
import li.gkd.app.matching.diagnostics.diagnosticIndex
import li.gkd.app.matching.input.MatchTimingPolicy
import li.gkd.app.model.ActionResult
import li.gkd.app.model.GkdAction
import li.gkd.app.rule.ResolvedRule
import li.gkd.app.rule.RuleDelayKind
import li.gkd.app.util.monotonicTimeMillis

/** Coroutine host for the scheduling machine. Cleanup owns the lane until completion. */
class MatchCoordinator(
    parentScope: CoroutineScope,
    private val workerContext: CoroutineContext,
    diagnostics: MatchDiagnostics? = null,
    private val nowMillis: () -> Long = ::monotonicTimeMillis,
    private val onDiagnosticCapture: (MatchDiagnosticCapture) -> Unit = {},
    private val foregroundSnapshot: () -> ForegroundDebugState? = { null },
    private val onFailure: (Throwable) -> Unit,
) : AutoCloseable {
    val diagnostics = diagnostics ?: MatchDiagnostics(onStoppedCapture = onDiagnosticCapture)
    private val lock = Any()
    private val owner = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + owner)
    private val machine = MatchScheduleMachine()
    private val identities = WeakHashMap<Any, Long>()
    private var nextIdentity = 0L
    private val ruleDescriptions = WeakHashMap<ResolvedRule, MatchScheduleRule>()
    private val snapshotDescriptions = WeakHashMap<ForegroundRuleSnapshot, MatchScheduleRules>()
    private var nextTask = 0L
    private val manualRule = MatchScheduleRule(0, 0, "manual", false)

    private class Task(
        val block: suspend MatchWork.() -> Unit,
        val completed: (Throwable?) -> Unit = {},
    ) {
        var job: Job? = null
    }

    private val tasks = mutableMapOf<Long, Task>()
    private var active: Job? = null
    private var binding: Any? = null
    private var rules: ForegroundRuleSnapshot? = null
    private var wake: Job? = null
    private var diagnosticOwner: Job? = null
    private var pendingDiagnosticSave = false
    private var stoppedDiagnosticOwner: Job? = null
    private var diagnosticPolicy: MatchTimingPolicy? = null
    val policy: StateFlow<MatchTimingPolicy>
        field = MutableStateFlow(MatchTimingPolicy())

    private fun identity(value: Any): Long = identities.getOrPut(value) { ++nextIdentity }

    private fun serviceIdentity(key: Any): Long? =
        if (binding === key) machine.state.service else -1L

    private fun describe(rule: ResolvedRule): MatchScheduleRule {
        val execution = identity(rule.executionIdentity)
        return ruleDescriptions[rule]?.takeIf { it.execution == execution }
            ?: MatchScheduleRule(
                    identity(rule),
                    execution,
                    rule.matchIdentity(),
                    rule.priorityEnabled,
                )
                .also { ruleDescriptions[rule] = it }
    }

    private fun describe(value: ForegroundRuleSnapshot) =
        snapshotDescriptions.getOrPut(value) {
            MatchScheduleRules(identity(value), value.currentRules.map(::describe))
        }

    private fun task(value: Task): Long = (++nextTask).also { tasks[it] = value }

    private fun task(block: suspend MatchWork.() -> Unit): Long = task(Task(block))

    private fun dispatch(command: MatchScheduleCommand): List<MatchScheduleEffect> {
        val before = machine.state
        val now = nowMillis()
        val effects = machine.apply(command, now)
        // Record before executing effects: immediate coroutine completion may reenter this lock.
        diagnostics.systemRecorder?.append(command, now, effects, machine.state)
        for (effect in effects) when (effect.type) {
            MatchScheduleEffectType.Start -> startLocked(requireNotNull(effect.run))
            MatchScheduleEffectType.Cancel -> {
                if (diagnostics.enabled)
                    diagnostics.record(
                        requireNotNull(effect.run).id,
                        MatchStage.Cancelling,
                        requireNotNull(effect.reason),
                        taskId = effect.run.task,
                        generation = effect.run.generation,
                    )
                active?.cancel(CancellationException(effect.reason))
            }
            MatchScheduleEffectType.Arm -> {
                if (effect.timer != machine.state.timer) continue
                wake?.cancel()
                wake = null
                effect.deadline?.let { deadline ->
                    val timer =
                        scope.launch(start = CoroutineStart.LAZY) {
                            delay((deadline - nowMillis()).coerceAtLeast(0))
                            synchronized(lock) {
                                dispatch(
                                    MatchScheduleCommand(
                                        MatchScheduleCommandType.Wake,
                                        timer = effect.timer,
                                    ),
                                )
                            }
                        }
                    wake = timer
                    timer.start()
                }
            }
            MatchScheduleEffectType.Reject ->
                diagnostics.entry("coordinator", requireNotNull(effect.reason))
            MatchScheduleEffectType.Commit -> Unit
        }
        val state = machine.state
        // New tasks also need cleanup when the command was rejected or deduplicated.
        // Read state after effects, which may synchronously finish or start another run.
        if (
            command.task != null ||
                before.active?.task != state.active?.task ||
                before.pending != state.pending ||
                before.deadlines !== state.deadlines
        ) {
            tasks.keys.retainAll(
                buildSet {
                    state.active?.let { add(it.task) }
                    state.pending?.let { add(it) }
                    state.deadlines.forEach { add(it.task) }
                },
            )
        }
        finishDiagnosticCapture()
        return effects
    }

    fun updatePolicy(value: MatchTimingPolicy) =
        synchronized(lock) {
            if (policy.value == value) return@synchronized
            dispatch(MatchScheduleCommand(MatchScheduleCommandType.Policy, policy = value))
            policy.value = value
            if (diagnostics.enabled)
                diagnostics.record(0, MatchStage.Idle, "policy_updated", policy = value)
        }

    fun updateDiagnosticPolicy(value: MatchTimingPolicy) =
        synchronized(lock) {
            check(diagnostics.enabled) { "Start a diagnostic session before changing timing" }
            updatePolicy(value)
        }

    fun snapshot(): MatchEngineSnapshot =
        synchronized(lock) {
            val state = machine.state
            MatchEngineSnapshot(
                state.service != null,
                state.generation,
                state.active?.id,
                state.pending != null,
                state.deadlines
                    .minOfOrNull { it.deadline }
                    ?.let { (it - nowMillis()).coerceAtLeast(0) },
                state.deadlines.count { it.rule != null },
                state.policy,
                state.active?.cancelling == true,
            )
        }

    fun attach(key: Any) =
        synchronized(lock) {
            if (binding === key) return@synchronized
            binding = key
            dispatch(
                MatchScheduleCommand(MatchScheduleCommandType.Attach, service = ++nextIdentity),
            )
            Unit
        }

    fun detach(key: Any) =
        synchronized(lock) {
            val service = serviceIdentity(key)
            if (binding === key) binding = null
            dispatch(MatchScheduleCommand(MatchScheduleCommandType.Detach, service = service))
            Unit
        }

    fun invalidate(reason: MatchInvalidationReason) =
        synchronized(lock) {
            dispatch(
                MatchScheduleCommand(
                    MatchScheduleCommandType.Invalidate,
                    invalidationReason = reason,
                ),
            )
            Unit
        }

    fun updateRules(value: ForegroundRuleSnapshot) =
        synchronized(lock) {
            rules = value
            if (diagnostics.enabled) diagnostics.captureRules(value)
            dispatch(MatchScheduleCommand(MatchScheduleCommandType.Rules, rules = describe(value)))
            Unit
        }

    fun canCommit(runId: Long, rule: ResolvedRule): Boolean =
        synchronized(lock) {
            if (
                machine.state.active?.id == runId &&
                    active?.isActive != true &&
                    machine.state.active?.cancelling == false
            ) {
                dispatch(
                    MatchScheduleCommand(
                        MatchScheduleCommandType.Cancel,
                        run = runId,
                        reason = "coroutine_cancelled",
                    ),
                )
            }
            dispatch(
                    MatchScheduleCommand(
                        MatchScheduleCommandType.Commit,
                        run = runId,
                        rule = describe(rule),
                    ),
                )
                .single()
                .canCommit == true
        }

    fun request(key: Any, block: suspend MatchWork.() -> Unit) =
        synchronized(lock) {
            if (!owner.isActive) {
                diagnostics.entry("coordinator", "engine_closed")
                return@synchronized
            }
            val taskId = task(block)
            if (binding === key && diagnostics.enabled)
                diagnostics.record(
                    0,
                    MatchStage.Idle,
                    if (active != null) "queued_behind_active_work" else "query_requested",
                    entrySource = "coordinator",
                    taskId = taskId,
                    generation = machine.state.generation,
                    blockingRunId = machine.state.active?.id,
                )
            dispatch(
                MatchScheduleCommand(
                    MatchScheduleCommandType.Request,
                    service = serviceIdentity(key),
                    task = taskId,
                ),
            )
            Unit
        }

    fun scheduleFollowUp(key: Any, block: suspend MatchWork.() -> Unit) =
        synchronized(lock) {
            if (!owner.isActive) return@synchronized
            dispatch(
                MatchScheduleCommand(
                    MatchScheduleCommandType.FollowUp,
                    service = serviceIdentity(key),
                    task = task(block),
                ),
            )
            Unit
        }

    fun scheduleRule(
        key: Any,
        rule: ResolvedRule,
        kind: RuleDelayKind,
        delayMillis: Long,
        block: suspend MatchWork.() -> Unit,
    ) =
        synchronized(lock) {
            if (!owner.isActive) return@synchronized
            dispatch(
                MatchScheduleCommand(
                    MatchScheduleCommandType.RuleDelay,
                    service = serviceIdentity(key),
                    task = task(block),
                    rule = describe(rule),
                    kind = kind,
                    delayMillis = delayMillis,
                ),
            )
            Unit
        }

    fun cancelRuleDelays(rule: ResolvedRule) =
        synchronized(lock) {
            dispatch(
                MatchScheduleCommand(MatchScheduleCommandType.CancelDelays, rule = describe(rule)),
            )
            Unit
        }

    suspend fun <T> execute(key: Any, block: suspend MatchWork.() -> T): T {
        val result = CompletableDeferred<T>()
        val request =
            Task(
                block = {
                    try {
                        result.complete(block())
                    } catch (error: Throwable) {
                        result.completeExceptionally(error)
                        throw error
                    }
                },
                completed = { error ->
                    if (!result.isCompleted)
                        result.completeExceptionally(
                            error ?: CancellationException("Execution ended without a result"),
                        )
                },
            )
        var runId: Long
        val job =
            synchronized(lock) {
                check(binding === key && owner.isActive) { "Service session is unavailable" }
                check(active == null) { "Matching engine is busy" }
                val effects =
                    dispatch(
                        MatchScheduleCommand(
                            MatchScheduleCommandType.Execute,
                            service = serviceIdentity(key),
                            task = task(request),
                        ),
                    )
                runId =
                    requireNotNull(effects.single { it.type == MatchScheduleEffectType.Start }.run)
                        .id
                checkNotNull(request.job)
            }
        return try {
            result.await()
        } catch (error: CancellationException) {
            synchronized(lock) {
                dispatch(
                    MatchScheduleCommand(
                        MatchScheduleCommandType.Cancel,
                        run = runId,
                        reason = error.message ?: "caller_cancelled",
                    ),
                )
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                job.join()
                try {
                    result.await()
                } catch (_: CancellationException) {}
            }
            throw error
        }
    }

    private fun startLocked(run: MatchScheduledRun) {
        val task = tasks.getValue(run.task)
        val work =
            MatchWork(
                run.id,
                machine.state.policy,
                diagnostics,
                onTrackRules = { snapshot ->
                    synchronized(lock) {
                        val effects =
                            dispatch(
                                MatchScheduleCommand(
                                    MatchScheduleCommandType.Track,
                                    run = run.id,
                                    rules = describe(snapshot),
                                ),
                            )
                        effects
                            .firstOrNull { it.type == MatchScheduleEffectType.Reject }
                            ?.let { throw CancellationException(it.reason) }
                        if (diagnostics.enabled) diagnostics.captureRules(snapshot, run.id)
                    }
                },
                onSelectRule = { rule ->
                    synchronized(lock) {
                        val effects =
                            dispatch(
                                MatchScheduleCommand(
                                    MatchScheduleCommandType.Select,
                                    run = run.id,
                                    rule = describe(rule),
                                ),
                            )
                        effects
                            .firstOrNull { it.type == MatchScheduleEffectType.Reject }
                            ?.let { throw CancellationException(it.reason) }
                    }
                },
                onAction = { rule, action, outcome ->
                    synchronized(lock) {
                        if (
                            outcome == null &&
                                (machine.state.active?.id != run.id ||
                                    machine.state.active?.cancelling != false ||
                                    run.generation != machine.state.generation)
                        ) {
                            throw CancellationException("execution_context_changed")
                        }
                        dispatch(
                            if (outcome == null)
                                MatchScheduleCommand(
                                    MatchScheduleCommandType.ActionStart,
                                    run = run.id,
                                    rule = rule?.let(::describe) ?: manualRule,
                                    action =
                                        action
                                            ?: requireNotNull(rule).let {
                                                GkdAction(
                                                    "",
                                                    action = it.parameters.action,
                                                    position = it.parameters.position,
                                                    swipeArg = it.parameters.swipeArg,
                                                )
                                            },
                                )
                            else
                                MatchScheduleCommand(
                                    MatchScheduleCommandType.ActionEnd,
                                    run = run.id,
                                    outcome = outcome,
                                ),
                        )
                    }
                },
                onQuery = { rule, outcome ->
                    synchronized(lock) {
                        dispatch(
                            MatchScheduleCommand(
                                if (outcome == null) MatchScheduleCommandType.QueryStart
                                else MatchScheduleCommandType.QueryEnd,
                                run = run.id,
                                rule = rule?.let(::describe) ?: manualRule,
                                queryOutcome = outcome,
                            ),
                        )
                    }
                },
            ) {
                synchronized(lock) {
                    machine.state.active?.let {
                        it.id == run.id &&
                            !it.cancelling &&
                            it.generation == machine.state.generation
                    } == true && owner.isActive
                }
            }
        val job =
            scope.launch(workerContext, start = CoroutineStart.LAZY) {
                try {
                    work.ensureCurrent()
                    if (diagnostics.enabled)
                        diagnostics.record(
                            work.id,
                            MatchStage.Query,
                            "query_started",
                            policy = work.policy,
                            taskId = run.task,
                            generation = run.generation,
                        )
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
                    dispatch(MatchScheduleCommand(MatchScheduleCommandType.Finish, run = run.id))
                }
            }
        }
        job.start()
    }

    suspend fun startDiagnostics(
        detailed: Boolean,
        ruleFilter: String?,
        recordQueries: Boolean = false,
        recordSystem: Boolean = false,
    ): MatchDiagnosticState {
        return synchronized(lock) {
            check(owner.isActive) { "Matching engine is closed" }
            finishDiagnosticCapture()
            check(!pendingDiagnosticSave) { "Previous diagnostic session is waiting for cleanup" }
            val session =
                diagnostics.start(
                    policy.value,
                    detailed,
                    ruleFilter,
                    recordQueries = recordQueries,
                    recordSystem = recordSystem,
                    initialRules =
                        rules
                            ?.takeIf { it.ruleAvailability == RuleAvailability.Ready }
                            ?.ruleSummary
                            ?.diagnosticIndex(),
                )
            diagnosticOwner = SupervisorJob(owner)
            if (recordSystem)
                diagnostics.systemRecorder = MatchSystemRecorder(machine.state, nowMillis())
            rules?.let { diagnostics.captureRules(it) }
            if (binding == null) diagnostics.entry("coordinator", "service_unavailable")
            diagnosticPolicy = policy.value
            session
        }
    }

    /**
     * Requests belong to this session; caller cancellation goes through execute's action cleanup.
     */
    fun diagnosticScope(id: String): CoroutineScope =
        synchronized(lock) {
            check(diagnostics.state.value?.let { it.id == id && it.active } == true) {
                "Diagnostic session is unavailable"
            }
            CoroutineScope(scope.coroutineContext + checkNotNull(diagnosticOwner))
        }

    fun stopDiagnostics(id: String) {
        val foreground = foregroundSnapshot()
        val stopped =
            synchronized(lock) {
                if (diagnostics.state.value?.let { it.id == id && it.active } != true) return
                diagnostics.captureRuntime(MatchDebugSnapshot(snapshot(), foreground))
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

    fun exportDiagnostics(id: String): MatchDiagnosticCapture {
        val foreground = if (diagnostics.enabled) foregroundSnapshot() else null
        return synchronized(lock) {
            if (diagnostics.state.value?.id == id)
                diagnostics.captureRuntime(MatchDebugSnapshot(snapshot(), foreground))
            diagnostics.export(id)
        }
    }

    private fun finishDiagnosticCapture() {
        if (
            !pendingDiagnosticSave ||
                stoppedDiagnosticOwner?.isCompleted == false ||
                diagnostics.systemRecorder?.sealed == false
        )
            return
        pendingDiagnosticSave = false
        stoppedDiagnosticOwner = null
        diagnostics.saveStopped(checkNotNull(diagnostics.state.value).id, onDiagnosticCapture)
    }

    override fun close() {
        val session =
            synchronized(lock) {
                dispatch(MatchScheduleCommand(MatchScheduleCommandType.Close))
                binding = null
                diagnostics.state.value?.id
            }
        session?.let(::stopDiagnostics)
        owner.cancel()
    }
}

@kotlinx.serialization.Serializable
data class MatchEngineSnapshot(
    val attached: Boolean,
    val generation: Long,
    val activeRunId: Long?,
    val queryPending: Boolean,
    val nextWakeMillis: Long?,
    val ruleDeadlines: Int,
    val policy: MatchTimingPolicy,
    val cancelling: Boolean = false,
)

class MatchWork(
    val id: Long,
    val policy: MatchTimingPolicy,
    private val diagnostics: MatchDiagnostics,
    private val onTrackRules: (ForegroundRuleSnapshot) -> Unit = {},
    private val onSelectRule: (ResolvedRule) -> Unit = {},
    private val onAction: (ResolvedRule?, GkdAction?, MatchActionOutcome?) -> Unit = { _, _, _ -> },
    private val onQuery: (ResolvedRule?, MatchQueryOutcome?) -> Unit = { _, _ -> },
    private val isCurrent: () -> Boolean,
) {
    fun trackRules(snapshot: ForegroundRuleSnapshot) = onTrackRules(snapshot)

    suspend fun selectRule(rule: ResolvedRule) {
        onSelectRule(rule)
        ensureCurrent()
    }

    suspend fun ensureCurrent() {
        currentCoroutineContext().ensureActive()
        if (!isCurrent()) throw CancellationException("execution_context_changed")
    }

    suspend fun performAction(rule: ResolvedRule, block: suspend () -> ActionResult): ActionResult =
        performAction(rule, null, block)

    suspend fun performAction(action: GkdAction, block: suspend () -> ActionResult): ActionResult =
        performAction(null, action, block)

    private suspend fun performAction(
        rule: ResolvedRule?,
        action: GkdAction?,
        block: suspend () -> ActionResult,
    ): ActionResult {
        ensureCurrent()
        onAction(rule, action, null)
        val operation =
            if (diagnostics.enabled)
                diagnostics.beginOperation("Action", id, rule?.matchIdentity() ?: "manual")
            else null
        val result =
            try {
                if (operation == null) block()
                else withContext(MatchActionOperation(diagnostics, operation)) { block() }
            } catch (error: Throwable) {
                diagnostics.endOperation(operation, error.toString())
                onAction(
                    rule,
                    action,
                    MatchActionOutcome(
                        if (error is CancellationException) MatchActionOutcomeKind.Cancelled
                        else MatchActionOutcomeKind.Failed,
                        errorType = error.javaClass.name,
                        message = error.message,
                    ),
                )
                throw error
            }
        diagnostics.endOperation(operation, if (result.result) "success" else "failed_result")
        onAction(rule, action, MatchActionOutcome(MatchActionOutcomeKind.Returned, result))
        return result
    }

    suspend fun <N : Any> match(rule: ResolvedRule?, block: suspend () -> N?): N? {
        onQuery(rule, null)
        val operation =
            if (diagnostics.enabled)
                diagnostics.beginOperation("Query", id, rule?.matchIdentity() ?: "manual")
            else null
        val result =
            try {
                block()
            } catch (error: Throwable) {
                diagnostics.endOperation(operation, error.toString())
                onQuery(rule, MatchQueryOutcome(failure = error.javaClass.name))
                throw error
            }
        diagnostics.endOperation(operation, if (result == null) "not_matched" else "matched")
        onQuery(rule, MatchQueryOutcome(matched = result != null))
        return result
    }

    fun trace(
        stage: MatchStage,
        reason: String,
        rule: ResolvedRule? = null,
        action: ActionResult? = null,
    ) {
        if (diagnostics.enabled)
            diagnostics.record(id, stage, reason, rule?.matchIdentity(), action = action)
    }
}
