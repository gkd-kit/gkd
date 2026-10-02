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
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.snapshot.SnapshotCaptureHost
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.subscription.SubscriptionResult
import li.gkd.app.ui.option.UpdateTimeOption
import li.gkd.app.ui.rule.statusText
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ScreenUtils
import li.gkd.app.util.launchLogged
import li.gkd.app.util.mapState
import li.gkd.selector.MatchOptions
import li.gkd.selector.NodeAdapter
import li.gkd.selector.Selector
import li.gkd.selector.SelectorCompileResult

fun onA11yFeatEvent(event: AccessibilityEvent) = event.run {
    if (event.eventType == STATE_CHANGED) {
        watchCaptureScreenshot()
    }
    if (event.packageName == launcherAppId) {
        watchAutoUpdateSubs()
    }
}

private var tempEventSelector = "" to (null as Selector?)
private fun AccessibilityEvent.getEventAttr(name: String): Any? = when (name) {
    "name" -> className
    "desc" -> contentDescription
    "text" -> text
    else -> null
}

private object A11yEventNodeAdapter : NodeAdapter<AccessibilityEvent>() {
    override fun getAttr(target: Any, name: String): Any? = when (target) {
        is AccessibilityEvent -> target.getEventAttr(name)
        is List<*> -> when (name) {
            "size" -> target.size
            else -> null
        }

        else -> null
    }

    override fun getInvoke(target: Any, name: String, args: List<Any>): Any? = when (target) {
        is List<*> -> when (name) {
            "get" -> (args.singleOrNull() as? Int)?.let(target::getOrNull)
            else -> null
        }

        else -> null
    }

    override fun getName(node: AccessibilityEvent): String? = node.className?.toString()

    override fun getChildCount(node: AccessibilityEvent): Int = 0

    override fun getChild(node: AccessibilityEvent, index: Int): AccessibilityEvent? = null

    override fun getParent(node: AccessibilityEvent): AccessibilityEvent? = null

    override fun getNodeKey(node: AccessibilityEvent): Any = node
}

context(event: AccessibilityEvent)
private fun watchCaptureScreenshot() {
    if (!settings.value.captureScreenshot) return
    if (SnapshotCaptureHost.isCapturing) return
    if (event.packageName != settings.value.screenshotTargetAppId) return
    if (tempEventSelector.first != settings.value.screenshotEventSelector) {
        val result = Selector.compile(settings.value.screenshotEventSelector)
        tempEventSelector = settings.value.screenshotEventSelector to
                (result as? SelectorCompileResult.Success)?.value
    }
    val selector = tempEventSelector.second ?: return
    selector.match(event, A11yEventNodeAdapter, MatchOptions(fastQuery = false)).let {
        if (it == null) return
    }
    appScope.launchLogged {
        SnapshotCaptureHost.capture()
    }
}

private var lastUpdateSubsTime = 0L
private var autoRefreshPending = false
private fun watchAutoUpdateSubs() {
    val interval = settings.value.updateSubsInterval
    if (interval <= 0 || autoRefreshPending) return
    val currentTime = System.currentTimeMillis()
    if (
        currentTime - lastUpdateSubsTime <=
        interval.coerceAtLeast(UpdateTimeOption.Everyday.value)
    ) return
    autoRefreshPending = true
    appScope.launchLogged {
        try {
            val result = SubscriptionRepository.refresh()
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
            if (settings.value.enableMatch && it.currentRules.isNotEmpty()) {
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
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == volumeChangedAction) {
            val t = System.currentTimeMillis()
            if (t - lastVolumeTriggerTime > 3000 && !ScreenUtils.isLocked()) {
                lastVolumeTriggerTime = t
                appScope.launchLogged {
                    SnapshotCaptureHost.capture()
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
        settings.mapState(appScope) { s -> s.captureVolumeChange }.collect(changeRegister)
    }
}

var isInteractive = true
    private set
private val screenStateReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
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
                A11yRuntime.onScreenForcedActive()
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
