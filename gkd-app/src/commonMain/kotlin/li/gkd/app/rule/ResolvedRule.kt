package li.gkd.app.rule

import li.gkd.app.resources.*
import li.gkd.app.subscription.RawSubscription

sealed class ResolvedRule(
    val rule: RawSubscription.RawRuleProps,
    val g: ResolvedGroup,
    protected val runtime: RuleRuntime,
) {
    fun matchIdentity(): String =
        rule.matchIdentity(subsItem.id, g.group, (this as? AppRule)?.appId, index)

    fun statusText(): String {
        val description = status.descriptionResource?.getSync() ?: "ok"
        return "id:${subsItem.id}, v:${rawSubs.version}, type:${type}, gKey=${g.group.key}, gName:${g.group.name}, index:${index}, key:${key}, status:$description"
    }

    private var state = RuleExecutionState()
    private var dependencies = emptySet<ResolvedRule>()

    fun sameExecution(other: ResolvedRule): Boolean = state === other.state

    val executionIdentity: Any
        get() = state

    fun retainExecution(previous: ResolvedRule) =
        synchronized(runtime) {
            state = previous.state
        }

    fun executionDependencies(): Set<ResolvedRule> = dependencies

    fun sharesState(other: ResolvedRule): Boolean =
        state.actionCount === other.state.actionCount ||
            state.actionTriggerTime === other.state.actionTriggerTime ||
            (actionCdKey != null && other.key == actionCdKey) ||
            (actionMaximumKey != null && other.key == actionMaximumKey)

    fun sameDefinition(other: ResolvedRule): Boolean =
        parameters == other.parameters && excludeData == other.excludeData && sameScope(other)

    protected abstract fun sameScope(other: ResolvedRule): Boolean

    private val group
        get() = g.group

    val subsItem
        get() = g.subsItem

    val rawSubs
        get() = g.subscription

    val key
        get() = rule.key

    val index = group.rules.indexOfFirst { r -> r === rule }
    val excludeData
        get() = g.excludeData

    val parameters = RuleExecutionParameters.resolve(rule, g.group)
    val matches = parameters.matches.map(group.compilation::selector)
    val anyMatches = parameters.anyMatches.map(group.compilation::selector)
    val excludeMatches = parameters.excludeMatches.map(group.compilation::selector)
    val excludeAllMatches = parameters.excludeAllMatches.map(group.compilation::selector)
    val matchDelay
        get() = parameters.matchDelay

    val actionDelay
        get() = parameters.actionDelay

    private val matchTime
        get() = parameters.matchTime

    private val forcedTime
        get() = parameters.forcedTime

    val matchOptions
        get() = parameters.matchOptions

    val matchRoot
        get() = parameters.matchRoot

    val order
        get() = parameters.order

    private val actionCdKey
        get() = parameters.actionCdKey

    private val actionMaximumKey
        get() = parameters.actionMaximumKey

    val priorityTime
        get() = parameters.priorityTime

    val priorityActionMaximum
        get() = parameters.priorityActionMaximum

    val priorityEnabled: Boolean
        get() = priorityTime > 0

    fun isPriority(): Boolean =
        synchronized(runtime) {
            if (!priorityEnabled) return@synchronized false
            if (priorityActionMaximum <= state.actionCount.value) return@synchronized false
            if (!status.ok) return@synchronized false
            val t = runtime.now()
            return@synchronized t - state.matchChangedTime.value < priorityTime + matchDelay
        }

    fun bindGroupRules(
        groupToRules: Map<out RawSubscription.RawGroupProps, List<ResolvedRule>>,
    ) {
        val selfGroupRules = groupToRules[group] ?: emptyList()
        val othersGroupRules =
            (group.scopeKeys ?: emptyList())
                .distinct()
                .filter { k -> k != group.key }
                .flatMap { k ->
                    groupToRules.entries.find { e -> e.key.key == k }?.value ?: emptyList()
                }
        val groupRules = selfGroupRules + othersGroupRules
        val sharedDependencies = mutableSetOf<ResolvedRule>()

        // 共享次数
        if (actionMaximumKey != null) {
            val otherRule = groupRules.find { r -> r.key == actionMaximumKey }
            if (otherRule != null) {
                state.shareCount(otherRule.state)
                sharedDependencies += otherRule
            }
        }
        // 共享 cd
        if (actionCdKey != null) {
            val otherRule = groupRules.find { r -> r.key == actionCdKey }
            if (otherRule != null) {
                state.shareCooldown(otherRule.state)
                sharedDependencies += otherRule
            }
        }
        preRules =
            groupRules
                .filter { otherRule ->
                    (otherRule.key != null) &&
                        parameters.preKeys.contains(
                            otherRule.key,
                        )
                }
                .toSet()
        dependencies = preRules + sharedDependencies
    }

    private var preRules = emptySet<ResolvedRule>()

    private fun transition(operation: RuleTransition, time: Long) {
        val recorder = runtime.observer()
        val before = recorder?.let { state.values() }
        state.transition(operation, time)
        if (recorder != null && before != null)
            recorder.recordRule(
                operation.recordOperation,
                time,
                runtime,
                this,
                state,
                before,
                parameters,
                preRules,
                null,
            )
    }

    fun checkDelay(): Boolean =
        synchronized(runtime) {
            if (actionDelay > 0 && state.actionDelayTriggerTime.value == 0L) {
                transition(RuleTransition.Delay, runtime.now())
                return@synchronized true
            }
            return@synchronized false
        }

    fun checkForced(): Boolean =
        synchronized(runtime) {
            if (forcedTime <= 0) return@synchronized false
            return@synchronized runtime.now() <
                state.matchChangedTime.value + matchDelay + forcedTime
        }

    fun trigger(): Unit =
        synchronized(runtime) {
            val t = runtime.now()
            transition(RuleTransition.Trigger, t)
            runtime.onTriggered(this, t)
        }

    val isFirstMatchApp: Boolean
        get() = state.matchChangedTime.value < runtime.appChangeTime

    fun resetState(t: Long): Unit =
        synchronized(runtime) {
            transition(RuleTransition.Reset, t)
            runtime.onReset(this)
        }

    fun onActivityTransition(time: Long, previouslyMatched: Boolean): Unit =
        synchronized(runtime) {
            when (parameters.resetMatchType) {
                ResetMatchType.App -> if (isFirstMatchApp) resetState(time)
                ResetMatchType.Activity -> resetState(time)
                ResetMatchType.Match -> if (!previouslyMatched) resetState(time)
            }
        }

    val status: RuleStatus
        get() =
            synchronized(runtime) {
                val count = state.actionCount.value
                val maximum = parameters.actionMaximum
                val limited = maximum != null && count >= maximum
                val prerequisite =
                    limited ||
                        preRules.isEmpty() ||
                        preRules.any { candidate ->
                            runtime.lastTriggerRule?.let(candidate::sameExecution) == true
                        }
                val t = if (!prerequisite || limited) 0L else runtime.now()
                val result =
                    evaluateRuleStatus(
                        count,
                        maximum,
                        prerequisite,
                        t,
                        state.matchChangedTime.value,
                        matchDelay,
                        matchTime,
                        state.actionTriggerTime.value,
                        parameters.actionCd,
                        state.actionDelayTriggerTime.value,
                        actionDelay,
                    )
                runtime.observer()?.let {
                    it.recordRule(
                        RuleStateOperation.Status,
                        t,
                        runtime,
                        this,
                        state,
                        state.values(),
                        parameters,
                        preRules,
                        result,
                    )
                }
                result
            }

    abstract val type: String

    // 范围越精确, 优先级越高
    abstract fun matchActivity(appId: String, activityId: String? = null): Boolean
}

sealed class ResetMatchType(val value: String) {
    data object Activity : ResetMatchType("activity")

    data object Match : ResetMatchType("match")

    data object App : ResetMatchType("app")

    companion object {
        val allSubObject by lazy { listOf(Activity, Match, App) }
    }
}

fun RawSubscription.RawRuleProps.matchIdentity(
    subsId: Long,
    group: RawSubscription.RawGroupProps,
    appId: String?,
    index: Int,
): String =
    "$subsId/${group.groupType}/${appId ?: "*"}/${group.key}/${key?.toString() ?: "index:$index"}"
