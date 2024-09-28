package li.songe.gkd.debug

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HttpTileService : TileService() {
    val scope = MainScope()
    private val listeningFlow = MutableStateFlow(false)
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            combine(
                HttpService.isRunning,
                listeningFlow
            ) { v1, v2 -> v1 to v2 }.collect { (httpRunning, listening) ->
                if (listening) {
                    qsTile.state = if (httpRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    qsTile.updateTile()
                }
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        listeningFlow.value = true
    }

    override fun onStopListening() {
        super.onStopListening()
        listeningFlow.value = false
    }

    override fun onClick() {
        super.onClick()
        if (HttpService.isRunning.value) {
            HttpService.stop()
        } else {
            HttpService.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}