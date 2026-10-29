package li.gkd.app.snapshot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.util.ScreenUtils
import li.gkd.app.util.launchLogged
import li.gkd.app.util.mapState
import li.gkd.selector.MatchOptions
import li.gkd.selector.NodeAdapter
import li.gkd.selector.Selector
import li.gkd.selector.SelectorCompileResult

private var tempEventSelector = "" to (null as Selector?)

private fun AccessibilityEvent.getEventAttr(name: String): Any? =
    when (name) {
        "name" -> className
        "desc" -> contentDescription
        "text" -> text
        else -> null
    }

private object A11yEventNodeAdapter : NodeAdapter<AccessibilityEvent>() {
    override fun getAttr(target: Any, name: String): Any? =
        when (target) {
            is AccessibilityEvent -> target.getEventAttr(name)
            is List<*> ->
                when (name) {
                    "size" -> target.size
                    else -> null
                }

            else -> null
        }

    override fun getInvoke(target: Any, name: String, args: List<Any>): Any? =
        when (target) {
            is List<*> ->
                when (name) {
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

fun captureScreenshotOnEvent(event: AccessibilityEvent) {
    if (!settings.value.captureScreenshot) return
    if (SnapshotCaptureHost.isCapturing) return
    if (event.packageName != settings.value.screenshotTargetAppId) return
    if (tempEventSelector.first != settings.value.screenshotEventSelector) {
        val result = Selector.compile(settings.value.screenshotEventSelector)
        tempEventSelector =
            settings.value.screenshotEventSelector to
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

private const val volumeChangedAction = "android.media.VOLUME_CHANGED_ACTION"

private fun createVolumeReceiver() =
    object : BroadcastReceiver() {
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

fun initVolumeSnapshotTrigger() {
    var captureVolumeReceiver: BroadcastReceiver? = null
    val changeRegister: (Boolean) -> Unit = {
        captureVolumeReceiver?.let(app::unregisterReceiver)
        captureVolumeReceiver =
            if (it) {
                createVolumeReceiver().apply {
                    ContextCompat.registerReceiver(
                        app,
                        this,
                        IntentFilter(volumeChangedAction),
                        ContextCompat.RECEIVER_EXPORTED,
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
