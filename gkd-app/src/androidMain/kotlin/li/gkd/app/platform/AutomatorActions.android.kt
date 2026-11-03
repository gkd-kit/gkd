package li.gkd.app.platform

import li.gkd.app.service.CompanionServiceController
import li.gkd.app.service.fixRestartAutomatorService

actual fun requestAutomatorRestart(): PlatformResult<Unit> {
    fixRestartAutomatorService()
    return PlatformResult.Success(Unit)
}

actual fun requestCompanionServiceUpdate(): PlatformResult<Unit> {
    CompanionServiceController.update()
    return PlatformResult.Success(Unit)
}
