package li.songe.gkd.util

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Point
import androidx.compose.ui.unit.IntSize
import li.songe.gkd.app


object ScreenUtils {
    fun getScreenSize(): IntSize = if (AndroidTarget.R) {
        val b = app.windowManager.currentWindowMetrics.bounds
        IntSize(b.width(), b.height())
    } else {
        val p = Point().apply {
            @Suppress("DEPRECATION")
            app.compatDisplay.getRealSize(this)
        }
        IntSize(p.x, p.y)
    }

    fun getScreenWidth(): Int = getScreenSize().width

    fun getScreenHeight(): Int = getScreenSize().height

    fun getScreenDensityDpi(): Int = Resources.getSystem().displayMetrics.densityDpi

    fun isLandscape(): Boolean {
        return app.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    @Suppress("DEPRECATION")
    fun isScreenLock(): Boolean = app.keyguardManager.inKeyguardRestrictedInputMode()

    fun inScreen(x: Float, y: Float): Boolean {
        val (w, h) = getScreenSize()
        return 0 <= x && 0 <= y && x <= w && y <= h
    }
}