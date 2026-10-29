package li.gkd.app.matching.diagnostics

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import li.gkd.app.matching.MatchScheduleCheckpoint
import li.gkd.app.matching.MatchScheduleCommand
import li.gkd.app.matching.MatchScheduleCommandType
import li.gkd.app.matching.MatchScheduleEffect
import li.gkd.app.rule.ResolvedRule
import li.gkd.app.rule.RuleExecutionParameters
import li.gkd.app.rule.RuleExecutionState
import li.gkd.app.rule.RuleRuntime
import li.gkd.app.rule.RuleStateObserver
import li.gkd.app.rule.RuleStateOperation
import li.gkd.app.rule.RuleStateValues
import li.gkd.app.rule.RuleStatus

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
    private val maxChars: Int = 8_000_000,
) : RuleStateObserver {
    private val lock = Any()
    private val steps = mutableListOf<MatchSystemStep>()
    private var final = initial
    private var sequence = 0L
    private var chars = 0
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
        val size = Json.encodeToString(step).length
        if (steps.size >= capacity || chars + size > maxChars) {
            incomplete = "system_recording_limit"
        } else {
            steps += step
            chars += size
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
