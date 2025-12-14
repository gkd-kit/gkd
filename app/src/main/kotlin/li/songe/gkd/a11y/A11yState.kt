package li.songe.gkd.a11y

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.util.LruCache
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.songe.gkd.META
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.data.ActionLog
import li.songe.gkd.data.ActionResult
import li.songe.gkd.data.ActivityLog
import li.songe.gkd.data.AttrInfo
import li.songe.gkd.data.ResetMatchType
import li.songe.gkd.data.ResolvedRule
import li.songe.gkd.data.RuleStatus
import li.songe.gkd.data.isSystem
import li.songe.gkd.db.DbSet
import li.songe.gkd.service.updateTopAppId
import li.songe.gkd.shizuku.safeInvokeMethod
import li.songe.gkd.store.actionCountFlow
import li.songe.gkd.store.checkAppBlockMatch
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.PKG_FLAGS
import li.songe.gkd.util.RuleSummary
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.systemUiAppId
import li.songe.loc.Loc

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

    fun sameAs(cn: ComponentName): Boolean {
        return appId == cn.packageName && activityId == cn.className
    }
}

val topActivityFlow = MutableStateFlow(TopActivity())
private var lastValidActivity: TopActivity = topActivityFlow.value
    set(value) {
        if (value.activityId != null) {
            field = value
        }
    }

private var activityLogCount = 0
private var lastActivityUpdateTime = 0L
private var lastActivityForceUpdateTime = 0L
private val tempActivityLogList = mutableListOf<ActivityLog>()

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
    val topActivity: TopActivity = TopActivity(),
    val ruleSummary: RuleSummary = RuleSummary(),
) {
    val blockMatch = checkAppBlockMatch(topActivity.appId)
    val appRules = ruleSummary.appIdToRules[topActivity.appId] ?: emptyList()
    val activityRules = if (blockMatch) emptyList() else appRules.filter { rule ->
        rule.matchActivity(topActivity.appId, topActivity.activityId)
    }
    val globalRules = if (blockMatch) emptyList() else ruleSummary.globalRules.filter { r ->
        r.matchActivity(topActivity.appId, topActivity.activityId)
    }

    val currentRules = (activityRules + globalRules).sortedBy { it.order }
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

private var lastAppId = ""

sealed class ActivityScene() {
    data object ScreenOn : ActivityScene()
    data object A11y : ActivityScene()
    data object TaskStack : ActivityScene()
}

@Loc
@Synchronized
fun updateTopActivity(
    appId: String,
    activityId: String?,
    scene: ActivityScene = ActivityScene.A11y,
    @Loc loc: String = "",
) {
    val t = System.currentTimeMillis()
    if (scene == ActivityScene.TaskStack && storeFlow.value.enableBlockA11yAppList) {
        updateTopAppId(appId)
    }
    val oldActivity = topActivityFlow.value
    val isSame = scene != ActivityScene.ScreenOn && oldActivity.sameAs(appId, activityId)
    if (scene == ActivityScene.TaskStack) {
        lastActivityForceUpdateTime = t
    } else if (scene == ActivityScene.A11y) {
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
    tempActivityLogList.add(
        ActivityLog(
            appId = appId,
            activityId = activityId,
            ctime = t,
        )
    )
    if (tempActivityLogList.size >= 16 || appId == META.appId) {
        val logs = tempActivityLogList.toTypedArray()
        tempActivityLogList.clear()
        appScope.launchTry {
            DbSet.activityLogDao.insert(*logs)
        }
    }
    if (activityLogCount++ % 100 == 0) {
        appScope.launchTry { DbSet.activityLogDao.deleteKeepLatest() }
    }
    val topActivity = topActivityFlow.value
    val oldActivityRule = activityRuleFlow.value
    val ruleSummary = ruleSummaryFlow.value
    val idChanged = (scene == ActivityScene.ScreenOn ||
            topActivity.appId != oldActivityRule.topActivity.appId)
    val topChanged = idChanged || oldActivityRule.topActivity != topActivity
    val ruleChanged = oldActivityRule.ruleSummary !== ruleSummary
    if (topChanged || ruleChanged) {
        val newActivityRule = ActivityRule(
            ruleSummary = ruleSummary,
            topActivity = topActivity,
        )
        if (idChanged) {
            val oldAppId = lastAppId
            lastAppId = appId
            appScope.launchTry {
                DbSet.appVisitLogDao.insert(oldAppId, appId, t)
            }
            appChangeTime = t
            ruleSummary.globalRules.forEach { it.resetState(t) }
            ruleSummary.appIdToRules[oldActivityRule.topActivity.appId]?.forEach { it.resetState(t) }
            newActivityRule.appRules.forEach { it.resetState(t) }
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
            "${oldActivity.format()} -> ${topActivityFlow.value.format()} (scene=$scene)",
            loc = loc,
            tag = "updateTopActivity",
        )
    }
}

@Volatile
var lastTriggerRule: ResolvedRule? = null

@Volatile
var lastTriggerTime = 0L

@Volatile
var appChangeTime = 0L

var imeAppId = ""
var launcherAppId = ""
var systemRecentCn = ComponentName("", "")

fun updateSystemDefaultAppId() {
    imeAppId = app.getSecureString(Settings.Secure.DEFAULT_INPUT_METHOD)
        ?.let(ComponentName::unflattenFromString)?.packageName ?: ""
    val launcherCn = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        .resolveActivity(app.packageManager)
    launcherAppId = launcherCn.packageName
    if (app.getPkgInfo(launcherAppId)?.applicationInfo?.isSystem == true) {
        systemRecentCn = launcherCn
    } else {
        safeInvokeMethod {
            if (AndroidTarget.P) {
                systemRecentCn = ComponentName.unflattenFromString(
                    app.getString(com.android.internal.R.string.config_recentsComponentName)
                ) ?: systemRecentCn
            }
        }
        if (systemRecentCn.packageName.isEmpty()) {
            // https://github.com/android-cs/8/blob/main/packages/SystemUI/src/com/android/systemui/recents/RecentsActivity.java
            systemRecentCn = ComponentName(
                systemUiAppId,
                "$systemUiAppId.recents.RecentsActivity",
            )
        }
    }
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
