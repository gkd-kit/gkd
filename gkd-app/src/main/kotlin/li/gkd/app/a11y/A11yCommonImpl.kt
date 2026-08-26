package li.gkd.app.a11y

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.CoroutineScope
import li.gkd.app.util.AutomatorModeOption

interface A11yCommonImpl {
    suspend fun screenshot(): Bitmap?
    val windowNodeInfo: AccessibilityNodeInfo?
    val windowInfos: List<AccessibilityWindowInfo>
    val scope: CoroutineScope
    var justStarted: Boolean
    val mode: AutomatorModeOption
    val ruleEngine: A11yRuleEngine
    fun shutdown(temp: Boolean = false)
}
