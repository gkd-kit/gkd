package li.gkd.app.util

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Point
import li.gkd.app.app
import li.gkd.app.model.ScreenSize

object ScreenUtils {
    fun getSize(): ScreenSize =
        if (AndroidTarget.R) {
            val b = app.windowManager.currentWindowMetrics.bounds
            ScreenSize(b.width(), b.height())
        } else {
            val p =
                Point().apply {
                    @Suppress("DEPRECATION") app.compatDisplay.getRealSize(this)
                }
            ScreenSize(p.x, p.y)
        }

    fun getDensityDpi(): Int = Resources.getSystem().displayMetrics.densityDpi

    fun isLandscape(): Boolean {
        return app.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    @Suppress("DEPRECATION")
    fun isLocked(): Boolean = app.keyguardManager.inKeyguardRestrictedInputMode()

    fun contains(x: Float, y: Float): Boolean {
        val size = getSize()
        return 0 <= x && 0 <= y && x <= size.width && y <= size.height
    }
}
