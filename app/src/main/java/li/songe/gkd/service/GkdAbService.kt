package li.songe.gkd.service

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ServiceUtils
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.composition.CompositionAbService
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.composition.CompositionExt.useScope
import li.songe.gkd.data.ActionResult
import li.songe.gkd.data.GkdAction
import li.songe.gkd.data.NodeInfo
import li.songe.gkd.data.RpcError
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.data.getActionFc
import li.songe.gkd.db.DbSet
import li.songe.gkd.shizuku.useSafeGetTasksFc
import li.songe.gkd.util.client
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.launchWhile
import li.songe.gkd.util.map
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
import li.songe.selector.Selector
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
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

    val safeGetTasksFc = useSafeGetTasksFc(scope)

    // 当锁屏/上拉通知栏时, safeActiveWindow 没有 activityId, 但是此时 shizuku 获取到是前台 app 的 appId 和 activityId
    fun getShizukuTopActivity(): TopActivity? {
        // 平均耗时 5 ms
        val top = safeGetTasksFc()?.lastOrNull()?.topActivity ?: return null
        return TopActivity(appId = top.packageName, activityId = top.className)
    }

    fun isActivity(
        appId: String,
        activityId: String,
    ): Boolean {
        if (appId == topActivityFlow.value?.appId && activityId == topActivityFlow.value?.activityId) return true
        val r = (try {
            packageManager.getActivityInfo(
                ComponentName(
                    appId, activityId
                ), 0
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } != null)
        Log.d("isActivity", "$appId, $activityId, $r")
        return r
    }

    var lastTriggerShizukuTime = 0L
    var lastContentEventTime = 0L
    var job: Job? = null
    val singleThread = Dispatchers.IO.limitedParallelism(1)
    val eventThread = Dispatchers.Default.limitedParallelism(1)
    onDestroy {
        singleThread.cancel()
    }
    val lastQueryTimeFlow = MutableStateFlow(0L)
    val debounceTime = 500L
    fun newQueryTask() {
        job = scope.launchTry(singleThread) {
            val activityRule = getCurrentRules()
            for (rule in (activityRule.rules)) {
                if (!isActive && !rule.isOpenAd) break
                if (!isAvailableRule(rule)) continue
                val nodeVal = safeActiveWindow ?: continue
                val target = rule.query(nodeVal) ?: continue
                if (activityRule !== getCurrentRules()) break

                // 开始 action 延迟
                if (rule.actionDelay > 0 && rule.actionDelayTriggerTime == 0L) {
                    rule.triggerDelay()
                    // 防止在 actionDelay 时间内没有事件发送导致无法触发
                    scope.launch(singleThread) {
                        delay(rule.actionDelay - debounceTime)
                        lastQueryTimeFlow.value = System.currentTimeMillis()
                    }
                    continue
                }

                // 如果节点在屏幕外部, click 的结果为 null
                val actionResult = rule.performAction(context, target)
                if (actionResult.result) {
                    LogUtils.d(
                        *rule.matches.toTypedArray(),
                        NodeInfo.abNodeToNode(nodeVal, target).attr,
                        actionResult
                    )
                    insertClickLog(rule)
                }
            }
        }
    }

    scope.launch(singleThread) {
        lastQueryTimeFlow.debounce(debounceTime).collect {
            newQueryTask()
        }
    }
    onAccessibilityEvent { event ->
        if (event == null) return@onAccessibilityEvent
        if (!(event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)) return@onAccessibilityEvent

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (event.eventTime - appChangeTime > 5_000) { // app 启动 5s 内关闭限制
                if (event.eventTime - lastContentEventTime < 100) {
                    if (event.eventTime - (lastTriggerRule?.actionTriggerTime?.value
                            ?: 0) > 1000
                    ) { // 规则刚刚触发后 1s 内关闭限制
                        return@onAccessibilityEvent
                    }
                }
            }
            lastContentEventTime = event.eventTime
        }

        scope.launch(eventThread) {
            val evAppId = event.packageName?.toString() ?: return@launch
            val evActivityId = event.className?.toString() ?: return@launch
            val rightAppId = safeActiveWindow?.packageName?.toString() ?: return@launch

            if (rightAppId == evAppId) {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    // tv.danmaku.bili, com.miui.home, com.miui.home.launcher.Launcher
                    if (isActivity(evAppId, evActivityId)) {
                        topActivityFlow.value = TopActivity(
                            evAppId, evActivityId
                        )
                        activityChangeTime = System.currentTimeMillis()
                    }
                } else {
                    if (event.eventTime - lastTriggerShizukuTime > 300) {
                        val shizukuTop = getShizukuTopActivity()
                        if (shizukuTop != null && shizukuTop.appId == rightAppId) {
                            if (shizukuTop.activityId == evActivityId) {
                                activityChangeTime = System.currentTimeMillis()
                            }
                            topActivityFlow.value = shizukuTop
                        }
                        lastTriggerShizukuTime = event.eventTime
                    }
                }
            }
            if (rightAppId != topActivityFlow.value?.appId) {
                // 从 锁屏,下拉通知栏 返回等情况, 应用不会发送事件, 但是系统组件会发送事件
                val shizukuTop = getShizukuTopActivity()
                if (shizukuTop?.appId == rightAppId) {
                    topActivityFlow.value = shizukuTop
                } else {
                    topActivityFlow.value = TopActivity(rightAppId)
                }
            }

            if (evAppId != rightAppId) {
                return@launch
            }

            if (getCurrentRules().rules.isEmpty()) {
                return@launch
            }

            if (!storeFlow.value.enableService) return@launch
            val jobVal = job
            if (jobVal?.isActive == true) {
                if (openAdOptimized == true) {
                    jobVal.cancel()
                } else {
                    return@launch
                }
            }

            lastQueryTimeFlow.value = event.eventTime

            newQueryTask()
        }
    }


    var lastUpdateSubsTime = 0L
    scope.launchWhile(Dispatchers.IO) { // 自动从网络更新订阅文件
        if (storeFlow.value.updateSubsInterval > 0 && System.currentTimeMillis() - lastUpdateSubsTime < storeFlow.value.updateSubsInterval) {
            subsItemsFlow.value.forEach { subsItem ->
                if (subsItem.updateUrl == null) return@forEach
                try {
                    val newSubsRaw = SubscriptionRaw.parse(
                        client.get(subsItem.updateUrl).bodyAsText()
                    )
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
        delay(30 * 60_000) // 每 30 分钟检查一次
    }

    scope.launch(Dispatchers.IO) {
        activityRuleFlow.debounce(300).collect {
            if (storeFlow.value.enableService) {
                LogUtils.d(it.topActivity, *it.rules.map { r ->
                    "subsId:${r.subsItem.id}, gKey=${r.group.key}, gName:${r.group.name}, ruleIndex:${r.index}, rKey:${r.key}, active:${
                        isAvailableRule(r)
                    }"
                }.toTypedArray())
            } else {
                LogUtils.d(
                    it.topActivity
                )
            }
        }
    }

    var aliveView: View? = null
    val wm by lazy { context.getSystemService(WINDOW_SERVICE) as WindowManager }
    onServiceConnected {
        scope.launchTry {
            storeFlow.map(scope) { s -> s.enableAbFloatWindow }.collect {
                if (aliveView != null) {
                    withContext(Dispatchers.Main) {
                        wm.removeView(aliveView)
                    }
                }
                if (it) {
                    aliveView = View(context)
                    val lp = WindowManager.LayoutParams().apply {
                        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                        format = PixelFormat.TRANSLUCENT
                        flags =
                            flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        width = 1
                        height = 1
                    }
                    withContext(Dispatchers.Main) {
                        wm.addView(aliveView, lp)
                    }
                } else {
                    aliveView = null
                }
            }
        }
    }
    onDestroy {
        if (aliveView != null) {
            wm.removeView(aliveView)
        }
    }
}) {

    companion object {
        var service: GkdAbService? = null
        fun isRunning() = ServiceUtils.isServiceRunning(GkdAbService::class.java)

        fun execAction(gkdAction: GkdAction): ActionResult {
            val serviceVal = service ?: throw RpcError("无障碍没有运行")
            val selector = try {
                Selector.parse(gkdAction.selector)
            } catch (e: Exception) {
                throw RpcError("非法选择器")
            }

            val targetNode =
                serviceVal.safeActiveWindow?.querySelector(selector, gkdAction.quickFind)
                    ?: throw RpcError("没有选择到节点")

            return getActionFc(gkdAction.action)(serviceVal, targetNode)
        }


        suspend fun currentScreenshot() = service?.run {
            suspendCoroutine {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    takeScreenshot(Display.DEFAULT_DISPLAY,
                        application.mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(screenshot: ScreenshotResult) {
                                try {
                                    it.resume(
                                        Bitmap.wrapHardwareBuffer(
                                            screenshot.hardwareBuffer, screenshot.colorSpace
                                        )
                                    )
                                } finally {
                                    screenshot.hardwareBuffer.close()
                                }
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