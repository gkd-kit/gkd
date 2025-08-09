package li.songe.gkd.service

import li.songe.gkd.store.storeFlow
import li.songe.gkd.store.switchStoreEnableMatch
import li.songe.gkd.util.map

class MatchTileService : BaseTileService() {
    override val activeFlow = storeFlow.map(scope) { it.enableMatch }

    init {
        onTileClicked { switchStoreEnableMatch() }
    }
}