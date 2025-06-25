package li.songe.gkd.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector.initDefaultTypeInfo

// 在主线程调用任意获取新节点或刷新节点的API会阻塞界面导致卡顿

// 某些应用耗时 554ms
val AccessibilityService.safeActiveWindow: AccessibilityNodeInfo?
    get() = try {
        // java.lang.SecurityException: Call from user 0 as user -2 without permission INTERACT_ACROSS_USERS or INTERACT_ACROSS_USERS_FULL not allowed.
        rootInActiveWindow?.apply {
            // https://github.com/gkd-kit/gkd/issues/759
            setGeneratedTime()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }.apply {
        a11yContext.rootCache = this
    }

val AccessibilityService.safeActiveWindowAppId: String?
    get() = safeActiveWindow?.packageName?.toString()

// 某些应用耗时 300ms
val AccessibilityEvent.safeSource: AccessibilityNodeInfo?
    get() = if (className == null) {
        null // https://github.com/gkd-kit/gkd/issues/426 event.clear 已被系统调用
    } else {
        try {
            // 原因未知, 仍然报错 Cannot perform this action on a not sealed instance.
            source?.apply {
                setGeneratedTime()
            }
        } catch (_: Exception) {
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
fun AccessibilityNodeInfo.setGeneratedTime() {
    extras.putLong(A11Y_NODE_TIME_KEY, System.currentTimeMillis())
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