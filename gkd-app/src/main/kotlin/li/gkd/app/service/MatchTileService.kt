package li.gkd.app.service

import li.gkd.app.store.storeFlow
import li.gkd.app.store.switchStoreEnableMatch
import li.gkd.app.util.mapState

class MatchTileService : BaseTileService() {
    override val activeFlow = storeFlow.mapState(scope) { it.enableMatch }

    init {
        onTileClicked { switchStoreEnableMatch() }
    }
}