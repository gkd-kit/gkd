package li.songe.gkd.service

import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.NetworkUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.ServiceUtils
import com.blankj.utilcode.util.ToastUtils
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import li.songe.gkd.composition.CompositionAbService
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.composition.CompositionExt.useScope
import li.songe.gkd.data.NodeInfo
import li.songe.gkd.data.Rule
import li.songe.gkd.data.RuleManager
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.data.TriggerLog
import li.songe.gkd.db.DbSet
import li.songe.gkd.debug.SnapshotExt
import li.songe.gkd.shizuku.activityTaskManager
import li.songe.gkd.shizuku.shizukuIsSafeOK
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.launchWhile
import li.songe.gkd.util.launchWhileTry
import li.songe.gkd.util.storeFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class GkdAbService : CompositionAbService({
    useLifeCycleLog()

    val context = this as GkdAbService

    val scope = useScope()

    service = context
    onDestroy {
        service = null
        currentAppId = null
        currentActivityId = null
    }

    ManageService.start(context)
    onDestroy {
        ManageService.stop(context)
    }


    var serviceConnected = false
    onServiceConnected { serviceConnected = true }
    onInterrupt { serviceConnected = false }

    onAccessibilityEvent { event -> // 根据事件获取 activityId, 概率不准确
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val appId = rootInActiveWindow?.packageName?.toString() ?: return@onAccessibilityEvent
                val activityId = event.className?.toString() ?: return@onAccessibilityEvent
                if (activityId == "com.miui.home.launcher.Launcher") { // 小米桌面 bug
                    if (appId != "com.miui.home") {
                        return@onAccessibilityEvent
                    }
                }

                if (activityId.startsWith("android.") || activityId.startsWith("androidx.") || activityId.startsWith(
                        "com.android."
                    )
                ) {
                    return@onAccessibilityEvent
                }
                currentAppId = appId
                currentActivityId = activityId
            }

            else -> {}
        }
    }

    onAccessibilityEvent { event -> // 小米手机监听截屏保存快照
        if (!storeFlow.value.enableCaptureScreenshot) return@onAccessibilityEvent
        if (event?.packageName == null || event.className == null) return@onAccessibilityEvent
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.contentChangeTypes == AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED && event.packageName.contentEquals(
                "com.miui.screenshot"
            ) && event.className!!.startsWith("android.") // android.widget.RelativeLayout
        ) {
            scope.launchTry {
                val snapshot = SnapshotExt.captureSnapshot()
                ToastUtils.showShort("保存快照成功")
                LogUtils.d("截屏:保存快照", snapshot.id)
            }
        }
    }


    scope.launchWhile { // 屏幕无障碍信息轮询
        delay(200)
        if (!serviceConnected) return@launchWhile
        currentAppId = rootInActiveWindow?.packageName?.toString()
        if (!storeFlow.value.enableService || ScreenUtils.isScreenLock()) return@launchWhile

        var tempRules = rules
        var i = 0
        while (i < tempRules.size) {
            val rule = tempRules[i]
            i++
            if (!ruleManager.ruleIsAvailable(rule)) continue
            val frozenNode = rootInActiveWindow
            val target = rule.query(frozenNode)
            if (target != null) {
                val clickResult = target.click(context)
                ruleManager.trigger(rule)
                LogUtils.d(
                    *rule.matches.toTypedArray(), NodeInfo.abNodeToNode(target), clickResult
                )

                if (clickResult != null) {
                    scope.launchTry(IO) {
                        val triggerLog = TriggerLog(
                            appId = currentAppId,
                            activityId = currentActivityId,
                            subsId = rule.subsItem.id,
                            groupKey = rule.group.key,
                            ruleIndex = rule.index,
                            ruleKey = rule.key
                        )
                        DbSet.triggerLogDb.triggerLogDao().insert(triggerLog)
                    }
                }
            }
            delay(50)
            currentAppId = rootInActiveWindow?.packageName?.toString()
            if (tempRules != rules) {
                tempRules = rules
                i = 0
            }
        }
    }

    scope.launchWhile { // 自动从网络更新订阅文件
        delay(5000)
        if (!NetworkUtils.isAvailable()) return@launchWhile
        DbSet.subsItemDao.query().first().forEach { subsItem ->
            try {
                val text = Singleton.client.get(subsItem.updateUrl).bodyAsText()
                val subscriptionRaw = SubscriptionRaw.parse5(text)
                if (subscriptionRaw.version <= subsItem.version) {
                    return@forEach
                }
                val newItem = subsItem.copy(
                    updateUrl = subscriptionRaw.updateUrl ?: subsItem.updateUrl,
                    name = subscriptionRaw.name,
                    mtime = System.currentTimeMillis()
                )
                newItem.subsFile.writeText(
                    SubscriptionRaw.stringify(
                        subscriptionRaw
                    )
                )
                DbSet.subsItemDao.update(newItem)
                LogUtils.d("更新磁盘订阅文件:${subsItem.name}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        delay(30 * 60_000)
    }

    scope.launchTry {
        delay(5000)
        DbSet.subsItemDao.query().flowOn(IO).collect {
            ruleManager = RuleManager(it)
        }
    }

    scope.launchWhileTry(interval = 400) {
        if (shizukuIsSafeOK()) {
            val topActivity =
                activityTaskManager.getTasks(1, false, true)?.firstOrNull()?.topActivity
            if (topActivity != null) {
                currentAppId = topActivity.packageName
                currentActivityId = topActivity.className
            }
        }
    }

}) {

    companion object {
        private var service: GkdAbService? = null
        fun isRunning() = ServiceUtils.isServiceRunning(GkdAbService::class.java)

        private var ruleManager = RuleManager()
            set(value) {
                field = value
                rules = value.match(currentAppId, currentActivityId).toList()
            }
        private var rules = listOf<Rule>()
            set(value) {
                field = value
                LogUtils.d(
                    "currentAppId: $currentAppId",
                    "currentActivityId: $currentActivityId",
                    value.size,
                )
            }

        var currentActivityId: String? = null
            set(value) {
                val oldValue = field
                field = value
                if (value != oldValue) {
                    rules = ruleManager.match(currentAppId, value).toList()
                }
            }
        private var currentAppId: String? = null
            set(value) {
                val oldValue = field
                field = value
                if (value != oldValue) {
                    rules = ruleManager.match(value, currentActivityId).toList()
                }
            }
        val currentAbNode: AccessibilityNodeInfo?
            get() {
                return service?.rootInActiveWindow
            }

        suspend fun currentScreenshot() = service?.run {
            suspendCoroutine {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    takeScreenshot(Display.DEFAULT_DISPLAY,
                        application.mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(screenshot: ScreenshotResult) {
                                it.resume(
                                    Bitmap.wrapHardwareBuffer(
                                        screenshot.hardwareBuffer, screenshot.colorSpace
                                    )
                                )
                            }

                            override fun onFailure(errorCode: Int) = it.resume(null)
                        })
                } else {
                    it.resume(null)
                }
            }
        }
    }
}