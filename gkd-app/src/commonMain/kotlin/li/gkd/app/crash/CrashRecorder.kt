package li.gkd.app.crash

data class CrashMetadata(
    val device: String,
    val osCode: Int,
    val osName: String,
    val versionCode: Int,
    val versionName: String,
)

class CrashRecorder(
    private val metadata: CrashMetadata,
    private val save: (CrashData) -> Unit = FileCrashStorage::save,
) {
    fun record(thread: Thread, error: Throwable) {
        val now = System.currentTimeMillis()
        save(
            CrashData(
                now,
                now,
                metadata.device,
                metadata.osCode,
                metadata.osName,
                metadata.versionCode,
                metadata.versionName,
                error.javaClass.name,
                error.message,
                thread.name,
                error.stackTraceToString(),
            ),
        )
    }
}
