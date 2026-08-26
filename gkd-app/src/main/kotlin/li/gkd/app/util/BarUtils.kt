package li.songe.gkd.util

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Rect
import android.view.WindowInsets
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.WorkerThread
import li.songe.gkd.a11y.A11yRuleEngine
import li.songe.gkd.app

@SuppressLint("DiscouragedApi", "InternalInsetResource")
object BarUtils {
    fun getNavBarHeight(): Int {
        val res = Resources.getSystem()
        val resourceId = res.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId != 0) {
            res.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }

    fun getStatusBarHeight(): Int {
        val resources = Resources.getSystem()
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return resources.getDimensionPixelSize(resourceId)
    }

    @WorkerThread
    fun checkStatusBarVisible(): Boolean? {
        val r = if (AndroidTarget.R) {
            // 后台/小窗模式下依然可判断
            app.windowManager.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.statusBars()).top > 0
        } else {
            null
        }
        if (r == false) return r
        val windows = A11yRuleEngine.compatWindows()
        val rect = Rect() // Rect(0, 0 - 1280, 152)
        if (windows.isNotEmpty()) {
            return windows.any { w ->
                w.getBoundsInScreen(rect)
                w.type == AccessibilityWindowInfo.TYPE_SYSTEM
                        && !w.isFocused && !w.isActive
                        && rect.top == 0 && rect.left == 0 && rect.right == ScreenUtils.getScreenWidth()
                        && rect.bottom <= getStatusBarHeight() * 2
            }
        }
        return r
    }
}