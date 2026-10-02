package li.gkd.app.rule

import kotlinx.atomicfu.atomic
import li.gkd.app.subscription.RawSubscription

sealed class ResolvedRule(
    val rule: RawSubscription.RawRuleProps,
    val g: ResolvedGroup,
    protected val runtime: RuleRuntime,
) {
    private val group get() = g.group
    val subsItem get() = g.subsItem
    val rawSubs get() = g.subscription
    val key get() = rule.key
    val index = group.rules.indexOfFirst { r -> r === rule }
    val excludeData get() = g.excludeData
    private val parameters = RuleExecutionParameters(rule, g.group)
    val matches get() = parameters.matches
    val anyMatches get() = parameters.anyMatches
    val excludeMatches get() = parameters.excludeMatches
    val excludeAllMatches get() = parameters.excludeAllMatches
    val matchDelay get() = parameters.matchDelay
    val actionDelay get() = parameters.actionDelay
    private val matchTime get() = parameters.matchTime
    private val forcedTime get() = parameters.forcedTime
    val matchOptions get() = parameters.matchOptions
    val matchRoot get() = parameters.matchRoot
    val order get() = parameters.order
    private val actionCdKey get() = parameters.actionCdKey
    private val actionMaximumKey get() = parameters.actionMaximumKey
    val priorityTime get() = parameters.priorityTime
    val priorityActionMaximum get() = parameters.priorityActionMaximum
    val priorityEnabled: Boolean
        get() = priorityTime > 0

    fun isPriority(): Boolean {
        if (!priorityEnabled) return false
        if (priorityActionMaximum <= actionCount.value) return false
        if (!status.ok) return false
        val t = runtime.now()
        return t - matchChangedTime.value < priorityTime + matchDelay
    }

    fun bindGroupRules(
        groupToRules: Map<out RawSubscription.RawGroupProps, List<ResolvedRule>>,
    ) {
        val selfGroupRules = groupToRules[group] ?: emptyList()
        val othersGroupRules =
            (group.scopeKeys ?: emptyList()).distinct().filter { k -> k != group.key }
                .flatMap { k ->
                    groupToRules.entries.find { e -> e.key.key == k }?.value ?: emptyList()
                }
        val groupRules = selfGroupRules + othersGroupRules

        // 共享次数
        if (actionMaximumKey != null) {
            val otherRule = groupRules.find { r -> r.key == actionMaximumKey }
            if (otherRule != null) {
                actionCount = otherRule.actionCount
            }
        }
        // 共享 cd
        if (actionCdKey != null) {
            val otherRule = groupRules.find { r -> r.key == actionCdKey }
            if (otherRule != null) {
                actionTriggerTime = otherRule.actionTriggerTime
            }
        }
        preRules = groupRules.filter { otherRule ->
            (otherRule.key != null) && parameters.preKeys.contains(
                otherRule.key
            )
        }.toSet()
    }

    private var preRules = emptySet<ResolvedRule>()

    private val actionDelayTriggerTime = atomic(0L)
    fun checkDelay(): Boolean {
        if (actionDelay > 0 && actionDelayTriggerTime.value == 0L) {
            actionDelayTriggerTime.value = runtime.now()
            return true
        }
        return false
    }

    fun checkForced(): Boolean {
        if (forcedTime <= 0) return false
        return runtime.now() < matchChangedTime.value + matchDelay + forcedTime
    }

    private var actionTriggerTime = atomic(0L)
    fun trigger() {
        val t = runtime.now()
        actionTriggerTime.value = t
        actionDelayTriggerTime.value = 0L
        actionCount.incrementAndGet()
        runtime.onTriggered(this, t)
    }

    private var actionCount = atomic(0)

    private val matchChangedTime = atomic(0L)
    val isFirstMatchApp: Boolean
        get() = matchChangedTime.value < runtime.appChangeTime

    private val matchLimitTime = (matchTime ?: 0) + matchDelay

    val resetMatchType = ResetMatchType.allSubObject.find {
        it.value == parameters.resetMatch
    } ?: ResetMatchType.Activity

    fun resetState(t: Long) {
        actionCount.value = 0
        actionDelayTriggerTime.value = 0L
        actionTriggerTime.value = 0
        runtime.onReset(this)
        matchChangedTime.value = t
    }

    fun onActivityTransition(time: Long, previouslyMatched: Boolean) {
        when (resetMatchType) {
            ResetMatchType.App -> if (isFirstMatchApp) resetState(time)
            ResetMatchType.Activity -> resetState(time)
            ResetMatchType.Match -> if (!previouslyMatched) resetState(time)
        }
    }

    val status: RuleStatus
        get() {
            val actionMaximum = parameters.actionMaximum
            if (actionMaximum != null) {
                if (actionCount.value >= actionMaximum) {
                    return RuleStatus.ActionLimitReached // 达到最大执行次数
                }
            }
            if (preRules.isNotEmpty() && !preRules.any { it === runtime.lastTriggerRule }) {
                return RuleStatus.PrerequisitePending // 需要提前触发某个规则
            }
            val t = runtime.now()
            val c = matchChangedTime.value
            if (matchDelay > 0 && t - c < matchDelay) {
                return RuleStatus.MatchDelay // 处于匹配延迟中
            }
            if (matchTime != null && t - c > matchLimitTime) {
                return RuleStatus.MatchTimeout // 超出匹配时间
            }
            if (actionTriggerTime.value + parameters.actionCd > t) {
                return RuleStatus.Cooldown // 处于冷却时间
            }
            val d = actionDelayTriggerTime.value
            if (d > 0) {
                if (d + actionDelay > t) {
                    return RuleStatus.ActionDelay // 处于触发延迟中
                }
            }
            return RuleStatus.Ready
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

