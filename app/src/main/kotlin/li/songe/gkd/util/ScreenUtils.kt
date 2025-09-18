package li.songe.gkd.util

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Point
import li.songe.gkd.app

@Suppress("DEPRECATION")
object ScreenUtils {
    fun getScreenWidth(): Int = Point().apply {
        app.windowManager.defaultDisplay.getRealSize(this)
    }.x

    fun getScreenHeight(): Int = Point().apply {
        app.windowManager.defaultDisplay.getRealSize(this)
    }.y

    fun getScreenDensityDpi(): Int = Resources.getSystem().displayMetrics.densityDpi

    fun isLandscape(): Boolean {
        return app.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    fun isScreenLock(): Boolean = app.keyguardManager.inKeyguardRestrictedInputMode()
}