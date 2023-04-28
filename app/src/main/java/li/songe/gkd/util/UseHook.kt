package li.songe.gkd.util

import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blankj.utilcode.util.ScreenUtils

object UseHook {
    var screenWidth by mutableStateOf(ScreenUtils.getAppScreenWidth())
    var screenHeight by mutableStateOf(ScreenUtils.getAppScreenHeight())
    var screenOrientationIsLandscape by mutableStateOf(ScreenUtils.isLandscape())
//    var locale by mutableStateOf(LanguageUtils.getSystemLanguage())

    fun update(newConfig: Configuration) {
        screenHeight = ScreenUtils.getAppScreenHeight()
        screenWidth = ScreenUtils.getAppScreenWidth()
        screenOrientationIsLandscape = ScreenUtils.isLandscape()
    }
}
