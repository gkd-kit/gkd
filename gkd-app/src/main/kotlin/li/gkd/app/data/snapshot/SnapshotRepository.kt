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
import li.gkd.app.text.UiStrings
import li.gkd.app.data.ComplexSnapshot
import li.gkd.app.snapshot.SnapshotFileLayout
import li.gkd.app.snapshot.commitSnapshotDirectory
import li.gkd.db.Snapshot
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ZipUtils
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.app.util.FolderUtils
import li.gkd.app.util.ExportFileNames
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

data class SnapshotUploadArchive(
    val file: File,
    val screenshotModifiedAt: Long,
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

    suspend fun markUploaded(
        snapshotId: Long,
        githubAssetId: Int,
        screenshotModifiedAt: Long,
    ): Boolean = mutationMutex.withLock {
        withContext(Dispatchers.IO) {
            val screenshot = fileLayout.committed(snapshotId).screenshotFile
            if (!screenshot.isFile || screenshot.lastModified() != screenshotModifiedAt) {
                return@withContext false
            }
            snapshotDao.markUploadedIfPending(snapshotId, githubAssetId) > 0
        }
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

    suspend fun delete(snapshot: Snapshot) {
        mutationMutex.withLock {
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable + Dispatchers.IO) {
                val directory = fileLayout.committed(snapshot.id).directory
                val staged = stageDeletion(directory)
                try {
                    snapshotDao.delete(snapshot)
                } catch (e: Throwable) {
                    rollbackDeletion(directory, staged, e)
                    throw e
                }
                finishDeletion(staged)
            }
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
                            throw IOException(UiStrings.screenshot_replacement_compress_failed)
                        }
                        stream.fd.sync()
                    }
                    currentCoroutineContext().ensureActive()
                    withContext(NonCancellable) {
                        val previousWebp = stageReplacement(files.webpFile)
                        try {
                            Os.rename(tempFile.absolutePath, files.webpFile.absolutePath)
                            snapshotDao.deleteGithubAssetId(snapshot.id)
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

    suspend fun createUploadArchive(snapshotId: Long): SnapshotUploadArchive =
        mutationMutex.withLock {
            val screenshotModifiedAt = withContext(Dispatchers.IO) {
                fileLayout.committed(snapshotId).screenshotFile.lastModified()
            }
            SnapshotUploadArchive(
                file = createArchiveLocked(snapshotId),
                screenshotModifiedAt = screenshotModifiedAt,
            )
        }

    suspend fun createArchive(
        snapshotId: Long,
        appId: String? = null,
        activityId: String? = null,
    ): File =
        mutationMutex.withLock {
            createArchiveLocked(snapshotId, appId, activityId)
        }

    private suspend fun createArchiveLocked(
        snapshotId: Long,
        appId: String? = null,
        activityId: String? = null,
    ): File =
        withContext(Dispatchers.IO) {
            val filename = if (appId != null) {
                val appName = AppInfoRepository.appInfoMapFlow.value[appId]?.name
                    ?.filterNot { char -> char in "\\/:*?\"<>|" || char <= ' ' }
                val stem = if (activityId != null) {
                    "${(appName ?: appId).take(20)}_${
                        activityId.split('.').last().take(40)
                    }-${ExportFileNames.timestamp(snapshotId)}"
                } else {
                    "${(appName ?: appId).take(20)}-${ExportFileNames.timestamp(snapshotId)}"
                }
                ExportFileNames.availableName(stem, "zip") { name ->
                    FolderUtils.sharedDir.listFiles().orEmpty().any { directory ->
                        directory.isDirectory && directory.name.startsWith("snapshot-") &&
                            directory.resolve(name).exists()
                    }
                }
            } else {
                "${snapshotId}.zip"
            }
            require(File(filename).name == filename) { UiStrings.archive_name_invalid }
            val outputDirectory = FolderUtils.sharedDir.resolve(
                "snapshot-$snapshotId-${UUID.randomUUID()}"
            )
            if (!outputDirectory.mkdirs()) {
                throw IOException(UiStrings.snapshot_archive_directory_create_failed)
            }
            val outputFile = outputDirectory.resolve(filename)
            try {
                val files = fileLayout.committed(snapshotId)
                if (!files.hasCompleteFiles) {
                    throw IOException(UiStrings.snapshot_files_incomplete(snapshotId))
                }
                if (!ZipUtils.zipFiles(
                        listOf(files.snapshotFile, files.screenshotFile),
                        outputFile,
                    )
                ) {
                    throw IOException(UiStrings.snapshot_compress_failed)
                }
                outputFile
            } catch (e: Throwable) {
                if (!outputDirectory.deleteRecursively()) {
                    e.addSuppressed(IOException(UiStrings.snapshot_archive_directory_cleanup_failed))
                }
                throw e
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
                            throw IOException(UiStrings.snapshot_screenshot_compress_failed)
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
            throw IOException(UiStrings.directory_stage_delete_failed(target.name))
        }
        return staged
    }

    private fun rollbackDeletion(target: File, staged: File?, cause: Throwable) {
        if (staged == null) return
        if (target.exists() && !target.deleteRecursively()) {
            cause.addSuppressed(IOException(UiStrings.directory_rollback_cleanup_failed(target.name)))
            return
        }
        if (!staged.renameTo(target)) {
            cause.addSuppressed(IOException(UiStrings.snapshot_directory_restore_failed(target.name)))
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
            throw IOException(UiStrings.screenshot_stage_old_failed)
        }
        return staged
    }

    private fun restoreReplacement(target: File, staged: File?, cause: Throwable) {
        if (staged != null && !staged.renameTo(target)) {
            cause.addSuppressed(IOException(UiStrings.screenshot_restore_old_failed))
        }
    }

    private fun finishReplacement(staged: File?) {
        if (staged != null && staged.exists() && !staged.delete()) {
            LogUtils.d("无法清理旧快照截图", staged.absolutePath)
        }
    }

}
