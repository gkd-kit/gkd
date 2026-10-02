package li.gkd.app.snapshot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.snapshot.platform.prepareSnapshotReplacement
import li.gkd.app.storage.ExportFileNames
import li.gkd.app.storage.StorageException
import li.gkd.app.storage.StorageIssue
import li.gkd.app.storage.ZipArchive
import li.gkd.app.storage.appStorage
import li.gkd.app.util.LogUtils
import li.gkd.db.Db
import li.gkd.db.Snapshot
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

data class SnapshotUploadArchive(
    val file: File,
    val screenshotModifiedAt: Long,
)

object SnapshotStore {
    private val sharedDirectory get() = appStorage().sharedCache

    private val json = Json {
        ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true
    }
    private val keepNullJson = Json(json) { explicitNulls = true }
    private val mutationMutex = Mutex()
    private val fileLayout by lazy { SnapshotFileLayout(appStorage().snapshot) }

    suspend fun save(snapshot: ComplexSnapshot, screenshot: ByteArray) =
        save(snapshot.id, write = { files ->
            files.webpFile.writeBytes(screenshot)
            files.snapshotFile.writeText(keepNullJson.encodeToString(snapshot))
            files.minSnapshotFile.writeText(keepNullJson.encodeToString(snapshot.copy(nodes = emptyList())))
        }, publish = { Db.snapshotDao.insert(snapshot.toSnapshot()) })

    fun snapshotFile(id: Long): File = fileLayout.committed(id).snapshotFile

    fun screenshotFile(id: Long): File = fileLayout.committed(id).screenshotFile


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
            Db.snapshotDao.markUploadedIfPending(snapshotId, githubAssetId) > 0
        }
    }

    suspend fun getMinSnapshot(id: Long): JsonObject = mutationMutex.withLock {
        val files = fileLayout.committed(id)
        val cachedText = withContext(Dispatchers.IO) {
            files.minSnapshotFile.takeIf { it.isFile && it.length() > 0 }?.readText()
        }
        if (cachedText != null) {
            val cachedSnapshot = withContext(Dispatchers.Default) {
                try {
                    json.decodeFromString<JsonObject>(cachedText)
                } catch (_: SerializationException) {
                    null
                }
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
                    Db.snapshotDao.delete(snapshot)
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
                val temporary =
                    Files.createTempFile(files.directory.toPath(), "replace-", ".tmp")
                        .toFile()
                try {
                    if (!prepareSnapshotReplacement(
                            files.screenshotFile,
                            newBytes,
                            temporary
                        )
                    ) return@withContext false
                    currentCoroutineContext().ensureActive()
                    withContext(NonCancellable) {
                        val previous = stageReplacement(files.webpFile)
                        try {
                            Files.move(
                                temporary.toPath(),
                                files.webpFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                            )
                            Db.snapshotDao.deleteGithubAssetId(snapshot.id)
                        } catch (e: Throwable) {
                            files.webpFile.delete()
                            restoreReplacement(files.webpFile, previous, e)
                            throw e
                        }
                        finishReplacement(previous)
                        if (files.legacyPngFile.exists() && !files.legacyPngFile.delete()) LogUtils.d(
                            "Cannot delete legacy screenshot",
                            files.legacyPngFile.absolutePath
                        )
                    }
                    true
                } finally {
                    temporary.delete()
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
                val appName = AppInfoRepository.snapshot?.apps?.get(appId)?.name
                    ?.filterNot { char -> char in "\\/:*?\"<>|" || char <= ' ' }
                val stem = if (activityId != null) {
                    "${(appName ?: appId).take(20)}_${
                        activityId.split('.').last().take(40)
                    }-${ExportFileNames.timestamp(snapshotId)}"
                } else {
                    "${(appName ?: appId).take(20)}-${
                        ExportFileNames.timestamp(
                            snapshotId
                        )
                    }"
                }
                "$stem.zip"
            } else {
                "${snapshotId}.zip"
            }
            if (!(File(filename).name == filename)) {
                throw StorageException(StorageIssue.archive_name_invalid)
            }
            val outputDirectory = sharedDirectory.resolve(
                "snapshot-$snapshotId-${UUID.randomUUID()}"
            )
            if (!outputDirectory.mkdirs()) {
                throw StorageException(StorageIssue.snapshot_archive_directory_create_failed)
            }
            val outputFile = outputDirectory.resolve(filename)
            try {
                val files = fileLayout.committed(snapshotId)
                if (!files.hasCompleteFiles) {
                    throw StorageException(
                        StorageIssue.snapshot_files_incomplete,
                        snapshotId
                    )
                }
                if (!ZipArchive.zipFiles(
                        listOf(files.snapshotFile, files.screenshotFile),
                        outputFile,
                    )
                ) {
                    throw StorageException(StorageIssue.snapshot_compress_failed)
                }
                outputFile
            } catch (e: Throwable) {
                if (!outputDirectory.deleteRecursively()) {
                    e.addSuppressed(StorageException(StorageIssue.snapshot_archive_directory_cleanup_failed))
                }
                throw e
            }
        }

    suspend fun deleteArchive(file: File) = withContext(NonCancellable + Dispatchers.IO) {
        val directory = file.parentFile ?: return@withContext
        if (directory.parentFile != sharedDirectory || !directory.name.startsWith("snapshot-")) {
            return@withContext
        }
        if (directory.exists() && !directory.deleteRecursively()) {
            LogUtils.d("无法清理快照压缩目录", directory.absolutePath)
        }
    }

    suspend fun save(
        id: Long,
        write: (SnapshotFileLayout.Files) -> Unit,
        publish: suspend () -> Unit
    ): Unit = mutationMutex.withLock {
        withContext(Dispatchers.IO) {
            commitSnapshotDirectory(
                fileLayout,
                id,
                write,
                publish
            )
        }
    }

    private fun stageDeletion(target: File): File? {
        if (!target.exists()) return null
        val staged = requireNotNull(target.parentFile)
            .resolve(".${target.name}.delete-${UUID.randomUUID()}")
        if (!target.renameTo(staged)) {
            throw StorageException(
                StorageIssue.directory_stage_delete_failed,
                target.name
            )
        }
        return staged
    }

    private fun rollbackDeletion(target: File, staged: File?, cause: Throwable) {
        if (staged == null) return
        if (target.exists() && !target.deleteRecursively()) {
            cause.addSuppressed(
                StorageException(
                    StorageIssue.directory_rollback_cleanup_failed,
                    target.name
                )
            )
            return
        }
        if (!staged.renameTo(target)) {
            cause.addSuppressed(
                StorageException(
                    StorageIssue.snapshot_directory_restore_failed,
                    target.name
                )
            )
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
            throw StorageException(StorageIssue.screenshot_stage_old_failed)
        }
        return staged
    }

    private fun restoreReplacement(target: File, staged: File?, cause: Throwable) {
        if (staged != null && !staged.renameTo(target)) {
            cause.addSuppressed(StorageException(StorageIssue.screenshot_restore_old_failed))
        }
    }

    private fun finishReplacement(staged: File?) {
        if (staged != null && staged.exists() && !staged.delete()) {
            LogUtils.d("无法清理旧快照截图", staged.absolutePath)
        }
    }

}
