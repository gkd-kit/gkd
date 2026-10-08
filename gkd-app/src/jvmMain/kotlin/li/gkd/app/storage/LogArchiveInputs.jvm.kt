package li.gkd.app.storage

actual fun logArchiveInputs() = LogArchiveInputs(
    mapOf("desktop.txt" to { "GKD Desktop development host\n" + System.getProperty("os.name") }),
)
