package li.songe.gkd.service

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
import li.songe.gkd.shizuku.newActivityTaskManager
import li.songe.gkd.shizuku.shizukuIsSafeOK
import li.songe.gkd.shizuku.useShizukuAliveState
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.increaseClickCount
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.launchWhile
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
import li.songe.selector.Selector
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(FlowPreview::class)
class GkdAbService : CompositionAbService({
    useLifeCycleLog()

    val context = this as GkdAbService

    val scope = useScope()

    service = context
    onDestroy {
        service = null
        topActivityFlow.value = null
    }

    ManageService.start(context)
    onDestroy {
        ManageService.stop(context)
    }


    var isScreenLock = false
    scope.launchWhile(IO) {
        isScreenLock = ScreenUtils.isScreenLock()
        delay(1000)
    }

    val shizukuAliveFlow = useShizukuAliveState()
    val shizukuGrantFlow = MutableStateFlow(false)
    scope.launchWhile(IO) {
        shizukuGrantFlow.value = if (shizukuAliveFlow.value) shizukuIsSafeOK() else false
        delay(3000)
    }
    val activityTaskManagerFlow =
        combine(shizukuAliveFlow, shizukuGrantFlow) { shizukuAlive, shizukuGrant ->
            if (shizukuAlive && shizukuGrant) newActivityTaskManager() else null
        }.flowOn(IO).stateIn(scope, SharingStarted.Eagerly, null)

    fun getActivityIdByShizuku(): String? {
        try {
            val topActivity =
                activityTaskManagerFlow.value?.getTasks(1, false, true)?.firstOrNull()?.topActivity
            return topActivity?.className
        } catch (_: Exception) {
        }
        return null
    }

    var lastKeyEventTime = -1L
    onKeyEvent { event -> // 当按下音量键时捕获快照
        val keyCode = event?.keyCode ?: return@onKeyEvent
        if (storeFlow.value.captureVolumeKey && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
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

    fun isActivity(appId: String, activityId: String): Boolean {
        return activityId.startsWith(appId) || (try {
            packageManager.getActivityInfo(ComponentName(appId, activityId), 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } != null)
    }

    onAccessibilityEvent { event -> // 根据事件获取 activityId, 概率不准确
        if (event == null || isScreenLock) return@onAccessibilityEvent

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val newAppId = event.packageName?.toString() ?: return@onAccessibilityEvent
                val rightAppId =
                    safeActiveWindow?.packageName?.toString() ?: return@onAccessibilityEvent
                val shizukuActivityId =
                    getActivityIdByShizuku() ?: topActivityFlow.value?.activityId
                if (rightAppId != newAppId) {
                    topActivityFlow.value =
                        TopActivity(appId = rightAppId, activityId = shizukuActivityId)
                    return@onAccessibilityEvent
                }
                val newActivityId = event.className?.toString() ?: return@onAccessibilityEvent

                if (isActivity(newAppId, newActivityId)) {
                    topActivityFlow.value = TopActivity(newAppId, newActivityId)
                } else {
                    if (newActivityId.startsWith("android.") || newActivityId.startsWith("androidx.") || newActivityId.startsWith(
                            "com.android."
                        )
                    ) {
                        topActivityFlow.value = topActivityFlow.value?.copy(
                            appId = rightAppId, activityId = shizukuActivityId, sourceId = null
                        )
                    } else {
                        topActivityFlow.value = topActivityFlow.value?.copy(
                            appId = rightAppId,
                            activityId = shizukuActivityId,
                            sourceId = newActivityId
                        )
                    }
                }
            }

            else -> {

            }
        }
    }


    var lastToastTime = -1L

    scope.launchWhile { // 屏幕无障碍信息轮询
        delay(200)
        if (!serviceConnected || isScreenLock) return@launchWhile

        val rightAppId = safeActiveWindow?.packageName?.toString()
        if (rightAppId != null && rightAppId != topActivityFlow.value?.appId) {
            topActivityFlow.value = topActivityFlow.value?.copy(
                appId = rightAppId,
                activityId = getActivityIdByShizuku() ?: topActivityFlow.value?.activityId
            )
        }

        if (!storeFlow.value.enableService) return@launchWhile

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
                        appId = topActivityFlow.value?.appId,
                        activityId = topActivityFlow.value?.activityId,
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
        if (!NetworkUtils.isAvailable() || storeFlow.value.updateSubsInterval <= 0 || isScreenLock) return@launchWhile
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
                    SubscriptionRaw.parse(Singleton.client.get(subsItem.updateUrl).bodyAsText())
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
        combine(topActivityFlow, currentRulesFlow) { topActivity, currentRules ->
            topActivity to currentRules
        }.debounce(300).collect { (topActivity, currentRules) ->
            if (storeFlow.value.enableService) {
                LogUtils.d(
                    topActivity, *currentRules.map { r -> r.rule.matches }.toTypedArray()
                )
            } else {
                LogUtils.d(
                    topActivity
                )
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