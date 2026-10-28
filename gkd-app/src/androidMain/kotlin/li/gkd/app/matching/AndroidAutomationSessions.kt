package li.gkd.app.matching

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.atomicfu.atomic
import li.gkd.app.matching.query.readA11yWindow
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.service.A11yService
import li.gkd.app.settings.AutomatorMode
import li.gkd.app.util.ThreadUtils

object AndroidAutomationSessions {
    fun compatWindows(): List<AccessibilityWindowInfo> {
        return readA11yWindow(MatchingRuntime.coordinator.diagnostics, "Windows") {
            service?.windowInfos
        }.orEmpty()
    }
}
