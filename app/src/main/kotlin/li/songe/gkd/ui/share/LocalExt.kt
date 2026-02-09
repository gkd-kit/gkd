package li.songe.gkd.ui.share

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.songe.gkd.MainViewModel

val LocalMainViewModel = compositionLocalOf<MainViewModel> {
    error("not found MainViewModel")
}

val LocalDarkTheme = compositionLocalOf { false }

val LocalIsTalkbackEnabled = compositionLocalOf<StateFlow<Boolean>> {
    MutableStateFlow(false)
}
