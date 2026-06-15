package li.songe.gkd.util

import android.text.format.DateUtils
import androidx.annotation.WorkerThread
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
    val markFile = app.filesDir.resolve(".gkd")
    if (markFile.isFile) {
        app.filesDir
    } else {
        // fix #1333
        app.getExternalFilesDir(null) ?: app.filesDir.also {
            markFile.createNewFile()
        }
    }
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
val crashFolder: File
    get() = filesDir.resolve("crash").autoMk()
val crashTempFolder: File
    get() = filesDir.resolve("crash/temp").autoMk()

val privateStoreFolder: File
    get() = app.filesDir.resolve("private-store").autoMk()

private val cacheDir by lazy { app.externalCacheDir ?: app.cacheDir }
val coilCacheDir: File
    get() = cacheDir.resolve("coil").autoMk()
val sharedDir: File
    get() = cacheDir.resolve("shared").autoMk()
private val tempDir: File
    get() = cacheDir.resolve("temp").autoMk()

fun createGkdTempDir(): File {
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
    val tempDir = createGkdTempDir()
    val files = listOf(dbFolder, storeFolder, subsFolder, logFolder, crashFolder).filter {
        it.list()?.isNotEmpty() == true
    }.toMutableList()
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
        val p1 = allPermissionStates.filter { s -> s.value }
        if (p1.isNotEmpty()) {
            it.appendText("已授权\n" + p1.joinToString("\n") { s -> s.name })
            it.appendText("\n\n")
        }
        val p2 = allPermissionStates.filter { s -> !s.value }
        if (p2.isNotEmpty()) {
            it.appendText("未授权\n" + p2.joinToString("\n") { s -> s.name })
            it.appendText("\n\n")
        }
        if (appListAuthAbnormalFlow.value) {
            it.appendText("其它\n")
            it.appendText("读取应用列表权限异常")
        }
        it.appendText("\n")
        files.add(it)
    }
    val formattedJson = Json(from = json) {
        prettyPrint = true
    }
    tempDir.resolve("gkd-${META.versionCode}-v${META.versionName}.json").also {
        it.writeText(formattedJson.encodeToString(META))
        files.add(it)
    }
    val logZipFile = sharedDir.resolve("log-${System.currentTimeMillis()}.zip")
    ZipUtils.zipFiles(files, logZipFile)
    tempDir.deleteRecursively()
    return logZipFile
}
