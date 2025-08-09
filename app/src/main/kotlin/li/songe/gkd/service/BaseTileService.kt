package li.songe.gkd.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import li.songe.gkd.util.OnCreateToDestroy
import li.songe.gkd.util.OnTileLife

abstract class BaseTileService : TileService(), OnCreateToDestroy, OnTileLife {
    override fun onCreate() = onCreated()
    override fun onStartListening() = onStartListened()
    override fun onClick() = onTileClicked()
    override fun onStopListening() = onStopListened()
    override fun onDestroy() = onDestroyed()

    abstract val activeFlow: StateFlow<Boolean>

    val scope = useScope()
    val listeningFlow = MutableStateFlow(false).apply {
        onStartListened { value = true }
        onStopListened { value = false }
    }

    init {
        useLogLifecycle()
        scope.launch {
            combine(
                activeFlow,
                listeningFlow
            ) { v1, v2 -> v1 to v2 }.collect { (active, listening) ->
                if (listening) {
                    qsTile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    qsTile.updateTile()
                }
            }
        }
    }
}
