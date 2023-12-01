package li.songe.gkd.composition

interface CanOnStartCommand {
    fun onStartCommand(f: StartCommandHook): Boolean
}