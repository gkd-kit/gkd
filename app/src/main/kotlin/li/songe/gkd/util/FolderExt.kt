package li.songe.gkd.util

import android.text.format.DateUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ZipUtils
import li.songe.gkd.app
import java.io.File

private fun File.autoMk(): File {
    if (!exists()) {
        mkdirs()
    }
    return this
}

private val filesDir: File by lazy { app.getExternalFilesDir(null) ?: app.filesDir }
val dbFolder: File
    get() = filesDir.resolve("db").autoMk()
val subsFolder: File
    get() = filesDir.resolve("subscription").autoMk()
val snapshotFolder: File
    get() = filesDir.resolve("snapshot").autoMk()

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
    val files = mutableListOf(dbFolder, subsFolder)
    LogUtils.getLogFiles().firstOrNull()?.parentFile?.let { files.add(it) }
    tempDir.resolve("appList.json").also {
        it.writeText(json.encodeToString(appInfoCacheFlow.value.values.toList()))
        files.add(it)
    }
    tempDir.resolve("store.json").also {
        it.writeText(json.encodeToString(storeFlow.value))
        files.add(it)
    }
    tempDir.resolve("shizuku.json").also {
        it.writeText(json.encodeToString(shizukuStoreFlow.value))
        files.add(it)
    }
    val logZipFile = sharedDir.resolve("log-${System.currentTimeMillis()}.zip")
    ZipUtils.zipFiles(files, logZipFile)
    tempDir.deleteRecursively()
    return logZipFile
}
