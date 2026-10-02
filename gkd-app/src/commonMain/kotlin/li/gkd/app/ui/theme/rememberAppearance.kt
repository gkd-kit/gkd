package li.gkd.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.gkd.app.settings.SettingsRepository

@Composable
fun rememberAppearance(): androidx.compose.runtime.State<AppearanceState> {
    val scope = rememberCoroutineScope()
    val appearance = remember {
        SettingsRepository.settings
            .map { AppearanceState(it.enableDarkTheme, it.enableDynamicColor) }
            .distinctUntilChanged()
            .debounce(300)
            .stateIn(scope, SharingStarted.Eagerly, SettingsRepository.settings.value.let {
                AppearanceState(it.enableDarkTheme, it.enableDynamicColor)
            })
    }
    return appearance.collectAsStateWithLifecycle()
}
