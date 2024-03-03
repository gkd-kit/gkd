package li.songe.gkd.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.util.LruCache
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.BuildConfig
import li.songe.gkd.appScope
import li.songe.gkd.composition.CompositionAbService
import li.songe.gkd.composition.CompositionExt.useLifeCycleLog
import li.songe.gkd.composition.CompositionExt.useScope
import li.songe.gkd.data.ActionResult
import li.songe.gkd.data.AppRule
import li.songe.gkd.data.AttrInfo
import li.songe.gkd.data.GkdAction
import li.songe.gkd.data.RpcError
import li.songe.gkd.data.RuleStatus
import li.songe.gkd.data.getActionFc
import li.songe.gkd.debug.SnapshotExt
import li.songe.gkd.shizuku.shizukuIsSafeOK
import li.songe.gkd.shizuku.useSafeGetTasksFc
import li.songe.gkd.shizuku.useShizukuAliveState
import li.songe.gkd.util.VOLUME_CHANGED_ACTION
import li.songe.gkd.util.checkSubsUpdate
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.map
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.toast
import li.songe.selector.Selector
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class GkdAbService : CompositionAbService({
    useLifeCycleLog()
    updateLauncherAppId()

    val context = this as GkdAbService
    val scope = useScope()

    service = context
    onDestroy {
        service = null
    }

    val shizukuAliveFlow = useShizukuAliveState()
    val shizukuGrantFlow = MutableStateFlow(false)
    var lastCheckShizukuTime = 0L
    onAccessibilityEvent { // 借助无障碍轮询校验 shizuku 权限
        if (storeFlow.value.enableShizuku && it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {// 筛选降低判断频率
            val t = System.currentTimeMillis()
            if (t - lastCheckShizukuTime > 30_000L) {
                lastCheckShizukuTime = t
                scope.launchTry(Dispatchers.IO) {
                    shizukuGrantFlow.value = if (shizukuAliveFlow.value) {
                        shizukuIsSafeOK()
                    } else {
                        false
                    }
                }
            }
        }
    }
    val safeGetTasksFc = useSafeGetTasksFc(scope, shizukuGrantFlow, shizukuAliveFlow)

    // 当锁屏/上拉通知栏时, safeActiveWindow 没有 activityId, 但是此时 shizuku 获取到是前台 app 的 appId 和 activityId
    fun getShizukuTopActivity(): TopActivity? {
        if (!storeFlow.value.enableShizuku) return null
        // 平均耗时 5 ms
        val top = safeGetTasksFc()?.lastOrNull()?.topActivity ?: return null
        return TopActivity(appId = top.packageName, activityId = top.className)
    }
    shizukuTopActivityGetter = {
        getShizukuTopActivity()
    }
    onDestroy {
        shizukuTopActivityGetter = null
    }

    val activityCache = object : LruCache<Pair<String, String>, Boolean>(128) {
        override fun create(key: Pair<String, String>): Boolean {
            return kotlin.runCatching {
                packageManager.getActivityInfo(
                    ComponentName(
                        key.first, key.second
                    ), 0
                )
            }.getOrNull() != null
        }
    }

    fun isActivity(
        appId: String,
        activityId: String,
    ): Boolean {
        if (appId == topActivityFlow.value.appId && activityId == topActivityFlow.value.activityId) return true
        val cacheKey = Pair(appId, activityId)
        return activityCache.get(cacheKey)
    }

    var lastTriggerShizukuTime = 0L
    var lastContentEventTime = 0L
    val queryThread = Dispatchers.IO.limitedParallelism(1)
    val actionThread = Dispatchers.IO.limitedParallelism(1)
    onDestroy {
        queryThread.cancel()
        actionThread.cancel()
    }
    val events = mutableListOf<AccessibilityNodeInfo>()
    var queryTaskJob: Job? = null
    fun newQueryTask(byEvent: Boolean = false) {
        queryTaskJob = scope.launchTry(queryThread) {
            var latestEvent = synchronized(events) {
                val size = events.size
                if (size == 0 && byEvent) return@launchTry
                val pair = if (size > 1) {
                    if (BuildConfig.DEBUG) {
                        Log.d("latestEvent", "丢弃事件=$size")
                    }
                    null
                } else {
                    events.lastOrNull()
                }
                events.clear()
                pair
            }
            val activityRule = getAndUpdateCurrentRules()
            for (rule in (activityRule.currentRules)) { // 规则数量有可能过多导致耗时过长
                val statusCode = rule.status
                if (statusCode == RuleStatus.Status3 && rule.matchDelayJob == null) {
                    rule.matchDelayJob = scope.launch(queryThread) {
                        delay(rule.matchDelay)
                        rule.matchDelayJob = null
                        newQueryTask()
                    }
                }
                if (statusCode != RuleStatus.StatusOk) continue
                latestEvent?.let { n ->
                    val refreshOk = try {
                        n.refresh()
                    } catch (e: Exception) {
                        false
                    }
                    if (!refreshOk) {
                        if (BuildConfig.DEBUG) {
                            Log.d("latestEvent", "最新事件已过期")
                        }
                        latestEvent = null
                    }
                }
                val nodeVal = (latestEvent ?: safeActiveWindow) ?: continue
                val rightAppId = nodeVal.packageName?.toString() ?: break
                val matchApp = rule.matchActivity(
                    rightAppId
                )
                if (topActivityFlow.value.appId != rightAppId || (!matchApp && rule is AppRule)) {
                    eventExecutor.execute {
                        if (topActivityFlow.value.appId != rightAppId) {
                            val shizukuTop = getShizukuTopActivity()
                            if (shizukuTop?.appId == rightAppId) {
                                topActivityFlow.value = shizukuTop
                            } else {
                                topActivityFlow.value = TopActivity(appId = rightAppId)
                            }
                            getAndUpdateCurrentRules()
                        }
                    }
                    return@launchTry
                }
                if (!matchApp) continue
                val target = rule.query(nodeVal, defaultCacheTransform)
                defaultCacheTransform.indexCache.clear()
                if (target == null) {
                    continue
                }
                if (activityRule !== getAndUpdateCurrentRules()) break
                if (rule.checkDelay() && rule.actionDelayJob == null) {
                    rule.actionDelayJob = scope.launch(queryThread) {
                        delay(rule.actionDelay)
                        rule.actionDelayJob = null
                        newQueryTask()
                    }
                    continue
                }
                if (rule.status != RuleStatus.StatusOk) break
                val actionResult = rule.performAction(context, target)
                if (actionResult.result) {
                    rule.trigger()
                    scope.launch(actionThread) {
                        delay(300)
                        if (queryTaskJob?.isActive != true) {
                            newQueryTask()
                        }
                    }
                    if (storeFlow.value.toastWhenClick) {
                        toast(storeFlow.value.clickToast)
                    }
                    appScope.launchTry(Dispatchers.IO) {
                        insertClickLog(rule)
                        LogUtils.d(
                            rule.statusText(),
                            AttrInfo.info2data(target, 0, 0),
                            actionResult
                        )
                    }
                }
            }
            val t = System.currentTimeMillis()
            if (t - lastTriggerTime < 3000L || t - appChangeTime < 5000L) {
                scope.launch(actionThread) {
                    delay(300)
                    if (queryTaskJob?.isActive != true) {
                        newQueryTask()
                    }
                }
            }
        }
    }

    val skipAppIds = listOf("com.android.systemui")
    onAccessibilityEvent { event ->
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            skipAppIds.contains(event.packageName.toString())
        ) {
            return@onAccessibilityEvent
        }

        val fixedEvent = event.toAbEvent() ?: return@onAccessibilityEvent
        if (fixedEvent.type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (fixedEvent.time - lastContentEventTime < 100 && fixedEvent.time - appChangeTime > 5000 && fixedEvent.time - lastTriggerTime > 3000) {
                return@onAccessibilityEvent
            }
            lastContentEventTime = fixedEvent.time
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                "AccessibilityEvent",
                "type:${event.eventType},app:${event.packageName},cls:${event.className}"
            )
        }

        // AccessibilityEvent 的 clear 方法会在后续时间被 某些系统 调用导致内部数据丢失
        // 因此不要在协程/子线程内传递引用, 此处使用 data class 保存数据
        val evAppId = fixedEvent.appId
        val evActivityId = fixedEvent.className


        eventExecutor.execute launch@{
            val eventNode = if (event.className == null) {
                null // https://github.com/gkd-kit/gkd/issues/426 event.clear 已被系统调用
            } else {
                try {
                    // 仍然报错 Cannot perform this action on a not sealed instance.
                    // TODO 原因未知
                    event.source
                } catch (e: Exception) {
                    null
                }
            }
            val oldAppId = topActivityFlow.value.appId
            val rightAppId = if (oldAppId == evAppId) {
                oldAppId
            } else {
                safeActiveWindow?.packageName?.toString() ?: return@launch
            }
            if (rightAppId == evAppId) {
                if (fixedEvent.type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    // tv.danmaku.bili, com.miui.home, com.miui.home.launcher.Launcher
                    if (isActivity(evAppId, evActivityId)) {
                        topActivityFlow.value = TopActivity(
                            evAppId, evActivityId, topActivityFlow.value.number + 1
                        )
                    }
                } else {
                    if (storeFlow.value.enableShizuku && fixedEvent.time - lastTriggerShizukuTime > 300) {
                        val shizukuTop = getShizukuTopActivity()
                        if (shizukuTop != null && shizukuTop.appId == rightAppId) {
                            if (shizukuTop.activityId == evActivityId) {
                                topActivityFlow.value = TopActivity(
                                    evAppId, evActivityId, topActivityFlow.value.number + 1
                                )
                            }
                            topActivityFlow.value = shizukuTop
                        }
                        lastTriggerShizukuTime = fixedEvent.time
                    }
                }
            }
            if (rightAppId != topActivityFlow.value.appId) {
                // 从 锁屏,下拉通知栏 返回等情况, 应用不会发送事件, 但是系统组件会发送事件
                val shizukuTop = getShizukuTopActivity()
                if (shizukuTop?.appId == rightAppId) {
                    topActivityFlow.value = shizukuTop
                } else {
                    topActivityFlow.value = TopActivity(rightAppId)
                }
            }

            if (getAndUpdateCurrentRules().currentRules.isEmpty()) {
                // 放在 evAppId != rightAppId 的前面使得 TopActivity 能借助 lastTopActivity 恢复
                return@launch
            }

            if (evAppId != rightAppId) {
                return@launch
            }
            if (!storeFlow.value.enableService) return@launch
            synchronized(events) {
                val eventLog = events.lastOrNull()
                if (eventNode != null) {
                    if (eventLog == eventNode) {
                        events.removeLast()
                    }
                    events.add(eventNode)
                }
            }
            newQueryTask(eventNode != null)
        }
    }

    var lastUpdateSubsTime = 0L
    onAccessibilityEvent {// 借助 无障碍事件 触发自动检测更新
        if (it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {// 筛选降低判断频率
            val i = storeFlow.value.updateSubsInterval
            if (i <= 0) return@onAccessibilityEvent
            val t = System.currentTimeMillis()
            if (t - lastUpdateSubsTime > i.coerceAtLeast(60 * 60_000)) {
                lastUpdateSubsTime = t
                checkSubsUpdate()
            }
        }
    }

    scope.launch(Dispatchers.IO) {
        activityRuleFlow.debounce(300).collect {
            if (storeFlow.value.enableService) {
                LogUtils.d(it.topActivity, *it.currentRules.map { r ->
                    r.statusText()
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
                    val tempView = View(context)
                    val lp = WindowManager.LayoutParams().apply {
                        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                        format = PixelFormat.TRANSLUCENT
                        flags =
                            flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        width = 1
                        height = 1
                    }
                    withContext(Dispatchers.Main) {
                        try {
                            // 在某些机型创建失败, 原因未知
                            wm.addView(tempView, lp)
                            aliveView = tempView
                        } catch (e: Exception) {
                            LogUtils.d("创建无障碍悬浮窗失败", e)
                            toast("创建无障碍悬浮窗失败")
                            storeFlow.update { store ->
                                store.copy(enableAbFloatWindow = false)
                            }
                        }
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


    fun createVolumeReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            var lastTriggerTime = -1L
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == VOLUME_CHANGED_ACTION) {
                    val t = System.currentTimeMillis()
                    if (t - lastTriggerTime > 3000 && !ScreenUtils.isScreenLock()) {
                        lastTriggerTime = t
                        scope.launchTry(Dispatchers.IO) {
                            SnapshotExt.captureSnapshot()
                            toast("快照成功")
                        }
                    }
                }
            }
        }
    }

    var captureVolumeReceiver: BroadcastReceiver? = null
    scope.launch {
        storeFlow.map(scope) { s -> s.captureVolumeChange }.collect {
            if (captureVolumeReceiver != null) {
                context.unregisterReceiver(captureVolumeReceiver)
            }
            captureVolumeReceiver = if (it) {
                createVolumeReceiver().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.registerReceiver(
                            this, IntentFilter(VOLUME_CHANGED_ACTION), Context.RECEIVER_EXPORTED
                        )
                    } else {
                        context.registerReceiver(this, IntentFilter(VOLUME_CHANGED_ACTION))
                    }
                }
            } else {
                null
            }
        }
    }
    onDestroy {
        if (captureVolumeReceiver != null) {
            context.unregisterReceiver(captureVolumeReceiver)
        }
    }

    onAccessibilityEvent { e ->
        if (!storeFlow.value.captureScreenshot) return@onAccessibilityEvent
        val appId = e.packageName ?: return@onAccessibilityEvent
        val appCls = e.className ?: return@onAccessibilityEvent
        if (appId.contentEquals("com.miui.screenshot") && e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !e.isFullScreen && appCls.contentEquals(
                "android.widget.RelativeLayout"
            ) && e.text.firstOrNull()?.contentEquals("截屏缩略图") == true // [截屏缩略图, 截长屏, 发送]
        ) {
            LogUtils.d("captureScreenshot", e)
            scope.launchTry(Dispatchers.IO) {
                SnapshotExt.captureSnapshot(skipScreenshot = true)
            }
        }
    }

    isRunning.value = true
    onDestroy {
        isRunning.value = false
    }

    ManageService.autoStart(context)
    onDestroy {
        if (!storeFlow.value.enableStatusService && ManageService.isRunning.value) {
            ManageService.stop()
        }
    }
}) {

    companion object {

        var shizukuTopActivityGetter: (() -> TopActivity?)? = null

        val eventExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
        var service: GkdAbService? = null

        val isRunning = MutableStateFlow(false)

        fun execAction(gkdAction: GkdAction): ActionResult {
            val serviceVal = service ?: throw RpcError("无障碍没有运行")
            val selector = Selector.parseOrNull(gkdAction.selector) ?: throw RpcError("非法选择器")
            selector.checkSelector()?.let {
                throw RpcError(it)
            }
            val targetNode =
                serviceVal.safeActiveWindow?.querySelector(
                    selector,
                    gkdAction.quickFind,
                    if (selector.canCacheIndex) createCacheTransform().transform else null
                ) ?: throw RpcError("没有查询到节点")

            if (gkdAction.action == null) {
                // 仅查询
                return ActionResult(
                    action = null,
                    result = true
                )
            }

            return getActionFc(gkdAction.action)(serviceVal, targetNode, gkdAction.position)
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