package li.gkd.app.service

import kotlinx.coroutines.flow.combine
import li.gkd.app.priv.uiAutomationFlow

class GkdTileService : BaseTileService() {
    override val activeFlow =
        combine(A11yService.isRunning, uiAutomationFlow) { a11y, automator ->
            a11y || automator != null
        }

    override fun onTileClick() = switchAutomatorService()
}
