package li.gkd.app.service

class HttpTileService : BaseTileService() {
    override val activeFlow get() = HttpService.isRunning

    override fun onTileClick() {
        if (HttpService.isRunning.value) {
            HttpService.stop()
        } else {
            HttpService.start()
        }
    }
}
