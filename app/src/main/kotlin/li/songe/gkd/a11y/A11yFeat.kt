package li.songe.gkd.a11y

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import li.songe.gkd.META
import li.songe.gkd.appScope
import li.songe.gkd.isActivityVisible
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.service.A11yService
import li.songe.gkd.service.StatusService
import li.songe.gkd.shizuku.safeGetTopCpn
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.ScreenUtils
import li.songe.gkd.util.SnapshotExt
import li.songe.gkd.util.UpdateTimeOption
import li.songe.gkd.util.checkSubsUpdate
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.mapState
import li.songe.gkd.util.toast


context(service: A11yService)
fun onA11yFeatInit() = service.run {
    useAttachState()
    useAliveOverlayView()
    useCaptureVolume()
    useRuleChangedLog()
    onA11yEvent { onA11yFeatEvent(it) }
    onCreated { StatusService.autoStart() }
    onDestroyed {
        safeGetTopCpn()?.let {
            // com.android.systemui
            if (!topActivityFlow.value.sameAs(it.packageName, it.className)) {
                updateTopActivity(it.packageName, it.className)
            }
        }
    }
}

private fun A11yService.useAttachState() {
    onCreated {
        if (isActivityVisible() || META.debuggable) {
            toast("无障碍已启动")
        }
    }
    onDestroyed {
        if (isActivityVisible() || META.debuggable) {
            if (willDestroyByBlock) {
                toast("无障碍已局部关闭")
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

context(event: AccessibilityEvent)
private fun watchCheckShizukuState() {
    // 借助无障碍轮询校验 shizuku 权限, 因为 shizuku 可能无故被关闭
    if (storeFlow.value.enableShizuku) {
        val t = System.currentTimeMillis()
        if (t - lastCheckShizukuTime > 60 * 60_000L) {
            lastCheckShizukuTime = t
            appScope.launchTry(Dispatchers.IO) {
                shizukuOkState.updateAndGet()
            }
        }
    }
}

context(event: AccessibilityEvent)
private fun watchCaptureScreenshot() {
    if (!storeFlow.value.captureScreenshot) return
    val appId = event.packageName.toString()
    val appCls = event.className.toString()
    if (!event.isFullScreen && appId == "com.miui.screenshot" && appCls == "android.widget.RelativeLayout" && event.text.firstOrNull()
            ?.contentEquals("截屏缩略图") == true
    ) {
        appScope.launchTry {
            SnapshotExt.captureSnapshot(skipScreenshot = true)
        }
    }
}

private var lastUpdateSubsTime = 0L

context(event: AccessibilityEvent)
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
