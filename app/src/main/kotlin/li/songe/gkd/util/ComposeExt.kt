package li.songe.gkd.util

import androidx.compose.runtime.compositionLocalOf
import com.dylanc.activityresult.launcher.PickContentLauncher
import com.dylanc.activityresult.launcher.StartActivityLauncher
import li.songe.gkd.MainViewModel


val LocalLauncher =
    compositionLocalOf<StartActivityLauncher> { error("not found StartActivityLauncher") }

val LocalPickContentLauncher =
    compositionLocalOf<PickContentLauncher> { error("not found LocalPickContentLauncher") }

val LocalMainViewModel = compositionLocalOf<MainViewModel> { error("not found LocalMainViewModel") }
