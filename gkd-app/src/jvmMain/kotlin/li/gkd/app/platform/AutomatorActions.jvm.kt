package li.gkd.app.platform

import li.gkd.app.DesktopRuntime
import li.gkd.app.selectedSimulatedAutomator
import li.gkd.app.settings.SettingsRepository

actual fun requestAutomatorRestart(): PlatformResult<Unit> {
    if (SettingsRepository.settings.value.enableAutomator) {
        val runtime = DesktopRuntime.requireCurrent()
        val service = selectedSimulatedAutomator()
        runtime.services.setEnabled(service, false)
        runtime.services.setEnabled(service, true)
    }
    return PlatformResult.Success(Unit)
}
