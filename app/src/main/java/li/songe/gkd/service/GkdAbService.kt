package li.songe.gkd.service

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.KeyEvent
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import li.songe.gkd.composition.CompositionAbService
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.composition.CompositionExt.useScope
import li.songe.gkd.data.ClickLog
import li.songe.gkd.data.NodeInfo
import li.songe.gkd.data.RpcError
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.DbSet
import li.songe.gkd.debug.SnapshotExt
import li.songe.gkd.shizuku.activityTaskManager
import li.songe.gkd.shizuku.shizukuIsSafeOK
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.increaseClickCount
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.launchWhile
import li.songe.gkd.util.launchWhileTry
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
import li.songe.selector.Selector
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class GkdAbService : CompositionAbService({
    useLifeCycleLog()

    val context = this as GkdAbService

    val scope = useScope()

    service = context
    onDestroy {
        service = null
        appIdFlow.value = null
        activityIdFlow.value = null
    }

    ManageService.start(context)
    onDestroy {
        ManageService.stop(context)
    }

    var lastKeyEventTime = -1L
    onKeyEvent { event -> // 当按下音量键时捕获快照
        if (storeFlow.value.captureVolumeKey && (event?.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event?.keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            val et = System.currentTimeMillis()
            if (et - lastKeyEventTime > 3000) {
                lastKeyEventTime = et
                scope.launchTry(IO) {
                    val snapshot = SnapshotExt.captureSnapshot()
                    ToastUtils.showShort("保存快照成功")
                    LogUtils.d("截屏:保存快照", snapshot.id)
                }
            }
        }
    }


    var serviceConnected = false
    onServiceConnected { serviceConnected = true }
    onInterrupt { serviceConnected = false }

    onAccessibilityEvent { event -> // 根据事件获取 activityId, 概率不准确
        val appId = safeActiveWindow?.packageName?.toString() ?: return@onAccessibilityEvent
        appIdFlow.value = appId
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
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
                activityIdFlow.value = activityId
            }

            else -> {}
        }
    }

    scope.launchWhileTry(interval = 500) { // 根据 shizuku 获取 activityId, 准确
        if (shizukuIsSafeOK()) {
            val topActivity =
                activityTaskManager.getTasks(1, false, true)?.firstOrNull()?.topActivity
            if (topActivity != null) {
                appIdFlow.value = topActivity.packageName
                activityIdFlow.value = topActivity.className
            }
        }
    }

    var isScreenLock = false
    scope.launchWhile(IO) {
        isScreenLock = ScreenUtils.isScreenLock()
        delay(1000)
    }

    var lastToastTime = -1L

    scope.launchWhile { // 屏幕无障碍信息轮询
        delay(200)
        if (!serviceConnected) return@launchWhile
        if (!storeFlow.value.enableService || isScreenLock) return@launchWhile

        val currentRules = currentRulesFlow.value
        for (rule in currentRules) {
            if (!isAvailableRule(rule)) continue
            val target = rule.query(safeActiveWindow) ?: continue

            // 开始延迟
            if (rule.delay > 0 && rule.delayTriggerTime == 0L) {
                rule.triggerDelay()
                continue
            }

            // 如果节点在屏幕外部, click 的结果为 null
            val clickResult = target.click(context)
            if (clickResult != null) {
                if (storeFlow.value.toastWhenClick) {
                    val t = System.currentTimeMillis()
                    if (t - lastToastTime > 3000) {
                        ToastUtils.showShort(storeFlow.value.clickToast)
                        lastToastTime = t
                    }
                }
                rule.trigger()
                LogUtils.d(
                    *rule.matches.toTypedArray(), NodeInfo.abNodeToNode(target).attr, clickResult
                )
                scope.launchTry(IO) {
                    val clickLog = ClickLog(
                        appId = appIdFlow.value,
                        activityId = activityIdFlow.value,
                        subsId = rule.subsItem.id,
                        groupKey = rule.group.key,
                        ruleIndex = rule.index,
                        ruleKey = rule.key
                    )
                    DbSet.clickLogDb.clickLogDao().insert(clickLog)
                    increaseClickCount()
                }
            }
            if (currentRules !== currentRulesFlow.value) break
        }
    }

    scope.launchTry {

        storeFlow.map { s -> s.updateSubsInterval }.collect { updateSubsInterval ->
            if (updateSubsInterval <= 0) {
                // clear
            } else {
                // new task
                updateSubsInterval.coerceAtLeast(60 * 60_000)
            }
        }
    }

    var lastUpdateSubsTime = System.currentTimeMillis()
    scope.launchWhile(IO) { // 自动从网络更新订阅文件
        delay(60_000)
        if (!NetworkUtils.isAvailable() || storeFlow.value.updateSubsInterval <= 0) return@launchWhile
        if (System.currentTimeMillis() - lastUpdateSubsTime < storeFlow.value.updateSubsInterval.coerceAtLeast(
                60 * 60_000
            )
        ) {
            return@launchWhile
        }
        subsItemsFlow.value.forEach { subsItem ->
            if (subsItem.updateUrl == null) return@forEach
            try {
                val newSubsRaw =
                    SubscriptionRaw.parse5(Singleton.client.get(subsItem.updateUrl).bodyAsText())
                if (newSubsRaw.id != subsItem.id) {
                    return@forEach
                }
                val oldSubsRaw = subsIdToRawFlow.value[subsItem.id]
                if (oldSubsRaw != null && newSubsRaw.version <= oldSubsRaw.version) {
                    return@forEach
                }
                subsItem.subsFile.writeText(
                    SubscriptionRaw.stringify(
                        newSubsRaw
                    )
                )
                val newItem = subsItem.copy(
                    updateUrl = newSubsRaw.updateUrl ?: subsItem.updateUrl,
                    mtime = System.currentTimeMillis()
                )
                DbSet.subsItemDao.update(newItem)
                LogUtils.d("更新磁盘订阅文件:${newSubsRaw.name}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        lastUpdateSubsTime = System.currentTimeMillis()
    }

    scope.launch {
        combine(appIdFlow, activityIdFlow, currentRulesFlow) { appId, activityId, currentRules ->
            appId to activityId to currentRules
        }.collect {
            val (appId, activityId) = it.first
            val currentRules = it.second
            Log.d("GkdAbService", "appId:$appId, activityId:$activityId")
            if (currentRules.isNotEmpty()) {
                LogUtils.d(*currentRules.map { r -> r.rule.matches }.toTypedArray())
            }
        }
    }

}) {

    companion object {
        private var service: GkdAbService? = null
        fun isRunning() = ServiceUtils.isServiceRunning(GkdAbService::class.java)

        fun click(source: String): String? {
            val serviceVal = service ?: throw RpcError("无障碍没有运行")
            val selector = try {
                Selector.parse(source)
            } catch (e: Exception) {
                throw RpcError("非法选择器")
            }
            return currentAbNode?.querySelector(selector)?.click(serviceVal)
        }


        val currentAbNode: AccessibilityNodeInfo?
            get() {
                return service?.safeActiveWindow
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