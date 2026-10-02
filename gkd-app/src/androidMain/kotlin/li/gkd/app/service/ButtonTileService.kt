package li.gkd.app.service

class ButtonTileService : BaseTileService() {
    override val activeFlow get() = ButtonService.isRunning

    override fun onTileClick() {
        if (ButtonService.isRunning.value) {
            ButtonService.stop()
        } else {
            ButtonService.start()
        }
    }
}
