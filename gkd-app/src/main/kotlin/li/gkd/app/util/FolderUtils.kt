package li.gkd.app.util

import android.text.format.DateUtils
import androidx.annotation.WorkerThread
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import li.gkd.app.META
import li.gkd.app.app
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.app.data.AppInfo
import li.gkd.app.data.UserInfo
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.currentUserId
import java.io.File

object FolderUtils {
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
        dir.listFiles()?.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > DateUtils.HOUR_IN_MILLIS) {
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else if (file.isFile) {
                    file.delete()
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
        val apps: List<AppInfo> = AppInfoRepository.userAppInfoMapFlow.value.values.toList(),
        val otherUsers: List<UserInfo> = AppInfoRepository.otherUserMapFlow.value.values.toList(),
        val othersApps: List<AppInfo> = AppInfoRepository.otherUserAppInfoMapFlow.value.values.toList(),
    )

    @WorkerThread
    fun buildLogFile(): File {
        val tempDir = createGkdTempDir()
        val files = listOf(dbFolder, storeFolder, subsFolder, logFolder, crashFolder).filter {
            it.list()?.isNotEmpty() == true
        }.toMutableList()
        tempDir.resolve("source-paths.txt").also { file ->
            app.assets.open(file.name).use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            files.add(file)
        }
        tempDir.resolve("apps.json").also {
            it.writeText(json.encodeToString(AppJsonData()))
            files.add(it)
        }
        tempDir.resolve("permission.txt").also {
            val grantedPermissions = PermissionStates.all.filter { state -> state.value }
            if (grantedPermissions.isNotEmpty()) {
                it.appendText("Authorized\n" + grantedPermissions.joinToString("\n") { state -> state.name })
                it.appendText("\n\n")
            }
            val deniedPermissions = PermissionStates.all.filter { state -> !state.value }
            if (deniedPermissions.isNotEmpty()) {
                it.appendText("Unauthorized\n" + deniedPermissions.joinToString("\n") { state -> state.name })
                it.appendText("\n\n")
            }
            if (AppInfoRepository.appListAuthAbnormalFlow.value) {
                it.appendText("Others\n")
                it.appendText("Exception reading app list permissions")
            }
            it.appendText("\n")
            files.add(it)
        }
        val formattedJson = Json(from = json) {
            prettyPrint = true
        }
        tempDir.resolve("gkd.json").also {
            it.writeText(formattedJson.encodeToString(META))
            files.add(it)
        }
        val logZipFile = ExportFileNames.reserve(
            sharedDir,
            "log-${ExportFileNames.timestamp(System.currentTimeMillis())}",
            "zip",
        )
        try {
            ZipUtils.zipFiles(files, logZipFile)
            return logZipFile
        } catch (e: Throwable) {
            logZipFile.delete()
            throw e
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
