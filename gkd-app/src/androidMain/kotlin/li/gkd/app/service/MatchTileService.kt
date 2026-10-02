package li.gkd.app.service

import kotlinx.coroutines.flow.map
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.store.AppStore.toggleEnableMatch

class MatchTileService : BaseTileService() {
    override val activeFlow = settings.map { it.enableMatch }

    override fun onTileClick() = toggleEnableMatch()
}
