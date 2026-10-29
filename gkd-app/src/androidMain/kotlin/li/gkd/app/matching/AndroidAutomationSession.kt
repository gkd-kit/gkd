package li.gkd.app.matching

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.CoroutineScope
import li.gkd.app.settings.AutomatorMode

interface AndroidAutomationSession {
    suspend fun screenshot(): Bitmap?

    val windowNodeInfo: AccessibilityNodeInfo?
    val windowInfos: List<AccessibilityWindowInfo>
    val scope: CoroutineScope
    val mode: AutomatorMode
    val matchHost: AndroidMatchHost

    fun shutdown(temp: Boolean = false)
}
