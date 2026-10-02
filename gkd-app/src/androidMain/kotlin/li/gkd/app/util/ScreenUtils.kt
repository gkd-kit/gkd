package li.gkd.app.util

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Point
import androidx.compose.ui.unit.IntSize
import li.gkd.app.app

object ScreenUtils {
    fun getSize(): IntSize = if (AndroidTarget.R) {
        val b = app.windowManager.currentWindowMetrics.bounds
        IntSize(b.width(), b.height())
    } else {
        val p = Point().apply {
            @Suppress("DEPRECATION")
            app.compatDisplay.getRealSize(this)
        }
        IntSize(p.x, p.y)
    }

    fun getWidth(): Int = getSize().width

    fun getHeight(): Int = getSize().height

    fun getDensityDpi(): Int = Resources.getSystem().displayMetrics.densityDpi

    fun isLandscape(): Boolean {
        return app.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    @Suppress("DEPRECATION")
    fun isLocked(): Boolean = app.keyguardManager.inKeyguardRestrictedInputMode()

    fun contains(x: Float, y: Float): Boolean {
        val (w, h) = getSize()
        return 0 <= x && 0 <= y && x <= w && y <= h
    }
}
