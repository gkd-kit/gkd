package li.gkd.app.service

class ButtonTileService : BaseTileService() {
    override val activeFlow = ButtonService.isRunning

    init {
        onTileClicked {
            if (ButtonService.isRunning.value) {
                ButtonService.stop()
            } else {
                ButtonService.start()
            }
        }
    }
}