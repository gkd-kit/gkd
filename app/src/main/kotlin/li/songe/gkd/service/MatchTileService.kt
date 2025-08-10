package li.songe.gkd.service

import li.songe.gkd.store.storeFlow
import li.songe.gkd.store.switchStoreEnableMatch
import li.songe.gkd.util.mapState

class MatchTileService : BaseTileService() {
    override val activeFlow = storeFlow.mapState(scope) { it.enableMatch }

    init {
        onTileClicked { switchStoreEnableMatch() }
    }
}