package li.gkd.app.a11y

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.LruCache
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import li.gkd.app.META
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.db.ActionLog
import li.gkd.app.data.ActionResult
import li.gkd.db.ActivityLog
import li.gkd.app.data.AttrInfo
import li.gkd.app.data.ResetMatchType
import li.gkd.app.data.ResolvedRule
import li.gkd.app.data.RuleStatus
import li.gkd.app.data.insert
import li.gkd.app.data.isSystem
import li.gkd.app.service.updateTopTaskAppId
import li.gkd.app.store.AppStore.actionCountFlow
import li.gkd.app.store.AppStore.checkAppBlockMatch
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.LogUtils
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.app.domain.rule.RuleSummary
import li.gkd.app.util.launchLogged
import li.gkd.app.data.subscription.SubscriptionState
import li.gkd.app.util.systemUiAppId
import li.gkd.db.Db
import li.songe.codeorigin.CallSite

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

val activityRuleFlow: StateFlow<ActivityRule>
    get() = A11yState.activityRuleFlow

val topActivityFlow = activityRuleFlow.map { it.topActivity }.distinctUntilChanged()
val currentTopActivity: TopActivity
    get() = activityRuleFlow.value.topActivity

private object ActivityCache : LruCache<Pair<String, String>, Boolean>(256) {
    override fun create(key: Pair<String, String>): Boolean = try {
        app.packageManager.getActivityInfo(
            ComponentName(key.first, key.second),
            AppInfoRepository.packageFlags
        )
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

fun isActivity(
    appId: String,
    activityId: String,
): Boolean {
    return currentTopActivity.sameAs(appId, activityId) || ActivityCache.get(appId to activityId)
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

sealed class ActivityScene {
    data object ScreenOn : ActivityScene()
    data object A11y : ActivityScene()
    data object TaskStack : ActivityScene()
}

object A11yState {
    private val lock = Any()

    // Resolving the foreground activity can block on the privileged process. Keep that
    // query, its checks and the update in one critical section shared by every writer.
    fun <T> withTopActivityLock(block: () -> T): T = synchronized(lock, block)

    val activityRuleFlow: StateFlow<ActivityRule>
        field = MutableStateFlow(ActivityRule())
    val currentRule: ActivityRule
        get() = synchronized(lock) { activityRuleFlow.value }

    fun onScreenForcedActive(): Unit = synchronized(lock) {
        val top = activityRuleFlow.value.topActivity
        updateTopActivity(top.appId, top.activityId, ActivityScene.ScreenOn)
    }

    private var lastValidActivity: TopActivity = activityRuleFlow.value.topActivity
        set(value) {
            if (value.activityId != null) {
                field = value
            }
        }

    private var activityLogCount = 0
    private var lastActivityUpdateTime = 0L
    private var lastActivityForceUpdateTime = 0L
    private val tempActivityLogList = mutableListOf<ActivityLog>()

    private var lastAppId = ""

    fun updateTopActivity(
        appId: String,
        activityId: String?,
        scene: ActivityScene = ActivityScene.A11y,
        @CallSite loc: String = "",
    ): Unit = synchronized(lock) {
        val t = System.currentTimeMillis()
        if (scene == ActivityScene.TaskStack) {
            updateTopTaskAppId(appId)
        }
        val oldActivity = activityRuleFlow.value.topActivity
        val oldActivityRule = activityRuleFlow.value
        val idChanged = (scene == ActivityScene.ScreenOn || appId != oldActivityRule.topActivity.appId)
        val isSame = scene != ActivityScene.ScreenOn && oldActivity.sameAs(appId, activityId)
        if (scene == ActivityScene.TaskStack) {
            lastActivityForceUpdateTime = t
        } else if (scene == ActivityScene.A11y) {
            if (idChanged && lastActivityForceUpdateTime > 0) {
                // ITaskStackListener 大部分场景快于无障碍
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
        val topActivity = TopActivity(
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
            appScope.launchLogged {
                Db.activityLogDao.insert(*logs)
            }
        }
        if (activityLogCount++ % 100 == 0) {
            appScope.launchLogged { Db.activityLogDao.deleteKeepLatest() }
        }
        val ruleSummary = SubscriptionState.ruleSummaryFlow.value
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
                appScope.launchLogged {
                    Db.appLastVisitDao.insert(oldAppId, appId, t)
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
                "${oldActivity.format()} -> ${topActivity.format()} (scene=$scene)",
                loc = loc,
                tag = "updateTopActivity",
            )
        }
    }
}

fun updateTopActivity(
    appId: String,
    activityId: String?,
    scene: ActivityScene = ActivityScene.A11y,
    @CallSite loc: String = "",
) = A11yState.updateTopActivity(appId, activityId, scene, loc)

@Volatile
var lastTriggerRule: ResolvedRule? = null

@Volatile
var lastTriggerTime = 0L

@Volatile
var appChangeTime = 0L

var imeAppId = ""
val launcherAppIdFlow: StateFlow<String>
    field = MutableStateFlow("")
val launcherAppId: String get() = launcherAppIdFlow.value
var systemRecentCn = ComponentName("", "")

fun updateSystemDefaultAppId() {
    imeAppId = app.getSecureString(Settings.Secure.DEFAULT_INPUT_METHOD)
        ?.let(ComponentName::unflattenFromString)?.packageName ?: ""
    val launcherCn = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        .resolveActivity(app.packageManager)
    launcherAppIdFlow.value = launcherCn.packageName
    if (app.getPkgInfo(launcherAppId)?.applicationInfo?.isSystem == true) {
        systemRecentCn = launcherCn
    } else {
        if (AndroidTarget.P) {
            systemRecentCn = ComponentName.unflattenFromString(
                app.getString(com.android.internal.R.string.config_recentsComponentName)
            ) ?: systemRecentCn
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
) = appScope.launchLogged(Dispatchers.IO) {
    val ctime = System.currentTimeMillis()
    actionLogMutex.withLock {
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
        Db.actionLogDao.insert(actionLog)
        if (actionCountFlow.value % 100 == 0L) {
            Db.actionLogDao.deleteKeepLatest()
        }
    }
    LogUtils.d(
        rule.statusText(),
        AttrInfo.info2data(target, 0, 0),
        actionResult
    )
}.let {}
