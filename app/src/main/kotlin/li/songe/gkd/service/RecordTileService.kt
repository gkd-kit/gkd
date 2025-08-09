package li.songe.gkd.service

class RecordTileService : BaseTileService() {
    override val activeFlow = RecordService.isRunning

    init {
        onTileClicked {
            if (RecordService.isRunning.value) {
                RecordService.stop()
            } else {
                RecordService.start()
            }
        }
    }
}
