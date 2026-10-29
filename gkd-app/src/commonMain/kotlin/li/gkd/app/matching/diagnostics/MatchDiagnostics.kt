package li.gkd.app.matching.diagnostics

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import li.gkd.app.META
import li.gkd.app.matching.ForegroundRuleSnapshot
import li.gkd.app.matching.input.MatchEventKey
import li.gkd.app.matching.input.MatchInputCheckpoint
import li.gkd.app.matching.input.MatchInputDecision
import li.gkd.app.matching.input.MatchInputRecord
import li.gkd.app.matching.input.MatchTimingPolicy
import li.gkd.app.matching.query.MatchNodeReadFailure
import li.gkd.app.model.ActionResult

@Serializable
enum class MatchStage {
    Idle,
    Query,
    Root,
    Rule,
    Selector,
    Action,
    Cancelling,
    Completed,
    Cancelled,
    Failed,
}

@Serializable
data class MatchEventDrop(val key: MatchEventKey, val capacity: Int, val ageMillis: Long)

@Serializable
data class MatchNodePressure(
    val activeReads: Int,
    val capacity: Int,
    val observedReads: Int,
    val oldestObservedReadNanos: Long?,
)

@Serializable
data class MatchOperation(
    val id: Long,
    val sessionId: String,
    val name: String,
    val runId: Long,
    val rule: String?,
    val page: Int?,
    val thread: String,
    val startedNanos: Long,
    val durationNanos: Long? = null,
    val outcome: String? = null,
    val parentOperationId: Long? = null,
    @kotlinx.serialization.Transient val recording: MatchQueryEvidence? = null,
    val arguments: List<MatchRecordedValue> = emptyList(),
)

@Serializable
data class MatchTrace(
    val sequence: Long,
    val time: Long,
    val elapsedNanos: Long,
    val runId: Long,
    val stage: MatchStage,
    val reason: String,
    val rule: String? = null,
    val policy: MatchTimingPolicy? = null,
    val input: MatchInputRecord? = null,
    val inputDecision: MatchInputDecision? = null,
    val ruleSnapshotId: Long? = null,
    val entrySource: String? = null,
    val condition: MatchConditionTrace? = null,
    val action: ActionResult? = null,
    val operation: MatchOperation? = null,
    val taskId: Long? = null,
    val generation: Long? = null,
    val blockingRunId: Long? = null,
    val eventDrop: MatchEventDrop? = null,
    val nodePressure: MatchNodePressure? = null,
)

@Serializable
data class MatchTracePage(
    val events: List<MatchTrace>,
    val cursor: Long,
    val gap: Boolean,
    val active: Boolean,
    val trackingComplete: Boolean,
    val hasMore: Boolean,
)

@Serializable
data class MatchDiagnosticState(
    val id: String,
    val active: Boolean,
    val startedAt: Long,
    val detailed: Boolean,
    val ruleFilter: String?,
    val policy: MatchTimingPolicy,
    val stage: MatchStage = MatchStage.Idle,
    val stageStartedNanos: Long = 0,
    val runId: Long = 0,
    val reason: String = "",
    val rule: String? = null,
    val dropped: Long = 0,
    val inputCheckpoint: MatchInputCheckpoint? = null,
    val recordQueries: Boolean = false,
    val recordSystem: Boolean = false,
    val systemFinalizing: Boolean = false,
    // Retained separately from the ring so an unreturned call survives event eviction.
    val openOperations: List<MatchOperation> = emptyList(),
    val omittedOperations: Long = 0,
    val stoppedAt: Long? = null,
    val operationsAtStop: List<MatchOperation> = emptyList(),
    val trackingComplete: Boolean = false,
)

/** Optional observation only. Captures retain values, never platform nodes or execution plans. */
class MatchDiagnostics(
    private val nanoTime: () -> Long = System::nanoTime,
    private val wallTime: () -> Long = System::currentTimeMillis,
    private val onStoppedCapture: (MatchDiagnosticCapture) -> Unit = {},
) {
    private val lock = Any()
    private val events = ArrayDeque<MatchTrace>()
    private var sequence = 0L
    private var operationSequence = 0L
    private var operationStatistics = MatchOperationStatisticsRecorder()
    private var origin = 0L
    private var capacity = 2000
    private var snapshotSequence = 0L
    private var lastRules: ForegroundRuleSnapshot? = null
    private var definitionSequence = 0L
    private val ruleDefinitions = linkedMapOf<Long, MatchRuleDefinition>()
    private val ruleSnapshots = ArrayDeque<MatchRuleSnapshot>()
    private var droppedRuleSnapshots = 0L
    private val entries = mutableMapOf<Pair<String, String>, Long>()
    private val queryRecordings = mutableListOf<MatchQueryEvidence>()

    private class StoppedSession(
        var capture: MatchDiagnosticCapture,
        val origin: Long,
        val capacity: Int,
        var sequence: Long,
        val queries: List<MatchQueryEvidence>,
        val operationStatistics: MatchOperationStatisticsRecorder,
    )

    private val stoppedSessions = linkedMapOf<String, StoppedSession>()
    private var droppedQueryRecordings = 0L
    private var initialRules: List<MatchRuleIndex>? = null
    private val runs = linkedMapOf<String, MatchDebugRunState>()
    private var runtime: MatchDebugSnapshot? = null
    private var runtimeCapturedAt: Long? = null
    @Volatile var systemRecorder: MatchSystemRecorder? = null
    @Volatile
    var enabled: Boolean = false
        private set

    val state: StateFlow<MatchDiagnosticState?>
        field = MutableStateFlow(null)

    fun start(
        policy: MatchTimingPolicy,
        detailed: Boolean = false,
        ruleFilter: String? = null,
        capacity: Int = 2000,
        recordQueries: Boolean = false,
        recordSystem: Boolean = false,
        initialRules: List<MatchRuleIndex>? = null,
    ): MatchDiagnosticState =
        synchronized(lock) {
            require(capacity in 10..20_000)
            require(!detailed || recordSystem || !ruleFilter.isNullOrBlank()) {
                "Detailed tracing requires a rule filter or system recording"
            }
            require(!recordQueries || recordSystem || !ruleFilter.isNullOrBlank()) {
                "Query recording requires a rule filter"
            }
            check(!enabled) { "A diagnostic session is already active" }
            check(systemRecorder?.sealed != false) {
                "Previous system recording is waiting for action cleanup"
            }
            state.value?.let { previous ->
                stoppedSessions[previous.id] =
                    StoppedSession(
                        export(previous.id),
                        origin,
                        this.capacity,
                        sequence,
                        queryRecordings.toList(),
                        operationStatistics,
                    )
            }
            systemRecorder = null
            operationStatistics = MatchOperationStatisticsRecorder()
            events.clear()
            ruleSnapshots.clear()
            ruleDefinitions.clear()
            definitionSequence = 0L
            lastRules = null
            snapshotSequence = 0
            droppedRuleSnapshots = 0
            entries.clear()
            queryRecordings.clear()
            droppedQueryRecordings = 0
            this.initialRules = initialRules
            runs.clear()
            runtime = null
            runtimeCapturedAt = null
            sequence = 0
            origin = nanoTime()
            this.capacity = capacity
            val now = wallTime()
            val session =
                MatchDiagnosticState(
                    UUID.randomUUID().toString(),
                    true,
                    now,
                    detailed,
                    ruleFilter,
                    policy,
                    recordQueries = recordQueries || recordSystem,
                    recordSystem = recordSystem,
                )
            state.value = session
            enabled = true
            pruneStoppedSessions()
            session
        }

    fun shouldRecordQuery(id: String): Boolean =
        synchronized(lock) {
            if (!enabled || state.value?.id != id || state.value?.recordQueries != true)
                return@synchronized false
            if (state.value?.recordSystem == true || queryRecordings.size < 8) true
            else {
                droppedQueryRecordings++
                false
            }
        }

    fun registerQuery(id: String, evidence: MatchQueryEvidence) =
        synchronized(lock) {
            if (state.value?.id != id || state.value?.recordQueries != true) return@synchronized
            if (state.value?.recordSystem == true) {
                if (queryRecordings.size == 32) {
                    queryRecordings.removeAt(0)
                    droppedQueryRecordings++
                }
                queryRecordings.add(evidence)
            } else if (queryRecordings.size < 8) queryRecordings.add(evidence)
            else droppedQueryRecordings++
        }

    fun finishQuery(id: String) =
        synchronized(lock) {
            if (state.value?.id == id) {
                if (!enabled) onStoppedCapture(export(id))
            } else if (id in stoppedSessions) {
                onStoppedCapture(export(id))
                pruneStoppedSessions(id)
            }
        }

    fun stop(id: String) =
        synchronized(lock) {
            val current = requireSession(id)
            enabled = false
            lastRules = null
            if (current.active)
                state.value =
                    current.copy(
                        active = false,
                        stoppedAt = wallTime(),
                        operationsAtStop = current.openOperations,
                    )
        }

    fun captureRuntime(value: MatchDebugSnapshot) =
        synchronized(lock) {
            if (enabled) {
                runtime = value
                runtimeCapturedAt = wallTime()
            }
        }

    fun recordRequest(id: String, request: MatchDebugRunState) =
        synchronized(lock) {
            if (state.value?.id != id) return@synchronized
            runs[request.request.requestId] = request
            if (!enabled) onStoppedCapture(export(id))
        }

    fun recordInput(
        before: MatchInputCheckpoint,
        input: MatchInputRecord,
        decision: MatchInputDecision,
    ) {
        if (!enabled) return
        synchronized(lock) {
            if (!enabled) return@synchronized
            val current = checkNotNull(state.value)
            if (current.inputCheckpoint == null)
                state.value = current.copy(inputCheckpoint = before)
            val key = "input" to decision.reason
            entries[key] = (entries[key] ?: 0) + 1
            record(0, MatchStage.Root, decision.reason, input = input, inputDecision = decision)
        }
    }

    fun captureRules(rules: ForegroundRuleSnapshot, runId: Long = 0) {
        if (!enabled) return
        synchronized(lock) {
            if (!enabled) return
            if (lastRules !== rules) {
                if (ruleSnapshots.size == 16) {
                    ruleSnapshots.removeFirst()
                    droppedRuleSnapshots++
                }
                ruleSnapshots.addLast(
                    rules.diagnosticSnapshot(++snapshotSequence) { definition ->
                        ruleDefinitions.entries.firstOrNull { it.value == definition }?.key
                            ?: (++definitionSequence).also { ruleDefinitions[it] = definition }
                    },
                )
                val retained = ruleSnapshots.flatMapTo(mutableSetOf()) { it.definitions }
                ruleDefinitions.keys.retainAll(retained)
                lastRules = rules
            }
            record(runId, MatchStage.Rule, "rule_snapshot", ruleSnapshotId = snapshotSequence)
        }
    }

    fun entry(
        source: String,
        reason: String,
        runId: Long = 0,
        eventDrop: MatchEventDrop? = null,
        nodePressure: MatchNodePressure? = null,
    ) {
        if (!enabled) return
        synchronized(lock) {
            if (!enabled) return
            val key = source to reason
            entries[key] = (entries[key] ?: 0) + 1
            record(
                runId,
                MatchStage.Idle,
                reason,
                entrySource = source,
                eventDrop = eventDrop,
                nodePressure = nodePressure,
            )
        }
    }

    fun nodeReadPressure(
        reason: MatchNodeReadFailure,
        activeReads: Int,
        capacity: Int,
        sessionId: String? = null,
    ) {
        if (!enabled) return
        synchronized(lock) {
            if (!enabled) return
            if (sessionId != null && state.value?.id != sessionId) return
            val reads =
                checkNotNull(state.value).openOperations.filter {
                    it.name == NODE_READ_OPERATION_NAME ||
                        it.name.startsWith("$NODE_READ_OPERATION_NAME:")
                }
            val oldest = reads.minOfOrNull { it.startedNanos }
            entry(
                "node_reader",
                reason.wireValue,
                nodePressure =
                    MatchNodePressure(
                        activeReads,
                        capacity,
                        reads.size,
                        oldest?.let { nanoTime() - origin - it },
                    ),
            )
        }
    }

    fun beginOperation(
        name: String,
        runId: Long = 0,
        rule: String? = null,
        page: Any? = null,
        parentOperationId: Long? = null,
        arguments: List<MatchRecordedValue> = emptyList(),
        sessionId: String? = null,
        recording: MatchQueryEvidence? = null,
    ): MatchOperation? {
        if (!enabled) return null
        return synchronized(lock) {
            if (!enabled) return@synchronized null
            val current = checkNotNull(state.value)
            if (sessionId != null && current.id != sessionId) return@synchronized null
            if (current.openOperations.size >= 128) {
                state.value = current.copy(omittedOperations = current.omittedOperations + 1)
                return@synchronized null
            }
            val operation =
                MatchOperation(
                    ++operationSequence,
                    current.id,
                    name,
                    runId,
                    rule,
                    page?.let(System::identityHashCode),
                    Thread.currentThread().name,
                    nanoTime() - origin,
                    parentOperationId = parentOperationId,
                    arguments = arguments,
                    recording = recording,
                )
            state.value = current.copy(openOperations = current.openOperations + operation)
            if (recording == null)
                record(
                    runId,
                    MatchStage.Idle,
                    "operation_started",
                    rule,
                    entrySource = "operation",
                    operation = operation,
                )
            operation
        }
    }

    fun endOperation(operation: MatchOperation?, outcome: String) {
        if (operation == null) return
        synchronized(lock) {
            val current = state.value ?: return
            if (current.id != operation.sessionId) {
                val previous = stoppedSessions[operation.sessionId] ?: return
                val capture = previous.capture
                if (capture.session.openOperations.none { it.id == operation.id }) return
                val elapsed = nanoTime() - previous.origin
                val finished =
                    operation.copy(
                        durationNanos = elapsed - operation.startedNanos,
                        outcome = outcome,
                    )
                val event =
                    MatchTrace(
                        ++previous.sequence,
                        wallTime(),
                        elapsed,
                        operation.runId,
                        MatchStage.Idle,
                        "operation_finished_after_stop",
                        operation.rule,
                        entrySource = "operation",
                        operation = finished,
                    )
                previous.operationStatistics.record(finished)
                recordFinishedOperation(finished)
                val dropped = capture.events.size == previous.capacity
                previous.capture =
                    capture.copy(
                        session =
                            capture.session.copy(
                                openOperations =
                                    capture.session.openOperations.filterNot {
                                        it.id == operation.id
                                    },
                                dropped = capture.session.dropped + if (dropped) 1 else 0,
                            ),
                        events = (if (dropped) capture.events.drop(1) else capture.events) + event,
                    )
                onStoppedCapture(export(operation.sessionId))
                pruneStoppedSessions(operation.sessionId)
                return
            }
            if (current.openOperations.none { it.id == operation.id }) return
            state.value =
                current.copy(
                    openOperations = current.openOperations.filterNot { it.id == operation.id },
                )
            val finished =
                operation.copy(
                    durationNanos = nanoTime() - origin - operation.startedNanos,
                    outcome = outcome,
                )
            operationStatistics.record(finished)
            recordFinishedOperation(finished)
            if (enabled) {
                if (
                    operation.recording != null &&
                        (outcome == "returned" || outcome == "null" || outcome == "false")
                )
                    return
                record(
                    operation.runId,
                    MatchStage.Idle,
                    "operation_finished",
                    operation.rule,
                    entrySource = "operation",
                    operation = finished,
                )
            } else {
                if (events.size == capacity) {
                    events.removeFirst()
                    state.value = checkNotNull(state.value).copy(dropped = current.dropped + 1)
                }
                events.addLast(
                    MatchTrace(
                        ++sequence,
                        wallTime(),
                        nanoTime() - origin,
                        operation.runId,
                        MatchStage.Idle,
                        "operation_finished_after_stop",
                        operation.rule,
                        entrySource = "operation",
                        operation = finished,
                    ),
                )
                onStoppedCapture(export(current.id))
            }
        }
    }

    private fun recordFinishedOperation(operation: MatchOperation) {
        if (operation.recording != null && !operation.name.startsWith("Selector.")) {
            operation.recording.operation(operation)
        }
    }

    /** Inline fast path: no clocks, locks, records or node inspection when disabled. */
    inline fun <T> observeOperation(
        name: String,
        runId: Long = 0,
        rule: String? = null,
        page: Any? = null,
        parentOperationId: Long? = null,
        arguments: List<MatchRecordedValue> = emptyList(),
        sessionId: String? = null,
        recording: MatchQueryEvidence? = null,
        block: () -> T,
    ): T {
        if (!enabled) return block()
        val operation =
            beginOperation(
                name,
                runId,
                rule,
                page,
                parentOperationId,
                arguments,
                sessionId,
                recording,
            )
        try {
            val result = block()
            endOperation(
                operation,
                if (result == null) "null"
                else if (result is Boolean && !result) "false" else "returned",
            )
            return result
        } catch (error: Throwable) {
            endOperation(operation, error.toString())
            throw error
        }
    }

    fun record(
        runId: Long,
        stage: MatchStage,
        reason: String,
        rule: String? = null,
        policy: MatchTimingPolicy? = null,
        input: MatchInputRecord? = null,
        inputDecision: MatchInputDecision? = null,
        ruleSnapshotId: Long? = null,
        entrySource: String? = null,
        condition: MatchConditionTrace? = null,
        sessionId: String? = null,
        action: ActionResult? = null,
        operation: MatchOperation? = null,
        taskId: Long? = null,
        generation: Long? = null,
        blockingRunId: Long? = null,
        eventDrop: MatchEventDrop? = null,
        nodePressure: MatchNodePressure? = null,
    ) {
        if (!enabled) return
        synchronized(lock) {
            if (!enabled) return
            val current = checkNotNull(state.value)
            if (sessionId != null && current.id != sessionId) return
            if (current.ruleFilter != null && rule != null && rule != current.ruleFilter) return
            val now = nanoTime()
            val event =
                MatchTrace(
                    ++sequence,
                    wallTime(),
                    now - origin,
                    runId,
                    stage,
                    reason,
                    rule,
                    policy,
                    input,
                    inputDecision,
                    ruleSnapshotId,
                    entrySource,
                    condition,
                    action,
                    operation,
                    taskId,
                    generation,
                    blockingRunId,
                    eventDrop,
                    nodePressure,
                )
            val dropped =
                if (events.size == capacity) {
                    events.removeFirst()
                    current.dropped + 1
                } else current.dropped
            events.addLast(event)
            state.value =
                if (
                    entrySource != null ||
                        operation != null ||
                        (runId == 0L && (policy != null || input != null || ruleSnapshotId != null))
                )
                    current.copy(dropped = dropped)
                else
                    current.copy(
                        stage = stage,
                        stageStartedNanos = now,
                        runId = runId,
                        reason = reason,
                        rule = rule,
                        dropped = dropped,
                    )
        }
    }

    fun read(id: String, after: Long = 0, limit: Int = 200): MatchTracePage =
        synchronized(lock) {
            require(after >= 0)
            require(limit in 1..1000)
            val current = session(id)
            val records = stoppedSessions[id]?.capture?.events ?: events
            val selected = records.asSequence().filter { it.sequence > after }.take(limit).toList()
            val cursor = selected.lastOrNull()?.sequence ?: after
            MatchTracePage(
                selected,
                cursor,
                records.firstOrNull()?.let { after < it.sequence - 1 } ?: false,
                current.active,
                current.trackingComplete,
                records.lastOrNull()?.sequence?.let { it > cursor } ?: false,
            )
        }

    fun export(id: String): MatchDiagnosticCapture =
        synchronized(lock) {
            stoppedSessions[id]?.let { previous ->
                return@synchronized completeTracking(
                    previous.capture.copy(
                        queryRecordings = previous.queries.map { it.snapshot() },
                        operationStatistics = previous.operationStatistics.snapshot(),
                    ),
                )
            }
            val current = requireSession(id)
            val system = systemRecorder?.export()
            completeTracking(
                MatchDiagnosticCapture(
                    current.copy(systemFinalizing = !current.active && system?.sealed == false),
                    events.toList(),
                    ruleSnapshots.toList(),
                    droppedRuleSnapshots,
                    entries.map { (key, count) -> MatchEntrySummary(key.first, key.second, count) },
                    queryRecordings = queryRecordings.map { it.snapshot() },
                    droppedQueryRecordings = droppedQueryRecordings,
                    systemRecording = system,
                    initialRules = initialRules,
                    ruleDefinitions = ruleDefinitions.toMap(),
                    operationStatistics = operationStatistics.snapshot(),
                    runs = runs.values.toList(),
                    runtime = runtime,
                    runtimeCapturedAt = runtimeCapturedAt,
                    appId = META.appId,
                    appVersion = META.versionName,
                    platform = System.getProperty("os.name", "unknown") ?: "unknown",
                ),
            )
        }

    fun requests(id: String): List<MatchDebugRunState> =
        synchronized(lock) {
            stoppedSessions[id]?.capture?.runs
                ?: run {
                    requireSession(id)
                    runs.values.toList()
                }
        }

    fun session(id: String): MatchDiagnosticState =
        synchronized(lock) {
            val previous = stoppedSessions[id]
            val current = previous?.capture?.session ?: requireSession(id)
            val queries = previous?.queries ?: queryRecordings
            val sealed =
                if (previous != null) previous.capture.systemRecording?.sealed
                else systemRecorder?.sealed
            val requests = previous?.capture?.runs ?: runs.values
            current.copy(
                systemFinalizing = !current.active && sealed == false,
                trackingComplete =
                    trackingComplete(current, queries.all { it.completed }, sealed, requests),
            )
        }

    /**
     * Keep stop/final snapshots ordered with late completions when handing them to the file queue.
     */
    fun saveStopped(id: String, save: (MatchDiagnosticCapture) -> Unit) =
        synchronized(lock) {
            check(!requireSession(id).active)
            save(export(id))
        }

    private fun completeTracking(capture: MatchDiagnosticCapture): MatchDiagnosticCapture =
        capture.copy(
            session =
                capture.session.copy(
                    trackingComplete =
                        trackingComplete(
                            capture.session,
                            capture.queryRecordings.all { it.completed },
                            capture.systemRecording?.sealed,
                            capture.runs,
                        ),
                ),
        )

    private fun trackingComplete(
        session: MatchDiagnosticState,
        queriesCompleted: Boolean,
        systemSealed: Boolean?,
        requests: Collection<MatchDebugRunState>,
    ): Boolean =
        !session.active &&
            session.openOperations.isEmpty() &&
            queriesCompleted &&
            systemSealed != false &&
            requests.none {
                it.phase == MatchDebugRunPhase.Running || it.phase == MatchDebugRunPhase.Cancelling
            }

    // Keep pending evidence until its actual completion. Finished sessions retain a bounded
    // in-memory history.
    private fun pruneStoppedSessions(updatedId: String? = null) {
        updatedId?.let { id -> stoppedSessions.remove(id)?.let { stoppedSessions[id] = it } }
        val completed = stoppedSessions.keys.filter { session(it).trackingComplete }
        completed.dropLast(5).forEach { stoppedSessions.remove(it) }
    }

    private fun requireSession(id: String): MatchDiagnosticState =
        requireNotNull(state.value?.takeIf { it.id == id }) { "Unknown diagnostic session" }
}
