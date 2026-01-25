package li.songe.gkd.util

import android.annotation.SuppressLint
import android.content.res.Resources
import android.view.WindowInsets
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

    fun checkStatusBarVisible(): Boolean? {
        return if (AndroidTarget.R) {
            // 后台/小窗模式下依然可判断
            app.windowManager.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.statusBars()).top > 0
        } else {
            null
        }
    }
}