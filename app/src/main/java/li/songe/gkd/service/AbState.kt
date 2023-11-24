package li.songe.gkd.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.appScope
import li.songe.gkd.data.ClickLog
import li.songe.gkd.data.Rule
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.appIdToRulesFlow
import li.songe.gkd.util.increaseClickCount
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.recordStoreFlow

data class TopActivity(
    val appId: String,
    val activityId: String? = null,
)

val topActivityFlow by lazy {
    MutableStateFlow<TopActivity?>(null)
}

data class ActivityRule(
    val rules: List<Rule> = emptyList(),
    val topActivity: TopActivity? = null,
    val appIdToRules: Map<String, List<Rule>> = emptyMap(),
)

val activityRuleFlow by lazy { MutableStateFlow(ActivityRule()) }

private var lastTopActivity: TopActivity? = null

private fun getFixTopActivity(): TopActivity? {
    val top = topActivityFlow.value ?: return null
    if (top.activityId == null) {
        if (lastTopActivity?.appId == top.appId) {
            // 当从通知栏上拉返回应用等时, activityId 的无障碍事件不会触发, 此时复用上一次获得的 activityId 填充
            topActivityFlow.value = lastTopActivity
        }
    } else {
        // 仅保留最近的有 activityId 的单个 TopActivity
        lastTopActivity = top
    }
    return topActivityFlow.value
}

fun getCurrentRules(): ActivityRule {
    val topActivity = getFixTopActivity()
    val activityRule = activityRuleFlow.value
    val appIdToRules = appIdToRulesFlow.value
    val idChanged = topActivity?.appId != activityRule.topActivity?.appId
    val topChanged = activityRule.topActivity != topActivity
    if (topChanged || activityRule.appIdToRules !== appIdToRules) {
        activityRuleFlow.value =
            ActivityRule(rules = (appIdToRules[topActivity?.appId] ?: emptyList()).filter { rule ->
                rule.matchActivityId(topActivity?.activityId)
            }, topActivity = topActivity, appIdToRules = appIdToRules)
    }
    if (topChanged) {
        val t = System.currentTimeMillis()
        if (idChanged) {
            appChangeTime = t
            openAdOptimized = null
        }
        activityChangeTime = t
        if (openAdOptimized == null && t - appChangeTime < openAdOptimizedTime) {
            openAdOptimized = activityRuleFlow.value.rules.any { r -> r.isOpenAd }
        }
    }
    return activityRuleFlow.value
}

var lastTriggerRule: Rule? = null
var appChangeTime = 0L
var activityChangeTime = 0L

// null: app 切换过
// true: 需要执行优化(此界面组需要存在开屏广告)
// false: 执行优化过了/已经过了优化时间
@Volatile
var openAdOptimized: Boolean? = null
const val openAdOptimizedTime = 5000L

fun isAvailableRule(rule: Rule): Boolean {
    val t = System.currentTimeMillis()
    if (openAdOptimized == true) {
        if (t - appChangeTime > openAdOptimizedTime) {
            openAdOptimized = false
        }
    }
    if (rule.resetMatchTypeWhenActivity) {
        if (activityChangeTime != rule.matchChangeTime) {
            // 当 界面 更新时, 重置操作延迟点, 重置点击次数
            rule.actionDelayTriggerTime = 0
            rule.actionCount.value = 0
            rule.matchChangeTime = activityChangeTime
        }
    } else {
        if (appChangeTime != rule.matchChangeTime) {
            // 当 切换APP 时, 重置点击次数
            rule.actionDelayTriggerTime = 0
            rule.actionCount.value = 0
            rule.matchChangeTime = appChangeTime
        }
    }
    if (rule.actionMaximum != null) {
        if (rule.actionCount.value >= rule.actionMaximum) {
            return false // 达到最大执行次数
        }
    }
    if (rule.matchDelay != null) {
        // 处于匹配延迟中
        if (rule.resetMatchTypeWhenActivity) {
            if (t - activityChangeTime < rule.matchDelay) {
                return false
            }
        } else {
            if (t - appChangeTime < rule.matchDelay) {
                return false
            }
        }
    }
    if (rule.matchTime != null) {
        // 超出了匹配时间
        if (rule.resetMatchTypeWhenActivity) {
            if (t - activityChangeTime > rule.matchAllTime) {
                return false
            }
        } else {
            if (t - appChangeTime > rule.matchAllTime) {
                return false
            }
        }
    }
    if (rule.actionTriggerTime.value + rule.actionCd > t) return false // 处于冷却时间
    if (rule.preRules.isNotEmpty()) { // 需要提前点击某个规则
        lastTriggerRule ?: return false
        // 上一个点击的规则不在当前需要点击的列表
        return rule.preRules.any { it === lastTriggerRule }
    }
    if (rule.actionDelayTriggerTime > 0) {
        if (rule.actionDelayTriggerTime + rule.actionDelay > t) {
            return false // 没有延迟完毕
        }
    }
    return true
}

fun insertClickLog(rule: Rule) {
    rule.trigger()
    toastClickTip()
    appScope.launchTry(Dispatchers.IO) {
        val clickLog = ClickLog(
            appId = topActivityFlow.value?.appId,
            activityId = topActivityFlow.value?.activityId,
            subsId = rule.subsItem.id,
            groupKey = rule.group.key,
            ruleIndex = rule.index,
            ruleKey = rule.key
        )
        DbSet.clickLogDao.insert(clickLog)
        increaseClickCount()
        if (recordStoreFlow.value.clickCount % 100 == 0) {
            DbSet.clickLogDao.deleteKeepLatest()
        }
    }
}
