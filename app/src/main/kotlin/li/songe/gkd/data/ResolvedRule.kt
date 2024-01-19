package li.songe.gkd.data

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Job
import li.songe.gkd.service.lastTriggerRule
import li.songe.gkd.service.lastTriggerTime
import li.songe.gkd.service.querySelector
import li.songe.selector.Selector

sealed class ResolvedRule(
    val rule: RawSubscription.RawRuleProps,
    val group: RawSubscription.RawGroupProps,
    val rawSubs: RawSubscription,
    val subsItem: SubsItem,
    val exclude: String,
) {
    val key = rule.key
    val index = group.rules.indexOf(rule)
    val preKeys = (rule.preKeys ?: emptyList()).toSet()
    val resetMatch = rule.resetMatch ?: group.resetMatch
    val matches = rule.matches.map { s -> Selector.parse(s) }
    val excludeMatches = (rule.excludeMatches ?: emptyList()).map { s -> Selector.parse(s) }
    val matchDelay = rule.matchDelay ?: group.matchDelay ?: 0L
    val actionDelay = rule.actionDelay ?: group.actionDelay ?: 0L
    val matchTime = rule.matchTime ?: group.matchTime
    val quickFind = rule.quickFind ?: group.quickFind ?: false

    val actionCdKey = rule.actionCdKey ?: group.actionCdKey
    val actionCd = rule.actionCd ?: if (actionCdKey != null) {
        group.rules.find { r -> r.key == actionCdKey }?.actionCd
    } else {
        null
    } ?: group.actionCd ?: 1000L

    val actionMaximumKey = rule.actionMaximumKey ?: group.actionMaximumKey
    val actionMaximum = rule.actionMaximum ?: if (actionMaximumKey != null) {
        group.rules.find { r -> r.key == actionMaximumKey }?.actionMaximum
    } else {
        null
    } ?: group.actionMaximum

    var groupRules: List<ResolvedRule> = emptyList()
        set(value) {
            field = value
            // 共享次数
            if (actionMaximumKey != null) {
                val otherRule = field.find { r -> r.key == actionMaximumKey }
                if (otherRule != null) {
                    actionCount = otherRule.actionCount
                }
            }
            // 共享 cd
            if (actionCdKey != null) {
                val otherRule = field.find { r -> r.key == actionCdKey }
                if (otherRule != null) {
                    actionTriggerTime = otherRule.actionTriggerTime
                }
            }
            preRules = field.filter { otherRule ->
                (otherRule.key != null) && preKeys.contains(
                    otherRule.key
                )
            }.toSet()
        }

    var preRules = emptySet<ResolvedRule>()
    val hasNext = group.rules.any { r -> r.preKeys?.any { k -> k == rule.key } == true }

    var actionDelayTriggerTime = 0L
    var actionDelayJob: Job? = null
    fun checkDelay(): Boolean {
        if (actionDelay > 0 && actionDelayTriggerTime == 0L) {
            actionDelayTriggerTime = System.currentTimeMillis()
            return true
        }
        return false
    }

    var actionTriggerTime = Value(0L)
    fun trigger() {
        actionTriggerTime.value = System.currentTimeMillis()
        lastTriggerTime = actionTriggerTime.value
        // 重置延迟点
        actionDelayTriggerTime = 0L
        actionCount.value++
        lastTriggerRule = this
    }

    var actionCount = Value(0)

    var matchChangedTime = 0L

    val matchLimitTime = (matchTime ?: 0) + matchDelay

    val resetMatchTypeWhenActivity = when (resetMatch) {
        "app" -> false
        "activity" -> true
        else -> true
    }

    fun query(nodeInfo: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (nodeInfo == null) return null
        var target: AccessibilityNodeInfo? = null
        for (selector in matches) {
            target = nodeInfo.querySelector(selector, quickFind) ?: return null
        }
        for (selector in excludeMatches) {
            if (nodeInfo.querySelector(selector, quickFind) != null) return null
        }
        return target
    }

    val performAction = getActionFc(rule.action)

    var matchDelayJob: Job? = null

    val status: RuleStatus
        get() {
            if (actionMaximum != null) {
                if (actionCount.value >= actionMaximum) {
                    return RuleStatus.Status1 // 达到最大执行次数
                }
            }
            if (preRules.isNotEmpty()) { // 需要提前点击某个规则
                return if (preRules.any { it === lastTriggerRule }) {
                    RuleStatus.StatusOk
                } else {
                    RuleStatus.Status2
                }
            }
            val t = System.currentTimeMillis()
            if (matchDelay > 0 && t - matchChangedTime < matchDelay) {
                return RuleStatus.Status3 // 处于匹配延迟中
            }
            if (matchTime != null && t - matchChangedTime > matchLimitTime) {
                return RuleStatus.Status4 // 超出匹配时间
            }
            if (actionTriggerTime.value + actionCd > t) {
                return RuleStatus.Status5 // 处于冷却时间
            }
            if (actionDelayTriggerTime > 0) {
                if (actionDelayTriggerTime + actionDelay > t) {
                    return RuleStatus.Status6 // 处于点击延迟中
                }
            }
            return RuleStatus.StatusOk
        }

    fun statusText(): String {
        return "id:${subsItem.id}, v:${rawSubs.version}, type:${type}, gKey=${group.key}, gName:${group.name}, index:${index}, key:${key}, status:${status.name}"
    }

    val excludeData = ExcludeData.parse(exclude)

    abstract val type: String

    // 范围越精确, 优先级越高
    abstract fun matchActivity(appId: String, activityId: String? = null): Boolean

}

sealed class RuleStatus(val name: String) {
    data object StatusOk : RuleStatus("ok")
    data object Status1 : RuleStatus("达到最大执行次数")
    data object Status2 : RuleStatus("需要提前点击某个规则")
    data object Status3 : RuleStatus("处于匹配延迟")
    data object Status4 : RuleStatus("超出匹配时间")
    data object Status5 : RuleStatus("处于冷却时间")
    data object Status6 : RuleStatus("处于点击延迟")
}

fun getFixActivityIds(
    appId: String,
    activityIds: List<String>?,
): List<String> {
    activityIds ?: return emptyList()
    return activityIds.map { activityId ->
        if (activityId.startsWith('.')) { // .a.b.c -> com.x.y.x.a.b.c
            appId + activityId
        } else {
            activityId
        }
    }
}


