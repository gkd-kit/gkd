package li.gkd.app.storage

import java.io.File

enum class HistoryDirectory(val path: String) {
    Snapshot("snapshot"),
    MatchDiagnostics("matching-diagnostics"),
    Crash("crash"),
    Log("log"),
}

/** Hosts supply the files, cache and private files roots. */
class AppStorageLayout(
    private val files: File,
    private val cache: File,
    private val privateFiles: File,
    val awaitHistoryMigration: suspend (HistoryDirectory) -> Unit = {},
) {
    private fun directory(root: File, path: String) =
        root.resolve(path).apply {
            check(isDirectory || mkdirs()) { "Cannot create directory: $absolutePath" }
        }

    val db
        get() = directory(files, "db")

    val database
        get() = db.resolve("gkd.db")

    val store
        get() = directory(files, "store")

    val subscription
        get() = directory(files, "subscription")

    val snapshot
        get() = directory(files, HistoryDirectory.Snapshot.path)

    val log
        get() = directory(files, HistoryDirectory.Log.path)

    val matchingDiagnostics
        get() = directory(files, HistoryDirectory.MatchDiagnostics.path)

    val crash
        get() = directory(files, HistoryDirectory.Crash.path)

    val crashTemp
        get() = directory(files, "${HistoryDirectory.Crash.path}/temp")

    val privateStore
        get() = directory(privateFiles, "private-store")

    val startupLog
        get() = privateFiles.resolve("startup-log")

    val coilCache
        get() = directory(cache, "coil")

    val webViewCache
        get() = directory(cache, "webview")

    val sharedCache
        get() = directory(cache, "shared")

    val tempCache
        get() = directory(cache, "temp")
}
