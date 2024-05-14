package li.songe.gkd.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.app
import li.songe.gkd.data.AppRule
import li.songe.gkd.data.ClickLog
import li.songe.gkd.data.GlobalRule
import li.songe.gkd.data.ResolvedRule
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.RuleSummary
import li.songe.gkd.util.getDefaultLauncherAppId
import li.songe.gkd.util.increaseClickCount
import li.songe.gkd.util.recordStoreFlow
import li.songe.gkd.util.ruleSummaryFlow

data class TopActivity(
    val appId: String = "",
    val activityId: String? = null,
    val number: Int = 0
)

val topActivityFlow = MutableStateFlow(TopActivity())

data class ActivityRule(
    val appRules: List<AppRule> = emptyList(),
    val globalRules: List<GlobalRule> = emptyList(),
    val topActivity: TopActivity = TopActivity(),
    val ruleSummary: RuleSummary = RuleSummary(),
) {
    val currentRules = (appRules + globalRules).sortedBy { r -> r.order }
}

val activityRuleFlow by lazy { MutableStateFlow(ActivityRule()) }

private var lastTopActivity: TopActivity = topActivityFlow.value

private fun getFixTopActivity(): TopActivity {
    val top = topActivityFlow.value
    if (top.activityId == null) {
        if (lastTopActivity.appId == top.appId) {
            // 当从通知栏上拉返回应用, 从锁屏返回 等时, activityId 的无障碍事件不会触发, 此时复用上一次获得的 activityId 填充
            topActivityFlow.value = lastTopActivity
        }
    } else {
        // 仅保留最近的有 activityId 的单个 TopActivity
        lastTopActivity = top
    }
    return topActivityFlow.value
}

fun getAndUpdateCurrentRules(): ActivityRule {
    val topActivity = getFixTopActivity()
    val oldActivityRule = activityRuleFlow.value
    val allRules = ruleSummaryFlow.value
    val idChanged = topActivity.appId != oldActivityRule.topActivity.appId
    val topChanged = idChanged || oldActivityRule.topActivity != topActivity
    val ruleChanged = oldActivityRule.ruleSummary !== allRules
    if (topChanged || ruleChanged) {
        val t = System.currentTimeMillis()
        val newActivityRule = ActivityRule(
            ruleSummary = allRules,
            topActivity = topActivity,
            appRules = (allRules.appIdToRules[topActivity.appId] ?: emptyList()).filter { rule ->
                rule.matchActivity(topActivity.appId, topActivity.activityId)
            },
            globalRules = ruleSummaryFlow.value.globalRules.filter { r ->
                r.matchActivity(topActivity.appId, topActivity.activityId)
            },
        )
        if (idChanged) {
            appChangeTime = t
            allRules.globalRules.forEach { r ->
                r.actionDelayTriggerTime = 0
                r.actionCount.value = 0
                r.matchChangedTime = t
            }
            allRules.appIdToRules[oldActivityRule.topActivity.appId]?.forEach { r ->
                r.actionDelayTriggerTime = 0
                r.actionCount.value = 0
                r.matchChangedTime = t
            }
            newActivityRule.appRules.forEach { r ->
                r.actionDelayTriggerTime = 0
                r.actionCount.value = 0
                r.matchChangedTime = t
            }
        } else {
            newActivityRule.currentRules.forEach { r ->
                if (r.resetMatchTypeWhenActivity) {
                    r.actionDelayTriggerTime = 0
                    r.actionCount.value = 0
                }
                if (!oldActivityRule.currentRules.contains(r)) {
                    // 新增规则
                    r.matchChangedTime = t
                }
            }
        }
        activityRuleFlow.value = newActivityRule
    }
    return activityRuleFlow.value
}

var lastTriggerRule: ResolvedRule? = null

@Volatile
var lastTriggerTime = 0L

@Volatile
var appChangeTime = 0L

var launcherAppId = ""
fun updateLauncherAppId() {
    launcherAppId = app.packageManager.getDefaultLauncherAppId() ?: ""
}

val clickLogMutex by lazy { Mutex() }
suspend fun insertClickLog(rule: ResolvedRule) {
    clickLogMutex.withLock {
        increaseClickCount()
        val clickLog = ClickLog(
            appId = topActivityFlow.value.appId,
            activityId = topActivityFlow.value.activityId,
            subsId = rule.subsItem.id,
            subsVersion = rule.rawSubs.version,
            groupKey = rule.g.group.key,
            groupType = when (rule) {
                is AppRule -> SubsConfig.AppGroupType
                is GlobalRule -> SubsConfig.GlobalGroupType
            },
            ruleIndex = rule.index,
            ruleKey = rule.key,
        )
        DbSet.clickLogDao.insert(clickLog)
        if (recordStoreFlow.value.clickCount % 100 == 0) {
            DbSet.clickLogDao.deleteKeepLatest()
        }
    }
}
