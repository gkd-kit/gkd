package li.songe.gkd.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.appScope
import li.songe.gkd.data.Rule
import li.songe.gkd.util.appIdToRulesFlow

val activityIdFlow by lazy {
    MutableStateFlow<String?>(null)
}

val appIdFlow by lazy {
    MutableStateFlow<String?>(null)
}

val currentRulesFlow by lazy {
    combine(appIdToRulesFlow, appIdFlow, activityIdFlow) { appIdToRules, appId, activityId ->
        (appIdToRules[appId] ?: emptyList()).filter { rule ->
            rule.matchActivityId(activityId)
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
    return true
}
