package li.songe.gkd.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.LogUtils
import li.songe.gkd.META
import li.songe.selector.MismatchExpressionTypeException
import li.songe.selector.MismatchOperatorTypeException
import li.songe.selector.MismatchParamTypeException
import li.songe.selector.Selector
import li.songe.selector.UnknownIdentifierException
import li.songe.selector.UnknownIdentifierMethodException
import li.songe.selector.UnknownIdentifierMethodParamsException
import li.songe.selector.UnknownMemberException
import li.songe.selector.UnknownMemberMethodException
import li.songe.selector.UnknownMemberMethodParamsException
import li.songe.selector.initDefaultTypeInfo

// 某些应用耗时 554ms
val AccessibilityService.safeActiveWindow: AccessibilityNodeInfo?
    get() = try {
        // java.lang.SecurityException: Call from user 0 as user -2 without permission INTERACT_ACROSS_USERS or INTERACT_ACROSS_USERS_FULL not allowed.
        rootInActiveWindow.apply {
            a11yContext.rootCache = this
        }
        // 在主线程调用会阻塞界面导致卡顿
    } catch (e: Exception) {
        e.printStackTrace()
        null
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
            source
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

private val typeInfo by lazy { initDefaultTypeInfo().globalType }

fun Selector.checkSelector(): String? {
    val error = checkType(typeInfo) ?: return null
    if (META.debuggable) {
        LogUtils.d(
            "Selector check error",
            source,
            error.message
        )
    }
    return when (error) {
        is MismatchExpressionTypeException -> "不匹配表达式类型:${error.exception.stringify()}"
        is MismatchOperatorTypeException -> "不匹配操作符类型:${error.exception.stringify()}"
        is MismatchParamTypeException -> "不匹配参数类型:${error.call.stringify()}"
        is UnknownIdentifierException -> "未知属性:${error.value.stringify()}"
        is UnknownIdentifierMethodException -> "未知方法:${error.value.stringify()}"
        is UnknownMemberException -> "未知属性:${error.value.stringify()}"
        is UnknownMemberMethodException -> "未知方法:${error.value.stringify()}"
        is UnknownIdentifierMethodParamsException -> "未知方法参数:${error.value.stringify()}"
        is UnknownMemberMethodParamsException -> "未知方法参数:${error.value.stringify()}"
    }
}