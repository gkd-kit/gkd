package li.songe.gkd.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.ComponentName
import android.database.ContentObserver
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.songe.gkd.app
import li.songe.gkd.service.A11yService
import li.songe.selector.initDefaultTypeInfo
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

context(vm: ViewModel)
fun useA11yServiceEnabledFlow(): StateFlow<Boolean> {
    val stateFlow = MutableStateFlow(getA11yServiceEnabled())
    val contextObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            stateFlow.value = getA11yServiceEnabled()
        }
    }
    app.contentResolver.registerContentObserver(
        Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
        false,
        contextObserver
    )
    vm.addCloseable {
        app.contentResolver.unregisterContentObserver(contextObserver)
    }
    return stateFlow
}

private fun getA11yServiceEnabled(): Boolean = app.getSecureA11yServices().any {
    ComponentName.unflattenFromString(it) == A11yService.a11yComponentName
}

const val STATE_CHANGED = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
const val CONTENT_CHANGED = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

// 某些应用耗时 300ms
private val AccessibilityEvent.safeSource: AccessibilityNodeInfo?
    get() = if (className == null) {
        null // https://github.com/gkd-kit/gkd/issues/426 event.clear 已被系统调用
    } else {
        try {
            source?.setGeneratedTime()
        } catch (_: Exception) {
            // 原因未知, 仍然报错 Cannot perform this action on a not sealed instance.
            null
        }
    }

fun AccessibilityNodeInfo.getVid(): CharSequence? {
    val id = viewIdResourceName ?: return null
    val appId = packageName ?: return null
    if (id.startsWith(appId) && id.startsWith(":id/", appId.length)) {
        return id.subSequence(
            appId.length + ":id/".length,
            id.length
        )
    }
    return null
}

// https://github.com/gkd-kit/gkd/issues/115
// https://github.com/gkd-kit/gkd/issues/650
// 限制节点遍历的数量避免内存溢出
const val MAX_CHILD_SIZE = 512
const val MAX_DESCENDANTS_SIZE = 4096

private const val A11Y_NODE_TIME_KEY = "generatedTime"
fun AccessibilityNodeInfo.setGeneratedTime(): AccessibilityNodeInfo {
    extras.putLong(A11Y_NODE_TIME_KEY, System.currentTimeMillis())
    return this
}

fun AccessibilityNodeInfo.isExpired(expiryMillis: Long): Boolean {
    val generatedTime = extras.getLong(A11Y_NODE_TIME_KEY, -1)
    if (generatedTime == -1L) {
        // https://github.com/gkd-kit/gkd/issues/759
        return true
    }
    return (System.currentTimeMillis() - generatedTime) > expiryMillis
}

val typeInfo by lazy { initDefaultTypeInfo().globalType }

val AccessibilityNodeInfo.compatChecked: Boolean?
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        when (checked) {
            AccessibilityNodeInfo.CHECKED_STATE_TRUE -> true
            AccessibilityNodeInfo.CHECKED_STATE_FALSE -> false
            AccessibilityNodeInfo.CHECKED_STATE_PARTIAL -> null
            else -> null
        }
    } else {
        @Suppress("DEPRECATION")
        isChecked
    }


private const val interestedEvents = STATE_CHANGED or CONTENT_CHANGED
val AccessibilityEvent.isUseful: Boolean
    get() = packageName != null && className != null && eventType.and(interestedEvents) != 0


suspend fun AccessibilityService.screenshot(): Bitmap? = suspendCoroutine {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
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
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            application.mainExecutor,
            callback
        )
    }
}

data class A11yEvent(
    val type: Int,
    val time: Long,
    val appId: String,
    val className: String,
    val event: AccessibilityEvent,
) {
    val safeSource: AccessibilityNodeInfo?
        get() = event.safeSource

    fun sameAs(other: A11yEvent): Boolean {
        if (other === this) return true
        return type == other.type && appId == other.appId && className == other.className
    }
}

// AccessibilityEvent 的 clear 方法会在后续时间被 某些系统 调用导致内部数据丢失, 导致异步子线程获取到的数据不一致
fun AccessibilityEvent.toA11yEvent(): A11yEvent? {
    val appId = packageName ?: return null
    val b = className ?: return null
    return A11yEvent(
        type = eventType,
        time = System.currentTimeMillis(),
        appId = appId.toString(),
        className = b.toString(),
        event = this,
    )
}
