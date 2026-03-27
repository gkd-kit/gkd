package li.songe.gkd.ui.share

import androidx.compose.runtime.staticCompositionLocalOf
import li.songe.gkd.MainViewModel

val LocalMainViewModel = staticCompositionLocalOf<MainViewModel> {
    error("not found MainViewModel")
}

val LocalDarkTheme = staticCompositionLocalOf { false }

val LocalIsTalkbackEnabled = staticCompositionLocalOf {
    false
}
