package li.gkd.app.a11y

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.store.storeFlow
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ScreenUtils
import li.gkd.app.snapshot.SnapshotCapture
import li.gkd.app.util.SubscriptionResult
import li.gkd.app.util.SubscriptionStore
import li.gkd.app.util.UpdateTimeOption
import li.gkd.app.util.launchTry
import li.gkd.app.util.mapState
import li.gkd.selector.MatchOption
import li.gkd.selector.QueryContext
import li.gkd.selector.Selector
import li.gkd.selector.Transform
import li.gkd.selector.getBooleanInvoke
import li.gkd.selector.getCharSequenceAttr
import li.gkd.selector.getCharSequenceInvoke
import li.gkd.selector.getIntInvoke


fun onA11yFeatEvent(event: AccessibilityEvent) = event.run {
    if (event.eventType == STATE_CHANGED) {
        watchCaptureScreenshot()
        if (event.packageName == launcherAppId) {
            watchAutoUpdateSubs()
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
    if (SnapshotCapture.isCapturing) return
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
        SnapshotCapture.capture()
    }
}

private var lastUpdateSubsTime = 0L
private var autoRefreshPending = false
private fun watchAutoUpdateSubs() {
    val interval = storeFlow.value.updateSubsInterval
    if (interval <= 0 || autoRefreshPending) return
    val currentTime = System.currentTimeMillis()
    if (
        currentTime - lastUpdateSubsTime <=
        interval.coerceAtLeast(UpdateTimeOption.Everyday.value)
    ) return
    autoRefreshPending = true
    appScope.launchTry {
        try {
            val result = SubscriptionStore.refresh()
            if (result !is SubscriptionResult.Busy) {
                lastUpdateSubsTime = currentTime
            }
        } finally {
            autoRefreshPending = false
        }
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
                    SnapshotCapture.capture()
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
