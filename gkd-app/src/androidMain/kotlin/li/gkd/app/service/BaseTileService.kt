package li.gkd.app.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

abstract class BaseTileService : TileService() {
    protected abstract val activeFlow: Flow<Boolean>
    protected abstract fun onTileClick()

    private val serviceScope = MainScope()
    private var listeningJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val timestamp = System.currentTimeMillis()
        if (timestamp - lastA11yFixTime > 3_000L) {
            lastA11yFixTime = timestamp
            fixRestartAutomatorService()
        }
        listeningJob?.cancel()
        listeningJob = serviceScope.launch {
            activeFlow.collect { active ->
                qsTile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                qsTile.updateTile()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        StatusService.autoStart()
        onTileClick()
    }

    override fun onStopListening() {
        listeningJob?.cancel()
        listeningJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

private var lastA11yFixTime = 0L
