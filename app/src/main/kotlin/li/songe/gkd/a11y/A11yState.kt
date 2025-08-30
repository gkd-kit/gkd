package li.songe.gkd.a11y

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.LruCache
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.data.ActionLog
import li.songe.gkd.data.ActionResult
import li.songe.gkd.data.ActivityLog
import li.songe.gkd.data.AppRule
import li.songe.gkd.data.AttrInfo
import li.songe.gkd.data.GlobalRule
import li.songe.gkd.data.ResetMatchType
import li.songe.gkd.data.ResolvedRule
import li.songe.gkd.data.RuleStatus
import li.songe.gkd.db.DbSet
import li.songe.gkd.store.actionCountFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.PKG_FLAGS
import li.songe.gkd.util.RuleSummary
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.ruleSummaryFlow

data class TopActivity(
    val appId: String = "",
    val activityId: String? = null,
    val number: Int = 0
) {
    val shortActivityId: String?
        get() {
            val a = if (activityId != null && activityId.startsWith(appId)) {
                activityId.substring(appId.length)
            } else {
                activityId
            }
            return a
        }

    fun format(): String {
        return "${appId}/${shortActivityId}/${number}"
    }

    fun sameAs(a: String, b: String?): Boolean {
        return appId == a && activityId == b
    }
}

val topActivityFlow = MutableStateFlow(TopActivity())
private var lastValidActivity: TopActivity = topActivityFlow.value
    set(value) {
        if (value.activityId != null) {
            field = value
        }
    }

private val activityLogMutex = Mutex()
private var activityLogCount = 0
private var lastActivityUpdateTime = 0L
private var lastActivityForceUpdateTime = 0L

private object ActivityCache : LruCache<Pair<String, String>, Boolean>(256) {
    override fun create(key: Pair<String, String>): Boolean = try {
        app.packageManager.getActivityInfo(
            ComponentName(key.first, key.second),
            PKG_FLAGS
        )
        true
    } catch (_: Exception) {
        false
    }
}

fun isActivity(
    appId: String,
    activityId: String,
): Boolean {
    return topActivityFlow.value.sameAs(appId, activityId) || ActivityCache.get(appId to activityId)
}

class ActivityRule(
    val appRules: List<AppRule> = emptyList(),
    val globalRules: List<GlobalRule> = emptyList(),
    val topActivity: TopActivity = TopActivity(),
    val ruleSummary: RuleSummary = RuleSummary(),
) {
    val currentRules = (appRules + globalRules).sortedBy { it.order }
    val hasPriorityRule = currentRules.size > 1 && currentRules.any { it.priorityEnabled }
    val activePriority: Boolean
        get() = hasPriorityRule && currentRules.any { it.isPriority() }
    val priorityRules: List<ResolvedRule>
        get() = if (hasPriorityRule) {
            currentRules.sortedBy { if (it.isPriority()) 0 else 1 }
        } else {
            currentRules
        }
    val skipMatch: Boolean
        get() {
            return currentRules.all { r -> !r.status.ok }
        }
    val skipConsumeEvent: Boolean
        get() {
            return currentRules.all { r -> !r.status.alive }
        }
    val hasFeatureAction: Boolean
        get() = currentRules.any { r -> r.checkForced() && (r.status == RuleStatus.StatusOk || r.status == RuleStatus.Status5) }
}

val activityRuleFlow = MutableStateFlow(ActivityRule())

@Synchronized
fun updateTopActivity(appId: String, activityId: String?, type: Int = 0) {
    val oldActivity = topActivityFlow.value
    val forced = type > 0
    val t = System.currentTimeMillis()
    val isSame = oldActivity.sameAs(appId, activityId)
    if (forced) {
        lastActivityForceUpdateTime = t
    } else {
        if (lastActivityForceUpdateTime > 0) {
            // ITaskStackListener 的变速快于无障碍
            if (t - lastActivityForceUpdateTime < 1000) return
            if (activityId != null && t - lastActivityForceUpdateTime < 3000) return
        }
        if (isSame && t - lastActivityUpdateTime < 1000) return
    }
    val number = if (isSame) {
        oldActivity.number + 1
    } else {
        0
    }
    topActivityFlow.value = TopActivity(
        appId = appId,
        activityId = activityId ?: lastValidActivity.takeIf { it.appId == appId }?.activityId,
        number = number,
    )
    lastValidActivity = oldActivity
    lastActivityUpdateTime = t
    if (storeFlow.value.enableActivityLog) {
        appScope.launchTry(Dispatchers.IO) {
            activityLogMutex.withLock {
                DbSet.activityLogDao.insert(
                    ActivityLog(
                        appId = appId,
                        activityId = activityId,
                        ctime = t,
                    )
                )
                activityLogCount++
                if (activityLogCount % 100 == 0) {
                    DbSet.activityLogDao.deleteKeepLatest()
                }
            }
        }
    }
    val topActivity = topActivityFlow.value
    val oldActivityRule = activityRuleFlow.value
    val allRules = ruleSummaryFlow.value
    val idChanged = topActivity.appId != oldActivityRule.topActivity.appId
    val topChanged = idChanged || oldActivityRule.topActivity != topActivity
    val ruleChanged = oldActivityRule.ruleSummary !== allRules
    if (topChanged || ruleChanged) {
        val allAppRules = allRules.appIdToRules[topActivity.appId] ?: emptyList()
        val newActivityRule = ActivityRule(
            ruleSummary = allRules,
            topActivity = topActivity,
            appRules = allAppRules.filter { rule ->
                rule.matchActivity(topActivity.appId, topActivity.activityId)
            },
            globalRules = ruleSummaryFlow.value.globalRules.filter { r ->
                r.matchActivity(topActivity.appId, topActivity.activityId)
            },
        )
        if (idChanged) {
            appChangeTime = t
            allRules.globalRules.forEach { it.resetState(t) }
            allRules.appIdToRules[oldActivityRule.topActivity.appId]?.forEach { it.resetState(t) }
            allAppRules.forEach { it.resetState(t) }
        } else {
            newActivityRule.currentRules.forEach { r ->
                when (r.resetMatchType) {
                    ResetMatchType.App -> {
                        if (r.isFirstMatchApp) {
                            r.resetState(t)
                        }
                    }

                    ResetMatchType.Activity -> r.resetState(t)
                    ResetMatchType.Match -> {
                        // is new rule
                        if (!oldActivityRule.currentRules.contains(r)) {
                            r.resetState(t)
                        }
                    }
                }
            }
        }
        activityRuleFlow.value = newActivityRule
        LogUtils.d(
            "${oldActivity.format()} -> ${topActivityFlow.value.format()} (type=$type)",
        )
    }
}

@Volatile
var lastTriggerRule: ResolvedRule? = null

@Volatile
var lastTriggerTime = 0L

@Volatile
var appChangeTime = 0L

var launcherAppId = ""

fun updateLauncherAppId() {
    launcherAppId = app.packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        PackageManager.MATCH_DEFAULT_ONLY
    )?.activityInfo?.packageName ?: ""
}

var imeAppId = ""
fun updateImeAppId() {
    imeAppId = app.getSecureString(Settings.Secure.DEFAULT_INPUT_METHOD)
        ?.let(ComponentName::unflattenFromString)?.packageName ?: ""
}

private val actionLogMutex = Mutex()
fun addActionLog(
    rule: ResolvedRule,
    topActivity: TopActivity,
    target: AccessibilityNodeInfo,
    actionResult: ActionResult,
) = appScope.launchTry(Dispatchers.IO) {
    val ctime = System.currentTimeMillis()
    actionLogMutex.withLock {
        val actionCount = actionCountFlow.updateAndGet { it + 1 }
        val actionLog = ActionLog(
            appId = topActivity.appId,
            activityId = topActivity.activityId,
            subsId = rule.subsItem.id,
            subsVersion = rule.rawSubs.version,
            groupKey = rule.g.group.key,
            groupType = rule.g.group.groupType,
            ruleIndex = rule.index,
            ruleKey = rule.key,
            ctime = ctime,
        )
        DbSet.actionLogDao.insert(actionLog)
        if (actionCount % 100 == 0L) {
            DbSet.actionLogDao.deleteKeepLatest()
        }
    }
    LogUtils.d(
        rule.statusText(),
        AttrInfo.info2data(target, 0, 0),
        actionResult
    )
}.let {}
