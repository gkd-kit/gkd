package li.songe.gkd.util

import android.text.format.DateUtils
import com.blankj.utilcode.util.LogUtils
import li.songe.gkd.app
import java.io.File

fun File.autoMk(): File {
    if (!exists()) {
        mkdirs()
    }
    return this
}

private val filesDir: File by lazy {
    app.getExternalFilesDir(null) ?: error("failed getExternalFilesDir")
}

val dbFolder: File
    get() = filesDir.resolve("db").autoMk()
val storeFolder: File
    get() = filesDir.resolve("store").autoMk()
val subsFolder: File
    get() = filesDir.resolve("subscription").autoMk()
val snapshotFolder: File
    get() = filesDir.resolve("snapshot").autoMk()

val privateStoreFolder: File
    get() = app.filesDir.resolve("store").autoMk()

private val cacheDir by lazy { app.externalCacheDir ?: app.cacheDir }
val coilCacheDir: File
    get() = cacheDir.resolve("coil").autoMk()
val sharedDir: File
    get() = cacheDir.resolve("shared").autoMk()
private val tempDir: File
    get() = cacheDir.resolve("temp").autoMk()

fun createTempDir(): File {
    return tempDir
        .resolve(System.currentTimeMillis().toString())
        .apply { mkdirs() }
}

private fun removeExpired(dir: File) {
    dir.listFiles()?.forEach { f ->
        if (System.currentTimeMillis() - f.lastModified() > DateUtils.HOUR_IN_MILLIS) {
            if (f.isDirectory) {
                f.deleteRecursively()
            } else if (f.isFile) {
                f.delete()
            }
        }
    }
}

fun clearCache() {
    removeExpired(sharedDir)
    removeExpired(tempDir)
}

fun buildLogFile(): File {
    val tempDir = createTempDir()
    val files = mutableListOf(dbFolder, storeFolder, subsFolder)
    LogUtils.getLogFiles().firstOrNull()?.parentFile?.let { files.add(it) }
    tempDir.resolve("appList.json").also {
        it.writeText(json.encodeToString(appInfoMapFlow.value.values.toList()))
        files.add(it)
    }
    val logZipFile = sharedDir.resolve("log-${System.currentTimeMillis()}.zip")
    ZipUtils.zipFiles(files, logZipFile)
    tempDir.deleteRecursively()
    return logZipFile
}
