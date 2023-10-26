package li.songe.gkd.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.appScope
import li.songe.gkd.data.Rule
import li.songe.gkd.util.appIdToRulesFlow
import li.songe.gkd.util.launchTry
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

val activityChangeTimeFlow by lazy {
    MutableStateFlow(System.currentTimeMillis()).apply {
        appScope.launchTry {
            topActivityFlow.collect {
                this@apply.value = System.currentTimeMillis()
            }
        }
    }
}

val appChangeTimeFlow by lazy {
    topActivityFlow.map(appScope) { t -> t?.appId }.map(appScope) { System.currentTimeMillis() }
}

fun isAvailableRule(rule: Rule): Boolean {
    if (rule.resetMatchTypeWhenActivity) {
        if (activityChangeTimeFlow.value != rule.matchChangeTime) {
            // 当 界面 更新时, 重置操作延迟点, 重置点击次数
            rule.actionDelayTriggerTime = 0
            rule.actionCount = 0
            rule.matchChangeTime = activityChangeTimeFlow.value
        }
    } else {
        if (appChangeTimeFlow.value != rule.matchChangeTime) {
            // 当 切换APP 时, 重置点击次数
            rule.actionDelayTriggerTime = 0
            rule.actionCount = 0
            rule.matchChangeTime = appChangeTimeFlow.value
        }
    }
    if (rule.actionMaximum != null) {
        if (rule.actionCount >= rule.actionMaximum) {
            return false // 达到最大执行次数
        }
    }
    val t = System.currentTimeMillis()
    if (rule.matchDelay != null) {
        // 处于匹配延迟中
        if (rule.resetMatchTypeWhenActivity) {
            if (t - activityChangeTimeFlow.value < rule.matchDelay) {
                return false
            }
        } else {
            if (t - appChangeTimeFlow.value < rule.matchDelay) {
                return false
            }
        }
    }
    if (rule.matchTime != null) {
        // 超出了匹配时间
        if (rule.resetMatchTypeWhenActivity) {
            if (t - activityChangeTimeFlow.value > rule.matchAllTime) {
                return false
            }
        } else {
            if (t - appChangeTimeFlow.value > rule.matchAllTime) {
                return false
            }
        }
    }
    if (rule.actionTriggerTime + rule.actionCd > t) return false // 处于冷却时间
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


