package li.gkd.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import li.gkd.app.settings.SettingsRepository

@Composable
fun rememberAppearance(): androidx.compose.runtime.State<AppearanceState> {
    val appearance = remember {
        SettingsRepository.settings
            .map { AppearanceState(it.enableDarkTheme, it.enableDynamicColor) }
            .distinctUntilChanged()
    }
    return appearance.collectAsStateWithLifecycle(
        initialValue =
            SettingsRepository.settings.value.let {
                AppearanceState(it.enableDarkTheme, it.enableDynamicColor)
            },
    )
}
