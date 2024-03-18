package li.songe.gkd.util

import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ZipUtils
import kotlinx.serialization.encodeToString
import li.songe.gkd.app
import java.io.File

private val filesDir by lazy {
    app.getExternalFilesDir(null) ?: app.filesDir
}
val dbFolder by lazy { filesDir.resolve("db") }
val subsFolder by lazy { filesDir.resolve("subscription") }
val snapshotFolder by lazy { filesDir.resolve("snapshot") }

private val cacheDir by lazy {
    app.externalCacheDir ?: app.cacheDir
}
val snapshotZipDir by lazy { cacheDir.resolve("snapshotZip") }
val newVersionApkDir by lazy { cacheDir.resolve("newVersionApk") }
val logZipDir by lazy { cacheDir.resolve("logZip") }
val imageCacheDir by lazy { cacheDir.resolve("imageCache") }

fun initFolder() {
    listOf(
        dbFolder,
        subsFolder,
        snapshotFolder,
        snapshotZipDir,
        newVersionApkDir,
        logZipDir,
        imageCacheDir
    ).forEach { f ->
        if (!f.exists()) {
            // TODO 在某些机型上无法创建目录 用户反馈重启手机后解决 是否存在其它解决方式?
            f.mkdirs()
        }
    }
}


fun buildLogFile(): File {
    val files = mutableListOf(dbFolder, subsFolder)
    LogUtils.getLogFiles().firstOrNull()?.parentFile?.let { files.add(it) }
    val appListFile = logZipDir
        .resolve("appList.json")
    appListFile.writeText(json.encodeToString(appInfoCacheFlow.value.values.toList()))
    files.add(appListFile)
    val logZipFile = logZipDir.resolve("log-${System.currentTimeMillis()}.zip")
    ZipUtils.zipFiles(files, logZipFile)
    return logZipFile
}