package li.gkd.app.matching.diagnostics

import kotlinx.serialization.Serializable
import li.gkd.app.matching.MatchScheduleCheckpoint
import li.gkd.app.matching.MatchScheduleCommand
import li.gkd.app.matching.MatchScheduleCommandType
import li.gkd.app.matching.MatchScheduleEffect
import li.gkd.app.matching.MatchScheduleRule
import li.gkd.app.matching.MatchScheduleRules
import li.gkd.app.matching.MatchScheduledRun
import li.gkd.app.model.GkdAction
import li.gkd.app.rule.ResolvedRule
import li.gkd.app.rule.RuleExecutionParameters
import li.gkd.app.rule.RuleExecutionState
import li.gkd.app.rule.RuleRuntime
import li.gkd.app.rule.RuleStateObserver
import li.gkd.app.rule.RuleStateOperation
import li.gkd.app.rule.RuleStateValues
import li.gkd.app.rule.RuleStatus
import li.gkd.app.subscription.RawSubscription

@Serializable
data class MatchSystemStep(
    val sequence: Long,
    val timeMillis: Long,
    val command: MatchScheduleCommand? = null,
    val effects: List<MatchScheduleEffect> = emptyList(),
    val ruleState: RuleStateRecord? = null,
)

@Serializable
data class MatchSystemRecording(
    val version: Int = 2,
    val initial: MatchScheduleCheckpoint,
    val startedAtMillis: Long,
    val steps: List<MatchSystemStep>,
    val final: MatchScheduleCheckpoint,
    val sealed: Boolean,
    val incomplete: String? = null,
    val stoppedAtMillis: Long? = null,
    val finishingRun: Long? = null,
)

/** Bounded, loss-explicit recording. Unlike the live log ring, it never evicts its prefix. */
class MatchSystemRecorder(
    private val initial: MatchScheduleCheckpoint,
    private val startedAt: Long,
    private val capacity: Int = 20_000,
    private val maxEstimatedBytes: Long = 16_000_000,
) : RuleStateObserver {
    private val lock = Any()
    private val steps = mutableListOf<MatchSystemStep>()
    private var final = initial
    private var sequence = 0L
    private var estimatedBytes = 0L
    private var incomplete: String? = null
    private var stoppedAt: Long? = null
    private var finishing: Long? = null
    private val identities = java.util.IdentityHashMap<Any, Long>()
    @Volatile
    var sealed = false
        private set

    fun append(
        command: MatchScheduleCommand,
        now: Long,
        effects: List<MatchScheduleEffect>,
        state: MatchScheduleCheckpoint,
    ) =
        synchronized(lock) {
            if (sealed) return@synchronized
            final = state
            if (incomplete == null) recordStep(MatchSystemStep(++sequence, now, command, effects))
            if (
                stoppedAt != null &&
                    command.type == MatchScheduleCommandType.Finish &&
                    command.run == finishing
            ) {
                sealed = true
                identities.clear()
            }
        }

    private fun recordStep(step: MatchSystemStep) {
        val size = step.estimatedBytes()
        if (steps.size >= capacity || estimatedBytes + size > maxEstimatedBytes) {
            incomplete = "system_recording_limit"
        } else {
            steps += step
            estimatedBytes += size
        }
    }

    override fun recordRule(
        operation: RuleStateOperation,
        time: Long,
        runtime: RuleRuntime,
        rule: ResolvedRule,
        state: RuleExecutionState,
        before: RuleStateValues,
        parameters: RuleExecutionParameters,
        prerequisites: Set<ResolvedRule>,
        status: RuleStatus?,
    ) =
        synchronized(lock) {
            if (sealed || incomplete != null) return@synchronized
            fun id(value: Any) = identities.getOrPut(value) { identities.size + 1L }
            val event =
                RuleStateRecord(
                    operation,
                    time,
                    id(runtime),
                    id(state),
                    rule.matchIdentity(),
                    id(state.actionCount),
                    id(state.actionTriggerTime),
                    before,
                    state.values(),
                    RuleStatePolicy(
                        parameters.actionMaximum,
                        parameters.actionCd,
                        parameters.matchDelay,
                        parameters.matchTime,
                        parameters.actionDelay,
                    ),
                    prerequisites.map { id(it.executionIdentity) },
                    runtime.lastTriggerRule?.let { id(it.executionIdentity) },
                    status?.name,
                )
            // Keep the rule's observation time in the payload; it does not advance scheduler time.
            recordStep(
                MatchSystemStep(
                    ++sequence,
                    steps.lastOrNull()?.timeMillis ?: startedAt,
                    ruleState = event,
                ),
            )
        }

    fun stop(now: Long, state: MatchScheduleCheckpoint) =
        synchronized(lock) {
            if (stoppedAt == null) {
                stoppedAt = now
                finishing = state.active?.id
                final = state
                if (finishing == null) {
                    sealed = true
                    identities.clear()
                }
            }
        }

    fun export(): MatchSystemRecording =
        synchronized(lock) {
            MatchSystemRecording(
                initial = initial,
                startedAtMillis = startedAt,
                steps = steps.toList(),
                final = final,
                sealed = sealed,
                incomplete = incomplete,
                stoppedAtMillis = stoppedAt,
                finishingRun = finishing,
            )
        }
}

// Admission accounting only: no JSON construction, escaping, or scanning string contents while
// holding scheduler/rule locks. Fixed allowances cover scalar fields; variable payloads are counted
// by length. This is a memory estimate, not an exact serialized-file-size limit.
private fun String?.estimatedBytes(): Long = if (this == null) 0 else 40L + length.toLong() * 2

private fun MatchScheduleRule?.estimatedBytes(): Long =
    if (this == null) 0 else 64L + identity.estimatedBytes()

private fun MatchScheduleRules?.estimatedBytes(): Long =
    if (this == null) 0 else 64L + rules.sumOf { it.estimatedBytes() }

private fun RawSubscription.Position?.estimatedBytes(): Long =
    if (this == null) 0
    else
        64L +
            left.estimatedBytes() +
            top.estimatedBytes() +
            right.estimatedBytes() +
            bottom.estimatedBytes() +
            x.estimatedBytes() +
            y.estimatedBytes()

private fun GkdAction?.estimatedBytes(): Long =
    if (this == null) 0
    else
        96L +
            selector.estimatedBytes() +
            action.estimatedBytes() +
            position.estimatedBytes() +
            (swipeArg?.let { 32L + it.start.estimatedBytes() + it.end.estimatedBytes() } ?: 0)

private fun MatchScheduledRun?.estimatedBytes(): Long =
    if (this == null) 0
    else
        128L +
            tracked.estimatedBytes() +
            query.estimatedBytes() +
            (action?.let { 64L + it.rule.estimatedBytes() + it.request.estimatedBytes() } ?: 0)

private fun MatchSystemStep.estimatedBytes(): Long {
    val commandBytes =
        command?.let { command ->
            256L +
                command.reason.estimatedBytes() +
                command.rule.estimatedBytes() +
                command.rules.estimatedBytes() +
                command.action.estimatedBytes() +
                command.queryOutcome?.failure.estimatedBytes() +
                (command.outcome?.let { outcome ->
                    128L +
                        outcome.errorType.estimatedBytes() +
                        outcome.message.estimatedBytes() +
                        outcome.result?.action.estimatedBytes() +
                        (outcome.result?.coordinateError?.let { error ->
                            128L +
                                error.field.estimatedBytes() +
                                error.code.estimatedBytes() +
                                error.source.estimatedBytes() +
                                error.detail.estimatedBytes() +
                                (error.inputs?.entries?.sumOf { it.key.estimatedBytes() + 32L }
                                    ?: 0)
                        } ?: 0)
                } ?: 0)
        } ?: 0
    val effectsBytes = effects.sumOf { 96L + it.reason.estimatedBytes() + it.run.estimatedBytes() }
    val ruleBytes =
        ruleState?.let {
            384L +
                it.identity.estimatedBytes() +
                it.status.estimatedBytes() +
                it.prerequisites.size.toLong() * 24
        } ?: 0
    return 64L + commandBytes + effectsBytes + ruleBytes
}
