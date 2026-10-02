package li.gkd.app.storage

import java.io.File

/** Android-compatible relative paths. Hosts supply external files/cache and private files roots. */
class AppStorageLayout(
    private val files: File,
    private val cache: File,
    private val privateFiles: File,
) {
    private fun directory(root: File, path: String) = root.resolve(path).apply {
        check(isDirectory || mkdirs()) { "Cannot create directory: $absolutePath" }
    }

    val db get() = directory(files, "db")
    val database get() = db.resolve("gkd.db")
    val sh get() = directory(files, "sh")
    val store get() = directory(files, "store")
    val subscription get() = directory(files, "subscription")
    val snapshot get() = directory(files, "snapshot")
    val log get() = directory(files, "log")
    val crash get() = directory(files, "crash")
    val crashTemp get() = directory(files, "crash/temp")
    val privateStore get() = directory(privateFiles, "private-store")
    val coilCache get() = directory(cache, "coil")
    val webViewCache get() = directory(cache, "webview")
    val sharedCache get() = directory(cache, "shared")
    val tempCache get() = directory(cache, "temp")
}
