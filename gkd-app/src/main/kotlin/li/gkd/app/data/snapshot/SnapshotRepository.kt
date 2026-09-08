package li.gkd.app.data.snapshot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import li.gkd.app.data.ComplexSnapshot
import li.gkd.app.snapshot.SnapshotFileLayout
import li.gkd.app.snapshot.commitSnapshotDirectory
import li.gkd.db.Snapshot
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ZipUtils
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.app.util.FolderUtils
import li.gkd.app.util.json
import li.gkd.app.util.keepNullJson
import li.gkd.app.util.webpLossyCompressFormat
import li.gkd.db.Db
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

object SnapshotRepository : SnapshotStore(
    snapshotDao = Db.snapshotDao,
    snapshotRoot = FolderUtils.snapshotFolder,
)

open class SnapshotStore(
    private val snapshotDao: Snapshot.SnapshotDao,
    snapshotRoot: File,
) {
    private val mutationMutex = Mutex()
    private val fileLayout = SnapshotFileLayout(snapshotRoot)

    fun snapshotFile(id: Long): File = fileLayout.committed(id).snapshotFile

    fun screenshotFile(id: Long): File = fileLayout.committed(id).screenshotFile

    fun snapshots(): Flow<List<Snapshot>> = snapshotDao.query()

    suspend fun markUploaded(snapshot: Snapshot, githubAssetId: Int) =
        withContext(Dispatchers.IO) {
            snapshotDao.update(snapshot.copy(githubAssetId = githubAssetId))
        }

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

    suspend fun delete(snapshot: Snapshot) = delete(listOf(snapshot))

    suspend fun delete(snapshots: Collection<Snapshot>) {
        if (snapshots.isEmpty()) return
        mutationMutex.withLock {
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable + Dispatchers.IO) {
                val targets = snapshots.map { snapshot ->
                    snapshot to fileLayout.committed(snapshot.id).directory
                }
                val stagedList = ArrayList<Pair<File, File?>>(targets.size)
                try {
                    for ((_, directory) in targets) {
                        stagedList.add(directory to stageDeletion(directory))
                    }
                } catch (e: Throwable) {
                    for ((directory, staged) in stagedList) {
                        rollbackDeletion(directory, staged, e)
                    }
                    throw e
                }
                try {
                    snapshotDao.delete(*snapshots.toTypedArray())
                } catch (e: Throwable) {
                    for ((directory, staged) in stagedList) {
                        rollbackDeletion(directory, staged, e)
                    }
                    throw e
                }
                for ((_, staged) in stagedList) {
                    finishDeletion(staged)
                }
            }
        }
    }

    suspend fun deleteAll() = mutationMutex.withLock {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable + Dispatchers.IO) {
            val snapshotRoot = fileLayout.rootDirectory
            val staged = stageDeletion(snapshotRoot)
            if (!snapshotRoot.mkdirs()) {
                val error = IOException("无法重建快照目录")
                rollbackDeletion(snapshotRoot, staged, error)
                throw error
            }
            try {
                snapshotDao.deleteAll()
            } catch (e: Throwable) {
                rollbackDeletion(snapshotRoot, staged, e)
                throw e
            }
            finishDeletion(staged)
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
                    currentCoroutineContext().ensureActive()
                    withContext(NonCancellable) {
                        val previousWebp = stageReplacement(files.webpFile)
                        try {
                            Os.rename(tempFile.absolutePath, files.webpFile.absolutePath)
                            if (snapshot.githubAssetId != null) {
                                snapshotDao.deleteGithubAssetId(snapshot.id)
                            }
                        } catch (e: Throwable) {
                            files.webpFile.delete()
                            restoreReplacement(files.webpFile, previousWebp, e)
                            throw e
                        }
                        finishReplacement(previousWebp)
                        if (files.legacyPngFile.exists() && !files.legacyPngFile.delete()) {
                            LogUtils.d("无法删除旧快照截图", files.legacyPngFile.absolutePath)
                        }
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
                    val appName = AppInfoRepository.appInfoMapFlow.value[appId]?.name
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
                FolderUtils.clearCache()
                val outputDirectory = FolderUtils.sharedDir.resolve(
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
        if (directory.parentFile != FolderUtils.sharedDir || !directory.name.startsWith("snapshot-")) {
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
                    snapshotDao.insert(snapshot.toSnapshot())
                },
            )
        }
    }

    private fun stageDeletion(target: File): File? {
        if (!target.exists()) return null
        val staged = requireNotNull(target.parentFile)
            .resolve(".${target.name}.delete-${UUID.randomUUID()}")
        if (!target.renameTo(staged)) {
            throw IOException("无法暂存待删除目录: ${target.name}")
        }
        return staged
    }

    private fun rollbackDeletion(target: File, staged: File?, cause: Throwable) {
        if (staged == null) return
        if (target.exists() && !target.deleteRecursively()) {
            cause.addSuppressed(IOException("无法清理回滚目标: ${target.name}"))
            return
        }
        if (!staged.renameTo(target)) {
            cause.addSuppressed(IOException("无法恢复快照目录: ${target.name}"))
        }
    }

    private fun finishDeletion(staged: File?) {
        if (staged != null && staged.exists() && !staged.deleteRecursively()) {
            LogUtils.d("无法清理已删除快照目录", staged.absolutePath)
        }
    }

    private fun stageReplacement(target: File): File? {
        if (!target.exists()) return null
        val staged = requireNotNull(target.parentFile)
            .resolve(".${target.name}.replace-${UUID.randomUUID()}")
        if (!target.renameTo(staged)) {
            throw IOException("无法暂存旧快照截图")
        }
        return staged
    }

    private fun restoreReplacement(target: File, staged: File?, cause: Throwable) {
        if (staged != null && !staged.renameTo(target)) {
            cause.addSuppressed(IOException("无法恢复旧快照截图"))
        }
    }

    private fun finishReplacement(staged: File?) {
        if (staged != null && staged.exists() && !staged.delete()) {
            LogUtils.d("无法清理旧快照截图", staged.absolutePath)
        }
    }

}
