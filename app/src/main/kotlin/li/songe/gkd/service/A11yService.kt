package li.songe.gkd.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.WINDOW_SERVICE
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.META
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.data.ActionPerformer
import li.songe.gkd.data.ActionResult
import li.songe.gkd.data.AppRule
import li.songe.gkd.data.AttrInfo
import li.songe.gkd.data.GkdAction
import li.songe.gkd.data.ResolvedRule
import li.songe.gkd.data.RpcError
import li.songe.gkd.data.RuleStatus
import li.songe.gkd.data.clearNodeCache
import li.songe.gkd.debug.SnapshotExt
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.shizuku.safeGetTopActivity
import li.songe.gkd.shizuku.serviceWrapperFlow
import li.songe.gkd.util.OnA11yConnected
import li.songe.gkd.util.OnA11yEvent
import li.songe.gkd.util.OnCreate
import li.songe.gkd.util.OnDestroy
import li.songe.gkd.util.UpdateTimeOption
import li.songe.gkd.util.checkSubsUpdate
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.map
import li.songe.gkd.util.showActionToast
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.toast
import li.songe.selector.MatchOption
import li.songe.selector.Selector
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class A11yService : AccessibilityService(), OnCreate, OnA11yConnected, OnA11yEvent, OnDestroy {
    override fun onCreate() {
        super.onCreate()
        onCreated()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        onA11yConnected()
    }

    override val a11yEventCallbacks = mutableListOf<(AccessibilityEvent) -> Unit>()
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !event.isUseful()) return
        onA11yEvent(event)
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        onDestroyed()
        scope.cancel()
    }

    val scope = CoroutineScope(Dispatchers.Default)

    init {
        useRunningState()
        useAliveView()
        useCaptureVolume()
        onA11yEvent(::handleCaptureScreenshot)
        useAutoUpdateSubs()
        useRuleChangedLog()
        useAutoCheckShizuku()
        serviceWrapperFlow
        useMatchRule()
    }

    companion object {
        internal var weakInstance = WeakReference<A11yService>(null)
        val instance: A11yService?
            get() = weakInstance.get()
        val isRunning = MutableStateFlow(false)

        // AccessibilityInteractionClient.getInstanceForThread(threadId)
        val queryThread by lazy { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }
        val eventExecutor by lazy { Executors.newSingleThreadExecutor()!! }
        val actionThread by lazy { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }

        fun execAction(gkdAction: GkdAction): ActionResult {
            val serviceVal = instance ?: throw RpcError("无障碍没有运行")
            val selector = Selector.parseOrNull(gkdAction.selector) ?: throw RpcError("非法选择器")
            selector.checkSelector()?.let {
                throw RpcError(it)
            }
            val targetNode = serviceVal.safeActiveWindow?.querySelector(
                selector, MatchOption(
                    quickFind = gkdAction.quickFind,
                    fastQuery = gkdAction.fastQuery,
                ), createCacheTransform().transform, isRootNode = true
            ) ?: throw RpcError("没有查询到节点")

            if (gkdAction.action == null) {
                // 仅查询
                return ActionResult(
                    action = null,
                    result = true
                )
            }

            return ActionPerformer
                .getAction(gkdAction.action)
                .perform(serviceVal, targetNode, gkdAction.position)
        }


        suspend fun currentScreenshot(): Bitmap? = suspendCoroutine {
            if (instance == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                it.resume(null)
            } else {
                val callback = object : TakeScreenshotCallback {
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
                }
                instance!!.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    instance!!.application.mainExecutor,
                    callback
                )
            }
        }
    }
}

private fun A11yService.useMatchRule() {
    val context = this

    var lastTriggerShizukuTime = 0L
    var lastContentEventTime = 0L
    val events = mutableListOf<AccessibilityNodeInfo>()
    var queryTaskJob: Job? = null
    fun newQueryTask(
        byEvent: Boolean = false, byForced: Boolean = false, delayRule: ResolvedRule? = null
    ) {
        if (!storeFlow.value.enableMatch) return
        queryTaskJob = scope.launchTry(A11yService.queryThread) {
            var latestEvent = if (delayRule != null) {// 延迟规则不消耗事件
                null
            } else {
                synchronized(events) {
                    val size = events.size
                    if (size == 0 && byEvent) return@launchTry
                    val node = if (size > 1) {
                        if (META.debuggable) {
                            Log.d("latestEvent", "丢弃事件=$size")
                        }
                        null
                    } else {
                        events.lastOrNull()
                    }
                    events.clear()
                    node
                }
            }
            val activityRule = getAndUpdateCurrentRules()
            if (activityRule.currentRules.isEmpty()) {
                return@launchTry
            }
            clearNodeCache()
            for (rule in activityRule.currentRules) { // 规则数量有可能过多导致耗时过长
                if (delayRule != null && delayRule !== rule) continue
                val statusCode = rule.status
                if (statusCode == RuleStatus.Status3 && rule.matchDelayJob == null) {
                    rule.matchDelayJob = scope.launch(A11yService.actionThread) {
                        delay(rule.matchDelay)
                        rule.matchDelayJob = null
                        newQueryTask(delayRule = rule)
                    }
                }
                if (statusCode != RuleStatus.StatusOk) continue
                if (byForced && !rule.checkForced()) continue
                latestEvent?.let { n ->
                    val refreshOk = try {
                        n.refresh()
                    } catch (_: Exception) {
                        false
                    }
                    if (!refreshOk) {
                        if (META.debuggable) {
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
                    A11yService.eventExecutor.execute {
                        if (topActivityFlow.value.appId != rightAppId) {
                            val shizukuTop = safeGetTopActivity()
                            if (shizukuTop?.appId == rightAppId) {
                                updateTopActivity(shizukuTop)
                            } else {
                                updateTopActivity(TopActivity(appId = rightAppId))
                            }
                            getAndUpdateCurrentRules()
                            scope.launch(A11yService.actionThread) {
                                delay(300)
                                if (queryTaskJob?.isActive != true) {
                                    newQueryTask()
                                }
                            }
                        }
                    }
                    return@launchTry
                }
                if (!matchApp) continue
                val target = rule.query(nodeVal, latestEvent == null) ?: continue
                if (activityRule !== getAndUpdateCurrentRules()) break
                if (rule.checkDelay() && rule.actionDelayJob == null) {
                    rule.actionDelayJob = scope.launch(A11yService.actionThread) {
                        delay(rule.actionDelay)
                        rule.actionDelayJob = null
                        newQueryTask(delayRule = rule)
                    }
                    continue
                }
                if (rule.status != RuleStatus.StatusOk) break
                val actionResult = rule.performAction(context, target)
                if (actionResult.result) {
                    rule.trigger()
                    scope.launch(A11yService.actionThread) {
                        delay(300)
                        if (queryTaskJob?.isActive != true) {
                            newQueryTask()
                        }
                    }
                    showActionToast(context)
                    appScope.launchTry(Dispatchers.IO) {
                        insertClickLog(rule)
                        LogUtils.d(
                            rule.statusText(), AttrInfo.info2data(target, 0, 0), actionResult
                        )
                    }
                }
            }
            val t = System.currentTimeMillis()
            if (t - lastTriggerTime < 3000L || t - appChangeTime < 5000L) {
                scope.launch(A11yService.actionThread) {
                    delay(300)
                    if (queryTaskJob?.isActive != true) {
                        newQueryTask()
                    }
                }
            } else {
                if (activityRule.currentRules.any { r -> r.checkForced() && r.status.let { s -> s == RuleStatus.StatusOk || s == RuleStatus.Status5 } }) {
                    scope.launch(A11yService.actionThread) {
                        delay(300)
                        if (queryTaskJob?.isActive != true) {
                            newQueryTask(byForced = true)
                        }
                    }
                }
            }
        }
    }

    val skipAppIds = setOf("com.android.systemui")
    onA11yEvent { event ->
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && skipAppIds.contains(
                event.packageName.toString()
            )
        ) {
            return@onA11yEvent
        }

        val fixedEvent = event.toA11yEvent() ?: return@onA11yEvent
        if (fixedEvent.type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (fixedEvent.time - lastContentEventTime < 100 && fixedEvent.time - appChangeTime > 5000 && fixedEvent.time - lastTriggerTime > 3000) {
                return@onA11yEvent
            }
            lastContentEventTime = fixedEvent.time
        }
        if (META.debuggable) {
            Log.d(
                "AccessibilityEvent",
                "type:${event.eventType},app:${event.packageName},cls:${event.className}"
            )
        }

        // AccessibilityEvent 的 clear 方法会在后续时间被 某些系统 调用导致内部数据丢失
        // 因此不要在协程/子线程内传递引用, 此处使用 data class 保存数据
        val evAppId = fixedEvent.appId
        val evActivityId = fixedEvent.className

        A11yService.eventExecutor.execute launch@{
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
                        updateTopActivity(
                            TopActivity(
                                evAppId, evActivityId, topActivityFlow.value.number + 1
                            )
                        )
                    }
                } else {
                    if (storeFlow.value.enableShizukuActivity && fixedEvent.time - lastTriggerShizukuTime > 300) {
                        val shizukuTop = safeGetTopActivity()
                        if (shizukuTop != null && shizukuTop.appId == rightAppId) {
                            if (shizukuTop.activityId == evActivityId) {
                                updateTopActivity(
                                    TopActivity(
                                        evAppId, evActivityId, topActivityFlow.value.number + 1
                                    )
                                )
                            }
                            updateTopActivity(shizukuTop)
                        }
                        lastTriggerShizukuTime = fixedEvent.time
                    }
                }
            }
            if (rightAppId != topActivityFlow.value.appId) {
                // 从 锁屏,下拉通知栏 返回等情况, 应用不会发送事件, 但是系统组件会发送事件
                val shizukuTop = safeGetTopActivity()
                if (shizukuTop?.appId == rightAppId) {
                    updateTopActivity(shizukuTop)
                } else {
                    updateTopActivity(TopActivity(rightAppId))
                }
            }

            if (getAndUpdateCurrentRules().currentRules.isEmpty()) {
                // 放在 evAppId != rightAppId 的前面使得 TopActivity 能借助 lastTopActivity 恢复
                return@launch
            }

            if (evAppId != rightAppId) {
                return@launch
            }
            if (!storeFlow.value.enableMatch) return@launch
            val eventNode = event.safeSource
            synchronized(events) {
                val eventLog = events.lastOrNull()
                if (eventNode != null) {
                    if (eventLog == eventNode) {
                        events.removeAt(events.lastIndex)
                    }
                    events.add(eventNode)
                }
            }
            newQueryTask(eventNode != null)
        }
    }
}

private fun A11yService.useRuleChangedLog() {
    scope.launch(Dispatchers.IO) {
        activityRuleFlow.debounce(300).collect {
            if (storeFlow.value.enableMatch && it.currentRules.isNotEmpty()) {
                LogUtils.d(it.topActivity, *it.currentRules.map { r ->
                    r.statusText()
                }.toTypedArray())
            }
        }
    }
}

private fun A11yService.useRunningState() {
    onCreated {
        A11yService.weakInstance = WeakReference(this)
        A11yService.isRunning.value = true
        ManageService.autoStart()
    }
    onDestroyed {
        A11yService.weakInstance = WeakReference(null)
        A11yService.isRunning.value = false
    }
}

private fun A11yService.useAutoCheckShizuku() {
    var lastCheckShizukuTime = 0L
    onA11yEvent {
        // 借助无障碍轮询校验 shizuku 权限, 因为 shizuku 可能无故被关闭
        if ((storeFlow.value.enableShizukuActivity || storeFlow.value.enableShizukuClick) && it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {// 筛选降低判断频率
            val t = System.currentTimeMillis()
            if (t - lastCheckShizukuTime > 10 * 60_000L) {
                lastCheckShizukuTime = t
                scope.launchTry(Dispatchers.IO) {
                    shizukuOkState.updateAndGet()
                }
            }
        }
    }
}

private fun A11yService.useAliveView() {
    val context = this
    var aliveView: View? = null
    val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    suspend fun removeA11View() {
        if (aliveView != null) {
            withContext(Dispatchers.Main) {
                wm.removeView(aliveView)
            }
            aliveView = null
        }
    }

    suspend fun addA11View() {
        removeA11View()
        val tempView = View(context)
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags =
                flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = 1
            height = 1
            packageName = context.packageName
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
    }

    onA11yConnected {
        scope.launchTry {
            storeFlow.map(scope) { s -> s.enableAbFloatWindow }.collect {
                if (it) {
                    addA11View()
                } else {
                    removeA11View()
                }
            }
        }
    }
    onDestroyed {
        if (aliveView != null) {
            wm.removeView(aliveView)
        }
    }
}

private fun A11yService.useAutoUpdateSubs() {
    var lastUpdateSubsTime = System.currentTimeMillis() - 25000
    onA11yEvent {// 借助 无障碍事件 触发自动检测更新
        if (it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {// 筛选降低判断频率
            val i = storeFlow.value.updateSubsInterval
            if (i <= 0) return@onA11yEvent
            val t = System.currentTimeMillis()
            if (t - lastUpdateSubsTime > i.coerceAtLeast(UpdateTimeOption.Everyday.value)) {
                lastUpdateSubsTime = t
                checkSubsUpdate()
            }
        }
    }
}

private const val volumeChangedAction = "android.media.VOLUME_CHANGED_ACTION"
private fun createVolumeReceiver() = object : BroadcastReceiver() {
    var lastTriggerTime = -1L
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == volumeChangedAction) {
            val t = System.currentTimeMillis()
            if (t - lastTriggerTime > 3000 && !ScreenUtils.isScreenLock()) {
                lastTriggerTime = t
                appScope.launchTry {
                    SnapshotExt.captureSnapshot()
                }
            }
        }
    }
}

private fun A11yService.useCaptureVolume() {
    var captureVolumeReceiver: BroadcastReceiver? = null
    onCreated {
        scope.launch {
            storeFlow.map(scope) { s -> s.captureVolumeChange }.collect {
                if (captureVolumeReceiver != null) {
                    unregisterReceiver(captureVolumeReceiver)
                }
                captureVolumeReceiver = if (it) {
                    createVolumeReceiver().apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            registerReceiver(
                                this, IntentFilter(volumeChangedAction), Context.RECEIVER_EXPORTED
                            )
                        } else {
                            registerReceiver(this, IntentFilter(volumeChangedAction))
                        }
                    }
                } else {
                    null
                }
            }
        }
    }
    onDestroyed {
        if (captureVolumeReceiver != null) {
            unregisterReceiver(captureVolumeReceiver)
        }
    }
}

private const val interestedEvents =
    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

private fun AccessibilityEvent.isUseful(): Boolean {
    return packageName != null && className != null && eventType.and(interestedEvents) != 0
}

private val activityCache = object : LruCache<Pair<String, String>, Boolean>(128) {
    override fun create(key: Pair<String, String>): Boolean {
        return runCatching {
            app.packageManager.getActivityInfo(
                ComponentName(
                    key.first, key.second
                ), 0
            )
        }.getOrNull() != null
    }
}

private fun isActivity(
    appId: String,
    activityId: String,
): Boolean {
    if (appId == topActivityFlow.value.appId && activityId == topActivityFlow.value.activityId) return true
    val cacheKey = Pair(appId, activityId)
    return activityCache.get(cacheKey)
}

private fun handleCaptureScreenshot(event: AccessibilityEvent) {
    if (!storeFlow.value.captureScreenshot) return
    val appId = event.packageName!!
    val appCls = event.className!!
    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !event.isFullScreen && appId.contentEquals(
            "com.miui.screenshot"
        ) && appCls.contentEquals(
            "android.widget.RelativeLayout"
        ) && event.text.firstOrNull()
            ?.contentEquals("截屏缩略图") == true // [截屏缩略图, 截长屏, 发送]
    ) {
        LogUtils.d("captureScreenshot", event)
        appScope.launchTry {
            SnapshotExt.captureSnapshot(skipScreenshot = true)
        }
    }
}
