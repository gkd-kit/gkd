package li.gkd.app.service

class ActivityTileService : BaseTileService() {
    override val activeFlow get() = ActivityService.isRunning

    override fun onTileClick() {
        if (ActivityService.isRunning.value) {
            ActivityService.stop()
        } else {
            ActivityService.start()
        }
    }
}
