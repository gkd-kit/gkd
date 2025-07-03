package li.songe.gkd.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import li.songe.gkd.util.OnChangeListen
import li.songe.gkd.util.OnDestroy
import li.songe.gkd.util.OnTileClick
import li.songe.gkd.util.map
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.switchStoreEnableMatch
import li.songe.gkd.util.useLogLifecycle

class MatchTileService : TileService(), OnDestroy, OnChangeListen, OnTileClick {
    override fun onStartListening() {
        super.onStartListening()
        onStartListened()
    }

    override fun onClick() {
        super.onClick()
        onTileClicked()
    }

    override fun onStopListening() {
        super.onStopListening()
        onStopListened()
    }

    override fun onDestroy() {
        super.onDestroy()
        onDestroyed()
    }

    val scope = MainScope().also { scope ->
        onDestroyed { scope.cancel() }
    }

    private val listeningFlow = MutableStateFlow(false).also { listeningFlow ->
        onStartListened { listeningFlow.value = true }
        onStopListened { listeningFlow.value = false }
    }

    init {
        useLogLifecycle()
        scope.launch {
            combine(
                storeFlow.map(scope) { it.enableMatch },
                listeningFlow
            ) { v1, v2 -> v1 to v2 }.collect { (enableMatch, listening) ->
                if (listening) {
                    qsTile.state = if (enableMatch) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    qsTile.updateTile()
                }
            }
        }
        onTileClicked { switchStoreEnableMatch() }
    }
}