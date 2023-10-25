package li.songe.gkd.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.appScope
import li.songe.gkd.data.Rule
import li.songe.gkd.util.appIdToRulesFlow
import li.songe.gkd.util.map

val launcherActivityIdFlow by lazy {
    MutableStateFlow<String?>(null)
}

data class TopActivity(
    val appId: String,
    val activityId: String? = null,
)

val topActivityFlow by lazy {
    MutableStateFlow<TopActivity?>(null)
}

val currentRulesFlow by lazy {
    combine(appIdToRulesFlow, topActivityFlow) { appIdToRules, topActivity ->
        (appIdToRules[topActivity?.appId] ?: emptyList()).filter { rule ->
            rule.matchActivityId(topActivity?.activityId)
        }
    }.stateIn(appScope, SharingStarted.Eagerly, emptyList())
}

val lastTriggerRuleFlow by lazy {
    MutableStateFlow<Rule?>(null)
}

private val activityIdChangeTimeFlow by lazy { topActivityFlow.map(appScope) { System.currentTimeMillis() } }

fun isAvailableRule(rule: Rule): Boolean {
    if (rule.actionMaximum != null) {
        if (activityIdChangeTimeFlow.value != rule.activityIdChangeTime) {
            // 当 界面 改变时, 重置点击次数
            rule.actionCount = 0
            rule.activityIdChangeTime = activityIdChangeTimeFlow.value
        }
        if (rule.actionCount >= rule.actionMaximum) {
            return false // 达到最大执行次数
        }
    }
    val t = System.currentTimeMillis()
    if (rule.matchDelay != null && t - activityIdChangeTimeFlow.value < rule.matchDelay) {
        return false // 处于匹配延迟中
    }
    if (rule.matchTime != null && t - activityIdChangeTimeFlow.value > rule.matchAllTime) {
        return false // 超出了匹配时间
    }
    if (!rule.notInCd) return false // 处于冷却时间
    if (rule.preRules.isNotEmpty()) { // 需要提前点击某个规则
        lastTriggerRuleFlow.value ?: return false
        // 上一个点击的规则不在当前需要点击的列表
        return rule.preRules.any { it === lastTriggerRuleFlow.value }
    }
    if (rule.actionDelayTriggerTime > 0) {
        if (rule.actionDelayTriggerTime + rule.actionDelay > t) {
            return false // 没有延迟完毕
        }
    }
    return true
}


