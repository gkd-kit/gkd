package li.gkd.app.service

/** Keeps the component identity of tiles already added to Android Quick Settings. */
class HttpTileService : BaseTileService() {
    override val activeFlow
        get() = DebugService.isRunning

    override fun onTileClick() {
        if (DebugService.isRunning.value) {
            DebugService.stop()
        } else {
            DebugService.start()
        }
    }
}
