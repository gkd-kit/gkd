package li.gkd.app.ui.platform

import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.option.AutomatorModeOption

actual fun UiHost.setStatusServiceEnabled(enabled: Boolean) {
    state.unsupported()
}

actual fun UiHost.changeAutomatorMode(mode: AutomatorModeOption) {
    SettingsRepository.updateAutomatorMode(mode.value)
}
