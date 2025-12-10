package li.songe.gkd.util

import android.text.format.DateUtils
import androidx.annotation.WorkerThread
import kotlinx.serialization.Serializable
import li.songe.gkd.META
import li.songe.gkd.app
import li.songe.gkd.data.AppInfo
import li.songe.gkd.data.UserInfo
import li.songe.gkd.data.otherUserMapFlow
import li.songe.gkd.permission.allPermissionStates
import li.songe.gkd.shizuku.currentUserId
import li.songe.gkd.shizuku.shizukuContextFlow
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
val shFolder: File
    get() = filesDir.resolve("sh").autoMk()
val storeFolder: File
    get() = filesDir.resolve("store").autoMk()
val subsFolder: File
    get() = filesDir.resolve("subscription").autoMk()
val snapshotFolder: File
    get() = filesDir.resolve("snapshot").autoMk()
val logFolder: File
    get() = filesDir.resolve("log").autoMk()

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

@Serializable
private data class AppJsonData(
    val userId: Int = currentUserId,
    val apps: List<AppInfo> = userAppInfoMapFlow.value.values.toList(),
    val otherUsers: List<UserInfo> = otherUserMapFlow.value.values.toList(),
    val othersApps: List<AppInfo> = otherUserAppInfoMapFlow.value.values.toList(),
)

@WorkerThread
fun buildLogFile(): File {
    val tempDir = createTempDir()
    val files = mutableListOf(dbFolder, storeFolder, subsFolder, logFolder)
    tempDir.resolve("meta.json").also {
        it.writeText(toJson5String(META))
        files.add(it)
    }
    tempDir.resolve("apps.json").also {
        it.writeText(json.encodeToString(AppJsonData()))
        files.add(it)
    }
    tempDir.resolve("shizuku.txt").also {
        it.writeText(shizukuContextFlow.value.states.joinToString("\n") { state ->
            state.first + ": " + state.second.toString()
        })
        files.add(it)
    }
    tempDir.resolve("permission.txt").also {
        it.writeText(allPermissionStates.joinToString("\n") { state ->
            state.name + ": " + state.stateFlow.value.toString()
        })
        it.appendText("\nappListAuthAbnormalFlow: ${appListAuthAbnormalFlow.value}")
        files.add(it)
    }
    tempDir.resolve("gkd-${META.versionCode}-v${META.versionName}.json").also {
        it.writeText(json.encodeToString(META))
        files.add(it)
    }
    val logZipFile = sharedDir.resolve("log-${System.currentTimeMillis()}.zip")
    ZipUtils.zipFiles(files, logZipFile)
    tempDir.deleteRecursively()
    return logZipFile
}
