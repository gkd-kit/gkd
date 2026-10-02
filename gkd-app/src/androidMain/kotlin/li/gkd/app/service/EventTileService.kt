package li.gkd.app.service

class EventTileService : BaseTileService() {
    override val activeFlow get() = EventService.isRunning

    override fun onTileClick() {
        if (EventService.isRunning.value) {
            EventService.stop()
        } else {
            EventService.start()
        }
    }
}
