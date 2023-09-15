package li.songe.gkd.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.appScope
import li.songe.gkd.data.Rule
import li.songe.gkd.util.appIdToRulesFlow

data class TopActivity(
    val appId: String,
    val activityId: String? = null,
    val sourceId: String? = null,
)

val topActivityFlow by lazy {
    MutableStateFlow<TopActivity?>(null)
}

val currentRulesFlow by lazy {
    combine(appIdToRulesFlow, topActivityFlow) { appIdToRules, topActivity ->
        (appIdToRules[topActivity?.appId] ?: emptyList()).filter { rule ->
            rule.matchActivityId(topActivity?.activityId) || rule.matchActivityId(topActivity?.sourceId)
        }
    }.stateIn(appScope, SharingStarted.Eagerly, emptyList())
}

val lastTriggerRuleFlow by lazy {
    MutableStateFlow<Rule?>(null)
}


fun isAvailableRule(rule: Rule): Boolean {
    if (!rule.active) return false // 处于冷却时间
    if (rule.preRules.isNotEmpty()) { // 需要提前点击某个规则
        lastTriggerRuleFlow.value ?: return false
        // 上一个点击的规则不在当前需要点击的列表
        return rule.preRules.any { it == lastTriggerRuleFlow.value }
    }
    if (rule.delayTriggerTime > 0) {
        if (rule.delayTriggerTime + rule.delay > System.currentTimeMillis()) {
            return false // 没有延迟完毕
        }
    }
    return true
}
