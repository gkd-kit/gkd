package li.songe.gkd.data

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Job
import li.songe.gkd.service.TopActivity
import li.songe.gkd.service.lastTriggerAppRule
import li.songe.gkd.service.querySelector
import li.songe.selector.Selector

sealed class ResolvedRule(
    val matches: List<Selector>,
    val excludeMatches: List<Selector>,

    val actionDelay: Long,
    val quickFind: Boolean,

    val matchDelay: Long,
    val matchTime: Long?,
    val resetMatch: String?,

    val key: Int?,
    val preKeys: Set<Int>,

    val index: Int,
    val rule: RawSubscription.RawRuleProps,
    val group: RawSubscription.RawGroupProps,
    val rawSubs: RawSubscription,
    val subsItem: SubsItem,
) {
    var preAppRules: Set<ResolvedRule> = emptySet()
    var actionDelayTriggerTime = 0L
    var actionDelayJob: Job? = null
    fun checkDelay(): Boolean {
        if (actionDelay > 0 && actionDelayTriggerTime == 0L) {
            actionDelayTriggerTime = System.currentTimeMillis()
            return true
        }
        return false
    }

    val actionCd = (if (rule.actionCdKey != null) {
        group.rules.find { r -> r.key == rule.actionCdKey }?.actionCd ?: group.actionCd
    } else {
        null
    } ?: rule.actionCd ?: group.actionCd ?: 1000L)

    var actionTriggerTime = Value(0L)
    fun trigger() {
        actionTriggerTime.value = System.currentTimeMillis()
        // 重置延迟点
        actionDelayTriggerTime = 0L
        actionCount.value++
        lastTriggerAppRule = this
    }

    val actionMaximum = ((if (rule.actionMaximumKey != null) {
        group.rules.find { r -> r.key == rule.actionMaximumKey }?.actionMaximum
            ?: group.actionMaximum
    } else {
        null
    }) ?: rule.actionMaximum ?: group.actionMaximum)

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

    val statusCode: Int
        get() {
            if (actionMaximum != null) {
                if (actionCount.value >= actionMaximum) {
                    return 1 // 达到最大执行次数
                }
            }
            if (preAppRules.isNotEmpty()) { // 需要提前点击某个规则
                lastTriggerAppRule ?: return 2
                return if (preAppRules.any { it === lastTriggerAppRule }) {
                    0
                } else {
                    3 // 上一个点击的规则不在当前需要点击的列表
                }
            }
            val t = System.currentTimeMillis()
            if (matchDelay > 0 && t - matchChangedTime < matchDelay) {
                return 4 // 处于匹配延迟中
            }
            if (matchTime != null && t - matchChangedTime > matchLimitTime) {
                return 5 // 超出匹配时间
            }
            if (actionTriggerTime.value + actionCd > t) {
                return 6 // 处于冷却时间
            }
            if (actionDelayTriggerTime > 0) {
                if (actionDelayTriggerTime + actionDelay > t) {
                    return 7 // 处于点击延迟中
                }
            }
            return 0
        }

    abstract fun matchActivity(topActivity: TopActivity?): Boolean

}



