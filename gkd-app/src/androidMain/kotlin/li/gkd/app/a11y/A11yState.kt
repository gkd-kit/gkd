package li.gkd.app.a11y

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.LruCache
import android.view.accessibility.AccessibilityNodeInfo
import com.android.internal.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.data.appinfo.PackageAppCatalog
import li.gkd.app.data.isSystem
import li.gkd.app.data.subscription.SubscriptionState
import li.gkd.app.data.toAttrInfo
import li.gkd.app.model.ActionResult
import li.gkd.app.record.ActionRecordInput
import li.gkd.app.record.AppVisitInput
import li.gkd.app.record.RuntimeRecordRepository
import li.gkd.app.rule.ActivityRule
import li.gkd.app.rule.ResolvedRule
import li.gkd.app.rule.RuleSummary
import li.gkd.app.rule.TopActivity
import li.gkd.app.service.updateTopTaskAppId
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.rule.statusText
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.Constants
import li.gkd.app.util.LogUtils
import li.gkd.app.util.launchLogged
import li.songe.codeorigin.CallSite
import li.gkd.app.app as gkdApp

val activityRuleFlow: StateFlow<ActivityRule>
    get() = A11yState.activityRuleFlow

val topActivityFlow = activityRuleFlow.map { it.topActivity }.distinctUntilChanged()
val currentTopActivity: TopActivity
    get() = activityRuleFlow.value.topActivity

private object ActivityCache : LruCache<Pair<String, String>, Boolean>(256) {
    override fun create(key: Pair<String, String>): Boolean = try {
        app.packageManager.getActivityInfo(
            ComponentName(key.first, key.second),
            PackageAppCatalog.packageFlags
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
        field = MutableStateFlow(ActivityRule(blockMatch = SettingsRepository.checkAppBlockMatch(Constants.systemUiAppId)))
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

    private var lastActivityUpdateTime = 0L
    private var lastActivityForceUpdateTime = 0L

    private var lastAppId = Constants.systemUiAppId

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
        val idChanged =
            (scene == ActivityScene.ScreenOn || appId != oldActivityRule.topActivity.appId)
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
        appScope.launchLogged { RuntimeRecordRepository.recordActivity(appId, activityId, t) }
        // Keep foreground/visit bookkeeping active while no complete executable snapshot exists.
        // Presentation still observes Loading/Failure; the executor receives no runnable rules.
        val ruleSummary = SubscriptionState.ruleSummaryFlow.value.value ?: RuleSummary()
        val topChanged = idChanged || oldActivityRule.topActivity != topActivity
        val ruleChanged = oldActivityRule.ruleSummary !== ruleSummary
        if (topChanged || ruleChanged) {
            val newActivityRule = ActivityRule(
                ruleSummary = ruleSummary,
                topActivity = topActivity,
                blockMatch = SettingsRepository.checkAppBlockMatch(topActivity.appId),
            )
            if (idChanged) {
                val oldAppId = lastAppId
                lastAppId = appId
                val visit = AppVisitInput(oldAppId, appId, t)
                appScope.launchLogged { RuntimeRecordRepository.recordVisit(visit) }
                RuleExecutionHost.runtime.onAppChanged(t)
                ruleSummary.globalRules.forEach { it.resetState(t) }
                ruleSummary.appIdToRules[oldActivityRule.topActivity.appId]?.forEach {
                    it.resetState(
                        t
                    )
                }
                newActivityRule.appRules.forEach { it.resetState(t) }
            } else {
                newActivityRule.currentRules.forEach { r ->
                    r.onActivityTransition(t, previouslyMatched = r in oldActivityRule.currentRules)
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

val lastTriggerTime: Long get() = RuleExecutionHost.runtime.lastTriggerTime

val appChangeTime: Long get() = RuleExecutionHost.runtime.appChangeTime

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
                gkdApp.getString(R.string.config_recentsComponentName)
            ) ?: systemRecentCn
        }
        if (systemRecentCn.packageName.isEmpty()) {
            // https://github.com/android-cs/8/blob/main/packages/SystemUI/src/com/android/systemui/recents/RecentsActivity.java
            systemRecentCn = ComponentName(
                Constants.systemUiAppId,
                "${Constants.systemUiAppId}.recents.RecentsActivity",
            )
        }
    }
}

fun addActionLog(
    rule: ResolvedRule,
    topActivity: TopActivity,
    target: AccessibilityNodeInfo,
    actionResult: ActionResult,
) {
    val input = ActionRecordInput(
        appId = topActivity.appId,
        activityId = topActivity.activityId,
        subsId = rule.subsItem.id,
        subsVersion = rule.rawSubs.version,
        groupKey = rule.g.group.key,
        groupType = rule.g.group.groupType,
        ruleIndex = rule.index,
        ruleKey = rule.key,
        time = System.currentTimeMillis(),
    )
    appScope.launchLogged { RuntimeRecordRepository.recordAction(input) }
    // Android node inspection is diagnostic only and stays outside the production record writer.
    appScope.launchLogged(Dispatchers.IO) {
        LogUtils.d(rule.statusText(), target.toAttrInfo(0, 0), actionResult)
    }
}
