package li.songe.gkd.data

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.Job
import li.songe.gkd.a11y.appChangeTime
import li.songe.gkd.a11y.lastTriggerRule
import li.songe.gkd.a11y.lastTriggerTime
import li.songe.selector.MatchOption
import li.songe.selector.Selector

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
        (rule.matches ?: emptyList()).map { s -> group.cacheMap[s] ?: Selector.parse(s) }
    val anyMatches =
        (rule.anyMatches ?: emptyList()).map { s -> group.cacheMap[s] ?: Selector.parse(s) }
    val excludeMatches =
        (rule.excludeMatches ?: emptyList()).map { s -> group.cacheMap[s] ?: Selector.parse(s) }
    val excludeAllMatches =
        (rule.excludeAllMatches ?: emptyList()).map { s -> group.cacheMap[s] ?: Selector.parse(s) }

    private val resetMatch = rule.resetMatch ?: group.resetMatch
    val matchDelay = rule.matchDelay ?: group.matchDelay ?: 0L
    val actionDelay = rule.actionDelay ?: group.actionDelay ?: 0L
    private val matchTime = rule.matchTime ?: group.matchTime
    private val forcedTime = rule.forcedTime ?: group.forcedTime ?: 0L
    val matchOption = MatchOption(
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

    private val hasSlowSelector by lazy {
        (matches + excludeMatches + anyMatches + excludeAllMatches).any { s -> s.isSlow(matchOption) }
    }
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

    val isSlow by lazy { preKeys.isEmpty() && (matchTime == null || matchTime > 10_000L) && hasSlowSelector }

    var groupToRules: Map<out RawSubscription.RawGroupProps, List<ResolvedRule>> = emptyMap()
        set(value) {
            field = value
            val selfGroupRules = field[group] ?: emptyList()
            val othersGroupRules =
                (group.scopeKeys ?: emptyList()).distinct().filter { k -> k != group.key }
                    .map { k ->
                        field.entries.find { e -> e.key.key == k }?.value ?: emptyList()
                    }.flatten()
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
                (otherRule.key != null) && preKeys.contains(
                    otherRule.key
                )
            }.toSet()
        }

    private var preRules = emptySet<ResolvedRule>()
    val hasNext = group.rules.any { r -> r.preKeys?.any { k -> k == rule.key } == true }

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
        actionTriggerTime.value = System.currentTimeMillis()
        actionDelayTriggerTime.value = 0L
        actionCount.incrementAndGet()
        lastTriggerTime = actionTriggerTime.value
        lastTriggerRule = this
    }

    private var actionCount = atomic(0)

    private val matchChangedTime = atomic(0L)
    val isFirstMatchApp: Boolean
        get() = matchChangedTime.value == appChangeTime

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
    })

    fun performAction(node: AccessibilityNodeInfo): ActionResult {
        return performer.perform(node, rule.position)
    }

    val matchDelayJob = atomic<Job?>(null)

    val status: RuleStatus
        get() {
            if (actionMaximum != null) {
                if (actionCount.value >= actionMaximum) {
                    return RuleStatus.Status1 // 达到最大执行次数
                }
            }
            if (preRules.isNotEmpty() && !preRules.any { it === lastTriggerRule }) {
                return RuleStatus.Status2 // 需要提前触发某个规则
            }
            val t = System.currentTimeMillis()
            val c = matchChangedTime.value
            if (matchDelay > 0 && t - c < matchDelay) {
                return RuleStatus.Status3 // 处于匹配延迟中
            }
            if (matchTime != null && t - c > matchLimitTime) {
                return RuleStatus.Status4 // 超出匹配时间
            }
            if (actionTriggerTime.value + actionCd > t) {
                return RuleStatus.Status5 // 处于冷却时间
            }
            val d = actionDelayTriggerTime.value
            if (d > 0) {
                if (d + actionDelay > t) {
                    return RuleStatus.Status6 // 处于触发延迟中
                }
            }
            return RuleStatus.StatusOk
        }

    fun statusText(): String {
        return "id:${subsItem.id}, v:${rawSubs.version}, type:${type}, gKey=${group.key}, gName:${group.name}, index:${index}, key:${key}, status:${status.name}"
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

sealed class RuleStatus(val name: String) {
    data object StatusOk : RuleStatus("ok")
    data object Status1 : RuleStatus("达到最大执行次数")
    data object Status2 : RuleStatus("需要提前触发某个规则")
    data object Status3 : RuleStatus("处于匹配延迟")
    data object Status4 : RuleStatus("超出匹配时间")
    data object Status5 : RuleStatus("处于冷却时间")
    data object Status6 : RuleStatus("处于触发延迟")

    val ok: Boolean
        get() = this === StatusOk

    val alive: Boolean
        get() = this !== Status1 && this !== Status2 && this !== Status4
}

fun getFixActivityIds(
    appId: String,
    activityIds: List<String>?,
): List<String> {
    if (activityIds == null || activityIds.isEmpty()) return emptyList()
    return activityIds.map { activityId ->
        if (activityId.startsWith('.')) { // .a.b.c -> com.x.y.x.a.b.c
            appId + activityId
        } else {
            activityId
        }
    }
}
