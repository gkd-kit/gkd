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
    private val appRules: List<AppRule> = emptyList(),
    private val globalRules: List<GlobalRule> = emptyList(),
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
    if (topChanged || oldActivityRule.ruleSummary !== allRules) {
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
        activityRuleFlow.value = newActivityRule
        if (newActivityRule.currentRules.isNotEmpty()) {
            val newRules =
                newActivityRule.currentRules.filter { r -> !oldActivityRule.currentRules.any { r2 -> r2 == r } }
            val t = System.currentTimeMillis()
            if (newRules.isNotEmpty()) {
                newRules.forEach { r ->
                    r.matchChangedTime = t
                }
            }
            newActivityRule.currentRules.forEach { r ->
                if (r.resetMatchTypeWhenActivity || idChanged) {
                    r.actionDelayTriggerTime = 0
                    r.actionCount.value = 0
                }
                if (idChanged) {
                    // 重置全局规则的匹配时间
                    r.matchChangedTime = t
                }
            }
        }
    }
    if (idChanged) {
        appChangeTime = System.currentTimeMillis()
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

val clickLogMutex = Mutex()
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
