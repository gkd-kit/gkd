package li.songe.gkd.priv

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityServiceInfoHidden
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import li.songe.gkd.app
import li.songe.gkd.service.A11yService
import li.songe.gkd.util.AndroidTarget

fun createUiAutomationServiceInfo(): AccessibilityServiceInfo {
    val resolveInfo = ResolveInfo().apply {
        serviceInfo = app.packageManager.getServiceInfo(
            A11yService.a11yCn,
            PackageManager.GET_META_DATA,
        )
    }
    val rawInfo = AccessibilityServiceInfoHidden(resolveInfo, app).toPublic
    return AccessibilityServiceInfo().apply {
        eventTypes = rawInfo.eventTypes
        feedbackType = rawInfo.feedbackType
        flags = rawInfo.flags or AccessibilityServiceInfoHidden.FLAG_FORCE_DIRECT_BOOT_AWARE
        toHidden.setCapabilities(rawInfo.capabilities)
        notificationTimeout = rawInfo.notificationTimeout
        if (AndroidTarget.UPSIDE_DOWN_CAKE) {
            toHidden.setAccessibilityTool(true)
        }
    }
}
