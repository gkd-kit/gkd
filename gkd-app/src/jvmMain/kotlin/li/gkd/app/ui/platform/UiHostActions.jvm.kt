package li.gkd.app.ui.platform

import li.gkd.app.settings.SettingsRepository
import li.gkd.app.DesktopRuntime
import li.gkd.app.SimulatedService
import li.gkd.app.ui.option.AutomatorModeOption

actual fun UiHost.setStatusServiceEnabled(enabled: Boolean) {
    DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Status, enabled)
}

actual fun UiHost.changeAutomatorMode(mode: AutomatorModeOption) {
    if (SettingsRepository.settings.value.automatorMode == mode.value) return
    val simulator = state.simulator.settings.value
    if (mode == AutomatorModeOption.AutomationMode && simulator.privilege.available && simulator.prompts.automationOccupied) {
        state.overlay = "occupied"
        return
    }
    SettingsRepository.updateAutomatorMode(mode.value)
    DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Accessibility, false)
    DesktopRuntime.requireCurrent().services.setEnabled(SimulatedService.Automation, false)
}
