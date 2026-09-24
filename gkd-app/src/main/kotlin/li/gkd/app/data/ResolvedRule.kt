package li.gkd.app.data

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.Job
import li.gkd.app.text.UiStrings
import li.gkd.app.a11y.appChangeTime
import li.gkd.app.a11y.lastTriggerRule
import li.gkd.app.a11y.lastTriggerTime
import li.gkd.app.store.AppStore
import li.gkd.selector.MatchOptions
import li.gkd.selector.Selector

sealed class ResolvedRule(
    val rule: RawSubscription.RawRuleProps,
    val g: ResolvedGroup,
) {
    private val group = g.group
    val subsItem = g.subsItem
    val rawSubs = g.subscription
    val key = rule.key
    val index = group.rules.indexOfFirst { r -> r === rule }
    val excludeData = g.excludeData
    private val preKeys = (rule.preKeys ?: emptyList()).toSet()
    val matches =
        (rule.matches ?: emptyList()).map { s -> group.cacheMap[s] ?: Selector.compile(s).value }
    val anyMatches =
        (rule.anyMatches ?: emptyList()).map { s -> group.cacheMap[s] ?: Selector.compile(s).value }
    val excludeMatches =
        (rule.excludeMatches ?: emptyList()).map { s ->
            group.cacheMap[s] ?: Selector.compile(s).value
        }
    val excludeAllMatches =
        (rule.excludeAllMatches ?: emptyList()).map { s ->
            group.cacheMap[s] ?: Selector.compile(s).value
        }

    private val resetMatch = rule.resetMatch ?: group.resetMatch
    val matchDelay = rule.matchDelay ?: group.matchDelay ?: 0L
    val actionDelay = rule.actionDelay ?: group.actionDelay ?: 0L
    private val matchTime = rule.matchTime ?: group.matchTime
    private val forcedTime = rule.forcedTime ?: group.forcedTime ?: 0L
    val matchOptions = MatchOptions(
        fastQuery = rule.fastQuery ?: group.fastQuery ?: false
    )
    val matchRoot = rule.matchRoot ?: group.matchRoot ?: false
    val order = rule.order ?: group.order ?: 0

    private val actionCdKey = rule.actionCdKey ?: group.actionCdKey
    private val actionCd = rule.actionCd ?: if (actionCdKey != null) {
        group.rules.find { r -> r.key == actionCdKey }?.actionCd
    } else {
        null
    } ?: group.actionCd ?: 1000L

    private val actionMaximumKey = rule.actionMaximumKey ?: group.actionMaximumKey
    private val actionMaximum = rule.actionMaximum ?: if (actionMaximumKey != null) {
        group.rules.find { r -> r.key == actionMaximumKey }?.actionMaximum
    } else {
        null
    } ?: group.actionMaximum

    val priorityTime = rule.priorityTime ?: group.priorityTime ?: 0
    val priorityActionMaximum = rule.priorityActionMaximum ?: group.priorityActionMaximum ?: 1
    val priorityEnabled: Boolean
        get() = priorityTime > 0

    fun isPriority(): Boolean {
        if (!priorityEnabled) return false
        if (priorityActionMaximum <= actionCount.value) return false
        if (!status.ok) return false
        val t = System.currentTimeMillis()
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

        // Share count
        if (actionMaximumKey != null) {
            val otherRule = groupRules.find { r -> r.key == actionMaximumKey }
            if (otherRule != null) {
                actionCount = otherRule.actionCount
            }
        }
        // Shared cd
        if (actionCdKey != null) {
            val otherRule = groupRules.find { r -> r.key == actionCdKey }
            if (otherRule != null) {
                actionTriggerTime = otherRule.actionTriggerTime
            }
        }
        preRules = groupRules.filter { otherRule ->
            (otherRule.key != null) && preKeys.contains(
                otherRule.key
            )
        }.toSet()
    }

    private var preRules = emptySet<ResolvedRule>()

    private var actionDelayTriggerTime = atomic(0L)
    val actionDelayJob = atomic<Job?>(null)
    fun checkDelay(): Boolean {
        if (actionDelay > 0 && actionDelayTriggerTime.value == 0L) {
            actionDelayTriggerTime.value = System.currentTimeMillis()
            return true
        }
        return false
    }

    fun checkForced(): Boolean {
        if (forcedTime <= 0) return false
        return System.currentTimeMillis() < matchChangedTime.value + matchDelay + forcedTime
    }

    private var actionTriggerTime = atomic(0L)
    fun trigger() {
        val t = System.currentTimeMillis()
        actionTriggerTime.value = t
        actionDelayTriggerTime.value = 0L
        actionCount.incrementAndGet()
        lastTriggerTime = t
        lastTriggerRule = this
        AppStore.incrementActionCount()
    }

    private var actionCount = atomic(0)

    private val matchChangedTime = atomic(0L)
    val isFirstMatchApp: Boolean
        get() = matchChangedTime.value < appChangeTime

    private val matchLimitTime = (matchTime ?: 0) + matchDelay

    val resetMatchType = ResetMatchType.allSubObject.find {
        it.value == resetMatch
    } ?: ResetMatchType.Activity

    fun resetState(t: Long) {
        actionCount.value = 0
        actionDelayTriggerTime.value = 0L
        actionTriggerTime.value = 0
        actionDelayJob.update { it?.cancel(); null }
        matchDelayJob.update { it?.cancel(); null }
        matchChangedTime.value = t
    }

    private val performer = ActionPerformer.getAction(rule.action ?: rule.position?.let {
        ActionPerformer.ClickCenter.action
    } ?: rule.swipeArg?.let {
        ActionPerformer.Swipe.action
    })

    suspend fun performAction(node: AccessibilityNodeInfo) = performer.perform(node, rule)

    val matchDelayJob = atomic<Job?>(null)

    val status: RuleStatus
        get() {
            if (actionMaximum != null) {
                if (actionCount.value >= actionMaximum) {
                    return RuleStatus.Status1 // Maximum execution count reached
                }
            }
            if (preRules.isNotEmpty() && !preRules.any { it === lastTriggerRule }) {
                return RuleStatus.Status2 // Need to trigger a rule in advance
            }
            val t = System.currentTimeMillis()
            val c = matchChangedTime.value
            if (matchDelay > 0 && t - c < matchDelay) {
                return RuleStatus.Status3 // In match delay
            }
            if (matchTime != null && t - c > matchLimitTime) {
                return RuleStatus.Status4 // Match time exceeded
            }
            if (actionTriggerTime.value + actionCd > t) {
                return RuleStatus.Status5 // In cooldown period
            }
            val d = actionDelayTriggerTime.value
            if (d > 0) {
                if (d + actionDelay > t) {
                    return RuleStatus.Status6 // In trigger delay
                }
            }
            return RuleStatus.StatusOk
        }

    fun statusText(): String {
        return "id:${subsItem.id}, v:${rawSubs.version}, type:${type}, gKey=${group.key}, gName:${group.name}, index:${index}, key:${key}, status:${status.name}"
    }

    abstract val type: String

    // The more precise the range, the higher the priority
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

sealed class RuleStatus(val name: String) {
    data object StatusOk : RuleStatus("ok")
    data object Status1 : RuleStatus(UiStrings.rule_status_maximum_actions)
    data object Status2 : RuleStatus(UiStrings.rule_status_prerequisite)
    data object Status3 : RuleStatus(UiStrings.rule_status_match_delay)
    data object Status4 : RuleStatus(UiStrings.rule_status_match_timeout)
    data object Status5 : RuleStatus(UiStrings.rule_status_cooldown)
    data object Status6 : RuleStatus(UiStrings.rule_status_action_delay)

    val ok: Boolean
        get() = this === StatusOk

    val alive: Boolean
        get() = this !== Status1 && this !== Status2 && this !== Status4
}
