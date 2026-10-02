package li.gkd.app.ui.settings

import li.gkd.app.settings.SettingsStore
import li.gkd.app.ui.page.SettingsUiState

fun SettingsStore.toUiState(privilegeAvailable: Boolean, dynamicColorAvailable: Boolean) =
    SettingsUiState(
        toastWhenClick, useSystemToast, actionToast, excludeFromRecents, enableBlockA11yAppList,
        enableDarkTheme, enableDynamicColor, privilegeAvailable, dynamicColorAvailable,
    )
