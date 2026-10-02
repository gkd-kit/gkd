package li.gkd.app.platform

import li.gkd.app.service.fixRestartAutomatorService

actual fun requestAutomatorRestart(): PlatformResult<Unit> {
    fixRestartAutomatorService()
    return PlatformResult.Success(Unit)
}
