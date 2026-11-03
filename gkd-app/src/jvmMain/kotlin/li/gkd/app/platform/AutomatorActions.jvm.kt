package li.gkd.app.platform

import li.gkd.app.DesktopRuntime

actual fun requestAutomatorRestart(): PlatformResult<Unit> {
    DesktopRuntime.requireCurrent().applyAutomatorSettings()
    return PlatformResult.Success(Unit)
}

actual fun requestCompanionServiceUpdate(): PlatformResult<Unit> {
    DesktopRuntime.requireCurrent().updateCompanionService()
    return PlatformResult.Success(Unit)
}
