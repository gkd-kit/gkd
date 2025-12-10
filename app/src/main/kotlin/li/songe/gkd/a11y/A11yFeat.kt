package li.songe.gkd.a11y

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.songe.gkd.appScope
import li.songe.gkd.isActivityVisible
import li.songe.gkd.permission.shizukuGrantedState
import li.songe.gkd.service.A11yService
import li.songe.gkd.service.StatusService
import li.songe.gkd.shizuku.shizukuContextFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.ScreenUtils
import li.songe.gkd.util.SnapshotExt
import li.songe.gkd.util.UpdateTimeOption
import li.songe.gkd.util.checkSubsUpdate
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.mapState
import li.songe.gkd.util.toast
import li.songe.selector.MatchOption
import li.songe.selector.QueryContext
import li.songe.selector.Selector
import li.songe.selector.Transform
import li.songe.selector.getBooleanInvoke
import li.songe.selector.getCharSequenceAttr
import li.songe.selector.getCharSequenceInvoke
import li.songe.selector.getIntInvoke


context(service: A11yService)
fun onA11yFeatInit() = service.run {
    useAttachState()
    useAliveOverlayView()
    useCaptureVolume()
    useRuleChangedLog()
    onA11yEvent { onA11yFeatEvent(it) }
    onCreated { StatusService.autoStart() }
    onDestroyed {
        shizukuContextFlow.value.topCpn()?.let {
            // com.android.systemui
            if (!topActivityFlow.value.sameAs(it.packageName, it.className)) {
                updateTopActivity(it.packageName, it.className)
            }
        }
    }
}

private fun A11yService.useAttachState() {
    onCreated {
        if (isActivityVisible()) {
            toast("无障碍已启动")
        }
    }
    onDestroyed {
        if (isActivityVisible()) {
            if (willDestroyByBlock) {
                toast("无障碍局部关闭")
            } else {
                toast("无障碍已停止")
            }
        }
    }
    onCreated { storeFlow.update { it.copy(enableService = true) } }
    onDestroyed {
        if (!willDestroyByBlock) {
            storeFlow.update { it.copy(enableService = false) }
        }
    }
}

private fun onA11yFeatEvent(event: AccessibilityEvent) = event.run {
    if (event.eventType == STATE_CHANGED) {
        watchCaptureScreenshot()
        if (event.packageName == launcherAppId) {
            watchCheckShizukuState()
            watchAutoUpdateSubs()
        }
    }
}

private var lastCheckShizukuTime = 0L
private fun watchCheckShizukuState() {
    // 借助无障碍轮询校验 shizuku 权限, 因为 shizuku 可能无故被关闭
    if (storeFlow.value.enableShizuku) {
        val t = System.currentTimeMillis()
        if (t - lastCheckShizukuTime > 60 * 60_000L) {
            lastCheckShizukuTime = t
            appScope.launchTry(Dispatchers.IO) {
                shizukuGrantedState.updateAndGet()
            }
        }
    }
}

private var tempEventSelector = "" to (null as Selector?)
private fun AccessibilityEvent.getEventAttr(name: String): Any? = when (name) {
    "name" -> className
    "desc" -> contentDescription
    "text" -> text
    else -> null
}

private val a11yEventTransform by lazy {
    Transform<AccessibilityEvent>(
        getAttr = { target, name ->
            when (target) {
                is QueryContext<*> -> when (name) {
                    "prev" -> target.prev
                    "current" -> target.current
                    else -> (target.current as AccessibilityEvent).getEventAttr(name)
                }

                is CharSequence -> getCharSequenceAttr(target, name)
                is AccessibilityEvent -> target.getEventAttr(name)
                is List<*> -> when (name) {
                    "size" -> target.size
                    else -> null
                }

                else -> null
            }
        },
        getInvoke = { target, name, args ->
            Log.d("A11yEventTransform", "getInvoke: $name(${args.joinToString()}) on $target")
            when (target) {
                is Int -> getIntInvoke(target, name, args)
                is Boolean -> getBooleanInvoke(target, name, args)
                is CharSequence -> getCharSequenceInvoke(target, name, args)
                is List<*> -> when (name) {
                    "get" -> {
                        (args.singleOrNull() as? Int)?.let { index ->
                            target.getOrNull(index)
                        }
                    }

                    else -> null
                }

                else -> null
            }
        },
        getName = { it.className },
        getChildren = { emptySequence() },
        getParent = { null }
    )
}

context(event: AccessibilityEvent)
private fun watchCaptureScreenshot() {
    if (!storeFlow.value.captureScreenshot) return
    if (event.packageName != storeFlow.value.screenshotTargetAppId) return
    if (tempEventSelector.first != storeFlow.value.screenshotEventSelector) {
        tempEventSelector =
            storeFlow.value.screenshotEventSelector to Selector.parseOrNull(storeFlow.value.screenshotEventSelector)
    }
    val selector = tempEventSelector.second ?: return
    selector.match(event, a11yEventTransform, MatchOption(fastQuery = false)).let {
        if (it == null) return
    }
    appScope.launchTry {
        SnapshotExt.captureSnapshot(skipScreenshot = true)
    }
}

private var lastUpdateSubsTime = 0L
private fun watchAutoUpdateSubs() {
    val i = storeFlow.value.updateSubsInterval
    if (i <= 0) return
    val t = System.currentTimeMillis()
    if (t - lastUpdateSubsTime > i.coerceAtLeast(UpdateTimeOption.Everyday.value)) {
        lastUpdateSubsTime = t
        checkSubsUpdate()
    }
}

private fun A11yService.useAliveOverlayView() {
    val context = this
    var aliveView: View? = null
    val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    fun removeA11View() {
        if (aliveView != null) {
            wm.removeView(aliveView)
            aliveView = null
        }
    }

    fun addA11View() {
        removeA11View()
        val tempView = View(context)
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags =
                flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            gravity = Gravity.START or Gravity.TOP
            width = 1
            height = 1
            packageName = context.packageName
        }
        wm.addView(tempView, lp)
    }
    onA11yConnected { addA11View() }
    onDestroyed { removeA11View() }
}

private const val volumeChangedAction = "android.media.VOLUME_CHANGED_ACTION"
private fun createVolumeReceiver() = object : BroadcastReceiver() {
    var lastVolumeTriggerTime = -1L
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == volumeChangedAction) {
            val t = System.currentTimeMillis()
            if (t - lastVolumeTriggerTime > 3000 && !ScreenUtils.isScreenLock()) {
                lastVolumeTriggerTime = t
                appScope.launchTry {
                    SnapshotExt.captureSnapshot()
                }
            }
        }
    }
}

private fun A11yService.useCaptureVolume() {
    var captureVolumeReceiver: BroadcastReceiver? = null
    val changeRegister: (Boolean) -> Unit = {
        if (captureVolumeReceiver != null) {
            unregisterReceiver(captureVolumeReceiver)
        }
        captureVolumeReceiver = if (it) {
            createVolumeReceiver().apply {
                ContextCompat.registerReceiver(
                    this@useCaptureVolume,
                    this,
                    IntentFilter(volumeChangedAction),
                    ContextCompat.RECEIVER_EXPORTED
                )
            }
        } else {
            null
        }
    }
    onCreated {
        scope.launch {
            storeFlow.mapState(scope) { s -> s.captureVolumeChange }.collect(changeRegister)
        }
    }
    onDestroyed {
        if (captureVolumeReceiver != null) {
            unregisterReceiver(captureVolumeReceiver)
        }
    }
}

private fun A11yService.useRuleChangedLog() {
    scope.launch(Dispatchers.Default) {
        activityRuleFlow.debounce(300).collect {
            if (storeFlow.value.enableMatch && it.currentRules.isNotEmpty()) {
                LogUtils.d(it.topActivity, *it.currentRules.map { r ->
                    r.statusText()
                }.toTypedArray())
            }
        }
    }
}
