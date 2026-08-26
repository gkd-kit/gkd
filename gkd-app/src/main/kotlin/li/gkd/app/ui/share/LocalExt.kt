package li.gkd.app.ui.share

import androidx.compose.runtime.staticCompositionLocalOf
import li.gkd.app.MainViewModel

val LocalMainViewModel = staticCompositionLocalOf<MainViewModel> {
    error("not found MainViewModel")
}

val LocalDarkTheme = staticCompositionLocalOf { false }

val LocalIsTalkbackEnabled = staticCompositionLocalOf {
    false
}
