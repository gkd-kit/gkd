package li.gkd.app.snapshot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import li.gkd.app.data.ComplexSnapshot
import li.gkd.app.data.Snapshot
import li.gkd.app.db.DbSet
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ZipUtils
import li.gkd.app.util.appInfoMapFlow
import li.gkd.app.util.clearCache
import li.gkd.app.util.json
import li.gkd.app.util.keepNullJson
import li.gkd.app.util.sharedDir
import li.gkd.app.util.snapshotFolder
import li.gkd.app.util.webpLossyCompressFormat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

object SnapshotStore {
    private val mutationMutex = Mutex()
    private val fileLayout by lazy { SnapshotFileLayout(snapshotFolder) }

    fun snapshotFile(id: Long): File = fileLayout.committed(id).snapshotFile

    fun screenshotFile(id: Long): File = fileLayout.committed(id).screenshotFile

    suspend fun getMinSnapshot(id: Long): JsonObject = mutationMutex.withLock {
        val files = fileLayout.committed(id)
        val cachedText = withContext(Dispatchers.IO) {
            files.minSnapshotFile.takeIf { it.isFile && it.length() > 0 }?.readText()
        }
        if (cachedText != null) {
            val cachedSnapshot = withContext(Dispatchers.Default) {
                runCatching { json.decodeFromString<JsonObject>(cachedText) }.getOrNull()
            }
            if (cachedSnapshot != null) return@withLock cachedSnapshot
        }
        val text = withContext(Dispatchers.IO) { files.snapshotFile.readText() }
        val snapshotJson = withContext(Dispatchers.Default) {
            // #1185
            json.decodeFromString<JsonObject>(text)
        }
        val minSnapshot = JsonObject(snapshotJson.toMutableMap().apply {
            this["nodes"] = JsonArray(emptyList())
        })
        withContext(Dispatchers.IO) {
            files.minSnapshotFile.writeText(keepNullJson.encodeToString(minSnapshot))
        }
        minSnapshot
    }

    suspend fun delete(snapshot: Snapshot) {
        mutationMutex.withLock {
            withContext(Dispatchers.IO) {
                fileLayout.committed(snapshot.id).directory.deleteRecursivelyOrThrow()
                DbSet.snapshotDao.delete(snapshot)
            }
        }
    }

    suspend fun deleteAll() = mutationMutex.withLock {
        withContext(Dispatchers.IO) {
            snapshotFolder.listFiles()?.forEach { file ->
                file.deleteRecursivelyOrThrow()
            }
            DbSet.snapshotDao.deleteAll()
        }
    }

    suspend fun replaceScreenshot(snapshot: Snapshot, newBytes: ByteArray): Boolean =
        mutationMutex.withLock {
            withContext(Dispatchers.IO) {
                val files = fileLayout.committed(snapshot.id)
                val oldBitmap = BitmapFactory.decodeFile(files.screenshotFile.absolutePath)
                    ?: return@withContext false
                val newBitmap = BitmapFactory.decodeByteArray(newBytes, 0, newBytes.size)
                if (newBitmap == null) {
                    oldBitmap.recycle()
                    return@withContext false
                }
                val sameSize = oldBitmap.width == newBitmap.width &&
                    oldBitmap.height == newBitmap.height
                oldBitmap.recycle()
                if (!sameSize) {
                    newBitmap.recycle()
                    return@withContext false
                }
                val tempFile = files.directory.resolve(
                    ".${files.webpFile.name}.${System.nanoTime()}.tmp"
                )
                try {
                    FileOutputStream(tempFile).use { stream ->
                        if (!newBitmap.compress(webpLossyCompressFormat, 85, stream)) {
                            throw IOException("替换截图压缩失败")
                        }
                        stream.fd.sync()
                    }
                    Os.rename(tempFile.absolutePath, files.webpFile.absolutePath)
                    if (files.legacyPngFile.exists() && !files.legacyPngFile.delete()) {
                        LogUtils.d("无法删除旧快照截图", files.legacyPngFile.absolutePath)
                    }
                    if (snapshot.githubAssetId != null) {
                        DbSet.snapshotDao.deleteGithubAssetId(snapshot.id)
                    }
                    true
                } finally {
                    newBitmap.recycle()
                    tempFile.delete()
                }
            }
        }

    suspend fun createArchive(
        snapshotId: Long,
        appId: String? = null,
        activityId: String? = null,
    ): File =
        mutationMutex.withLock {
            withContext(Dispatchers.IO) {
                val filename = if (appId != null) {
                    val appName = appInfoMapFlow.value[appId]?.name
                        ?.filterNot { char -> char in "\\/:*?\"<>|" || char <= ' ' }
                    if (activityId != null) {
                        "${(appName ?: appId).take(20)}_${
                            activityId.split('.').last().take(40)
                        }-${snapshotId}.zip"
                    } else {
                        "${(appName ?: appId).take(20)}-${snapshotId}.zip"
                    }
                } else {
                    "${snapshotId}.zip"
                }
                require(File(filename).name == filename) { "无效压缩包名称" }
                clearCache()
                val outputDirectory = sharedDir.resolve(
                    "snapshot-$snapshotId-${UUID.randomUUID()}"
                )
                if (!outputDirectory.mkdirs()) {
                    throw IOException("无法创建快照压缩目录")
                }
                val outputFile = outputDirectory.resolve(filename)
                try {
                    val files = fileLayout.committed(snapshotId)
                    if (!files.hasCompleteFiles) {
                        throw IOException("快照文件不完整: $snapshotId")
                    }
                    if (!ZipUtils.zipFiles(
                            listOf(files.snapshotFile, files.screenshotFile),
                            outputFile,
                        )
                    ) {
                        throw IOException("快照压缩失败")
                    }
                    outputFile
                } catch (e: Throwable) {
                    if (!outputDirectory.deleteRecursively()) {
                        e.addSuppressed(IOException("无法清理快照压缩目录"))
                    }
                    throw e
                }
            }
        }

    suspend fun deleteArchive(file: File) = withContext(NonCancellable + Dispatchers.IO) {
        val directory = file.parentFile ?: return@withContext
        if (directory.parentFile != sharedDir || !directory.name.startsWith("snapshot-")) {
            return@withContext
        }
        if (directory.exists() && !directory.deleteRecursively()) {
            LogUtils.d("无法清理快照压缩目录", directory.absolutePath)
        }
    }

    suspend fun save(snapshot: ComplexSnapshot, bitmap: Bitmap): Unit = mutationMutex.withLock {
        withContext(Dispatchers.IO) {
            commitSnapshotDirectory(
                layout = fileLayout,
                id = snapshot.id,
                write = { files ->
                    files.webpFile.outputStream().use { stream ->
                        if (!bitmap.compress(webpLossyCompressFormat, 85, stream)) {
                            throw IOException("快照截图压缩失败")
                        }
                    }
                    files.snapshotFile.writeText(
                        keepNullJson.encodeToString(snapshot)
                    )
                    files.minSnapshotFile.writeText(
                        keepNullJson.encodeToString(snapshot.copy(nodes = emptyList()))
                    )
                },
                publish = {
                    DbSet.snapshotDao.insert(snapshot.toSnapshot())
                },
            )
        }
    }

    private fun File.deleteRecursivelyOrThrow() {
        if (exists() && !deleteRecursively()) {
            throw IOException("无法删除快照文件: $name")
        }
    }
}
