package li.gkd.app.matching

import kotlinx.serialization.Serializable
import li.gkd.app.matching.input.MatchTimingPolicy
import li.gkd.app.model.ActionResult
import li.gkd.app.model.GkdAction
import li.gkd.app.rule.RuleDelayKind

@Serializable
data class MatchScheduleRule(
    val instance: Long,
    val execution: Long,
    val identity: String,
    val priority: Boolean,
)

@Serializable
data class MatchScheduleRules(val id: Long, val rules: List<MatchScheduleRule>) {
    fun selection(execution: Long?): List<Long>? {
        if (execution == null) return rules.map { it.execution }
        val index = rules.indexOfFirst { it.execution == execution }
        if (index < 0) return null
        return rules.filterIndexed { i, rule -> i <= index || rule.priority }.map { it.execution }
    }
}

@Serializable
data class MatchScheduledTask(
    val task: Long,
    val deadline: Long,
    val scheduledAt: Long,
    val rule: MatchScheduleRule? = null,
    val kind: RuleDelayKind? = null,
)

@Serializable
data class MatchScheduledRun(
    val id: Long,
    val task: Long,
    val generation: Long,
    val cancelling: Boolean = false,
    val tracked: MatchScheduleRules? = null,
    val selected: Long? = null,
    val action: MatchScheduledAction? = null,
    val query: MatchScheduleRule? = null,
)

@Serializable
data class MatchQueryOutcome(val matched: Boolean? = null, val failure: String? = null)

@Serializable
data class MatchScheduledAction(
    val rule: MatchScheduleRule,
    val request: GkdAction,
    val startedAt: Long,
)

@Serializable
data class MatchActionOutcome(
    val kind: MatchActionOutcomeKind,
    val result: ActionResult? = null,
    val errorType: String? = null,
    val message: String? = null,
)

@Serializable
data class MatchScheduleCheckpoint(
    val service: Long? = null,
    val generation: Long = 0,
    val nextRun: Long = 0,
    val closed: Boolean = false,
    val active: MatchScheduledRun? = null,
    val pending: Long? = null,
    val deadlines: List<MatchScheduledTask> = emptyList(),
    val timer: Long = 0,
    val rules: MatchScheduleRules? = null,
    val policy: MatchTimingPolicy = MatchTimingPolicy(),
)

@Serializable
data class MatchScheduleCommand(
    val type: MatchScheduleCommandType,
    val service: Long? = null,
    val task: Long? = null,
    val run: Long? = null,
    val reason: String? = null,
    val rule: MatchScheduleRule? = null,
    val kind: RuleDelayKind? = null,
    val delayMillis: Long = 0,
    val timer: Long? = null,
    val rules: MatchScheduleRules? = null,
    val policy: MatchTimingPolicy? = null,
    val action: GkdAction? = null,
    val outcome: MatchActionOutcome? = null,
    val queryOutcome: MatchQueryOutcome? = null,
    val invalidationReason: MatchInvalidationReason? = null,
)

@Serializable
data class MatchScheduleEffect(
    val type: MatchScheduleEffectType,
    val run: MatchScheduledRun? = null,
    val reason: String? = null,
    val timer: Long? = null,
    val deadline: Long? = null,
    val canCommit: Boolean? = null,
)

/** The production scheduling decision machine. It performs no IO and does not own coroutines. */
class MatchScheduleMachine {
    var state: MatchScheduleCheckpoint = MatchScheduleCheckpoint()
        private set

    fun apply(command: MatchScheduleCommand, now: Long): List<MatchScheduleEffect> {
        val effects = mutableListOf<MatchScheduleEffect>()
        fun arm() {
            state = state.copy(timer = state.timer + 1)
            effects +=
                MatchScheduleEffect(
                    MatchScheduleEffectType.Arm,
                    timer = state.timer,
                    deadline = state.deadlines.minOfOrNull { it.deadline },
                )
        }
        fun cancel(reason: String) {
            state.active?.let {
                state = state.copy(active = it.copy(cancelling = true))
                effects +=
                    MatchScheduleEffect(
                        MatchScheduleEffectType.Cancel,
                        run = state.active,
                        reason = reason,
                    )
            }
        }
        fun invalidate(reason: MatchInvalidationReason) {
            state =
                state.copy(
                    generation = state.generation + 1,
                    pending = null,
                    deadlines = emptyList(),
                )
            arm()
            // A foreground transition invalidates future work, but may be the result
            // of the gesture already in progress. Keep that gesture's execution lane.
            if (
                state.active?.action == null ||
                    (reason != MatchInvalidationReason.PageChanged &&
                        reason != MatchInvalidationReason.ForegroundConflict)
            )
                cancel(reason.wireValue)
        }
        fun start() {
            if (state.active != null || state.service == null || state.closed) return
            val task = state.pending ?: return
            val run = MatchScheduledRun(state.nextRun + 1, task, state.generation)
            state = state.copy(active = run, pending = null, nextRun = run.id)
            effects += MatchScheduleEffect(MatchScheduleEffectType.Start, run)
        }
        fun reject(reason: String) {
            effects += MatchScheduleEffect(MatchScheduleEffectType.Reject, reason = reason)
        }
        when (command.type) {
            MatchScheduleCommandType.Attach ->
                if (state.service != command.service) {
                    invalidate(MatchInvalidationReason.ServiceChanged)
                    state = state.copy(service = requireNotNull(command.service))
                }
            MatchScheduleCommandType.Detach ->
                if (state.service == command.service) {
                    invalidate(MatchInvalidationReason.ServiceDisconnected)
                    state = state.copy(service = null)
                }
            MatchScheduleCommandType.Invalidate ->
                invalidate(requireNotNull(command.invalidationReason))
            MatchScheduleCommandType.Close -> {
                invalidate(MatchInvalidationReason.ApplicationClosed)
                state = state.copy(service = null, closed = true)
            }
            MatchScheduleCommandType.Policy -> {
                val policy = requireNotNull(command.policy)
                if (state.policy != policy) {
                    val deadlines =
                        state.deadlines.map {
                            if (it.rule == null)
                                it.copy(deadline = it.scheduledAt + policy.followUpMillis)
                            else it
                        }
                    state = state.copy(policy = policy, deadlines = deadlines)
                    if (deadlines.any { it.rule == null }) arm()
                }
            }
            MatchScheduleCommandType.Request,
            MatchScheduleCommandType.Execute -> {
                if (state.closed || state.service != command.service)
                    reject(if (state.closed) "engine_closed" else "service_unavailable")
                else if (command.type == MatchScheduleCommandType.Execute && state.active != null)
                    reject("busy")
                else {
                    state = state.copy(pending = requireNotNull(command.task))
                    start()
                }
            }
            MatchScheduleCommandType.FollowUp,
            MatchScheduleCommandType.RuleDelay -> {
                if (state.closed || state.service != command.service) reject("service_unavailable")
                else {
                    val rule =
                        if (command.type == MatchScheduleCommandType.RuleDelay)
                            requireNotNull(command.rule)
                        else null
                    val exists =
                        state.deadlines.any {
                            it.rule?.instance == rule?.instance && it.kind == command.kind
                        }
                    if (rule == null || !exists) {
                        val task =
                            MatchScheduledTask(
                                requireNotNull(command.task),
                                now +
                                    if (rule == null) state.policy.followUpMillis
                                    else command.delayMillis,
                                now,
                                rule,
                                command.kind,
                            )
                        state =
                            state.copy(
                                deadlines =
                                    state.deadlines.filterNot { rule == null && it.rule == null } +
                                        task,
                            )
                        arm()
                    }
                }
            }
            MatchScheduleCommandType.CancelDelays -> {
                val instance = requireNotNull(command.rule).instance
                val deadlines = state.deadlines.filterNot { it.rule?.instance == instance }
                if (deadlines.size != state.deadlines.size) {
                    state = state.copy(deadlines = deadlines)
                    arm()
                }
            }
            MatchScheduleCommandType.Wake ->
                if (state.timer == command.timer && !state.closed) {
                    val due = state.deadlines.filter { it.deadline <= now }
                    val next = due.firstOrNull { it.rule != null } ?: due.firstOrNull()
                    state =
                        state.copy(
                            deadlines = state.deadlines.filter { it.deadline > now },
                            pending = next?.task ?: state.pending,
                        )
                    if (next != null) start()
                    arm()
                }
            MatchScheduleCommandType.Rules -> {
                val rules = requireNotNull(command.rules)
                state.active
                    ?.takeIf { it.generation == state.generation }
                    ?.let { active ->
                        active.tracked?.let { old ->
                            val before = old.selection(active.selected)
                            val after = rules.selection(active.selected)
                            if (before == null || after == null || before != after)
                                cancel("rule_dependencies_changed")
                            else state = state.copy(active = active.copy(tracked = rules))
                        }
                    }
                val deadlines =
                    state.deadlines
                        .mapNotNull { task ->
                            if (task.rule == null) task
                            else
                                rules.rules
                                    .find { it.execution == task.rule.execution }
                                    ?.let { task.copy(rule = it) }
                        }
                        .asReversed()
                        .distinctBy { it.rule?.instance to it.kind }
                        .asReversed()
                state = state.copy(rules = rules, deadlines = deadlines)
                arm()
            }
            MatchScheduleCommandType.Track -> {
                if (state.active?.id != command.run || state.rules?.id != command.rules?.id)
                    reject("rule_snapshot_changed")
                else state = state.copy(active = state.active?.copy(tracked = state.rules))
            }
            MatchScheduleCommandType.Select -> {
                val active = state.active
                if (
                    active == null ||
                        active.id != command.run ||
                        (active.tracked != null &&
                            state.rules?.rules?.none { it.execution == command.rule?.execution } !=
                                false)
                )
                    reject("rule_dependencies_changed")
                else
                    state =
                        state.copy(
                            active = active.copy(selected = requireNotNull(command.rule).execution),
                        )
            }
            MatchScheduleCommandType.Cancel ->
                if (state.active?.id == command.run) cancel(requireNotNull(command.reason))
            MatchScheduleCommandType.QueryStart -> {
                val active = requireNotNull(state.active)
                require(active.id == command.run && active.query == null && active.action == null)
                state = state.copy(active = active.copy(query = requireNotNull(command.rule)))
            }
            MatchScheduleCommandType.QueryEnd -> {
                val active = requireNotNull(state.active)
                require(active.id == command.run && active.query == command.rule)
                val outcome = requireNotNull(command.queryOutcome)
                require((outcome.matched != null) != (outcome.failure != null))
                state = state.copy(active = active.copy(query = null))
            }
            MatchScheduleCommandType.ActionStart -> {
                val active = requireNotNull(state.active)
                require(
                    active.id == command.run &&
                        active.generation == state.generation &&
                        !active.cancelling &&
                        active.action == null &&
                        active.query == null,
                ) {
                    "Action cannot start in this run"
                }
                state =
                    state.copy(
                        active =
                            active.copy(
                                action =
                                    MatchScheduledAction(
                                        requireNotNull(command.rule),
                                        requireNotNull(command.action),
                                        now,
                                    ),
                            ),
                    )
            }
            MatchScheduleCommandType.ActionEnd -> {
                val active = requireNotNull(state.active)
                require(active.id == command.run && active.action != null) {
                    "Action completion has no matching start"
                }
                val outcome = requireNotNull(command.outcome)
                require(
                    (outcome.kind == MatchActionOutcomeKind.Returned) == (outcome.result != null),
                )
                // The platform function has returned/thrown, including its structured cleanup.
                // Cancellation is a request, never an early release of the execution lane.
                state = state.copy(active = active.copy(action = null))
            }
            MatchScheduleCommandType.Finish -> {
                require(state.active?.id == command.run) {
                    "Completion does not belong to the active run"
                }
                require(state.active?.action == null) {
                    "Run completed before action cleanup returned"
                }
                require(state.active?.query == null) { "Run completed before query returned" }
                state = state.copy(active = null)
                start()
            }
            MatchScheduleCommandType.Commit ->
                effects +=
                    MatchScheduleEffect(
                        MatchScheduleEffectType.Commit,
                        canCommit =
                            canCommit(
                                requireNotNull(command.run),
                                requireNotNull(command.rule).execution,
                            ),
                    )
        }
        return effects
    }

    fun canCommit(run: Long, execution: Long): Boolean =
        state.active?.let { it.id == run && it.generation == state.generation && !it.cancelling } ==
            true && state.rules?.rules?.any { it.execution == execution } == true
}
