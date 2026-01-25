package li.songe.gkd.a11y

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import li.songe.gkd.app
import li.songe.gkd.appScope
import li.songe.gkd.permission.shizukuGrantedState
import li.songe.gkd.store.storeFlow
import li.songe.gkd.util.LogUtils
import li.songe.gkd.util.ScreenUtils
import li.songe.gkd.util.SnapshotExt
import li.songe.gkd.util.UpdateTimeOption
import li.songe.gkd.util.checkSubsUpdate
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.mapState
import li.songe.selector.MatchOption
import li.songe.selector.QueryContext
import li.songe.selector.Selector
import li.songe.selector.Transform
import li.songe.selector.getBooleanInvoke
import li.songe.selector.getCharSequenceAttr
import li.songe.selector.getCharSequenceInvoke
import li.songe.selector.getIntInvoke


fun onA11yFeatEvent(event: AccessibilityEvent) = event.run {
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

private fun initRuleChangedLog() {
    appScope.launch(Dispatchers.Default) {
        activityRuleFlow.debounce(300).drop(1).collect {
            if (storeFlow.value.enableMatch && it.currentRules.isNotEmpty()) {
                LogUtils.d(it.topActivity, *it.currentRules.map { r ->
                    r.statusText()
                }.toTypedArray())
            }
        }
    }
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

private fun initCaptureVolume() {
    var captureVolumeReceiver: BroadcastReceiver? = null
    val changeRegister: (Boolean) -> Unit = {
        captureVolumeReceiver?.let(app::unregisterReceiver)
        captureVolumeReceiver = if (it) {
            createVolumeReceiver().apply {
                ContextCompat.registerReceiver(
                    app,
                    this,
                    IntentFilter(volumeChangedAction),
                    ContextCompat.RECEIVER_EXPORTED
                )
            }
        } else {
            null
        }
    }
    appScope.launch(Dispatchers.IO) {
        storeFlow.mapState(appScope) { s -> s.captureVolumeChange }.collect(changeRegister)
    }
}

var isInteractive = true
    private set
private val screenStateReceiver = object : BroadcastReceiver() {
    override fun onReceive(
        context: Context?,
        intent: Intent?
    ) {
        val action = intent?.action ?: return
        LogUtils.d("screenStateReceiver->${action}")
        isInteractive = when (action) {
            Intent.ACTION_SCREEN_ON -> true
            Intent.ACTION_SCREEN_OFF -> false
            Intent.ACTION_USER_PRESENT -> true
            else -> isInteractive
        }
        if (isInteractive) {
            val t = System.currentTimeMillis()
            if (t - appChangeTime > 500) { // 37.872(a11y) -> 38.228(onReceive)
                A11yRuleEngine.onScreenForcedActive()
            }
        }
    }
}

private fun initScreenStateReceiver() {
    isInteractive = app.powerManager.isInteractive
    ContextCompat.registerReceiver(
        app,
        screenStateReceiver,
        IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        },
        ContextCompat.RECEIVER_EXPORTED
    )
}

fun initA11yFeat() {
    initRuleChangedLog()
    initCaptureVolume()
    initScreenStateReceiver()
}
