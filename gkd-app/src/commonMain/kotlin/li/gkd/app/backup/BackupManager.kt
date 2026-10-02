package li.gkd.app.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.storage.ExportFileNames
import li.gkd.app.storage.FileSource
import li.gkd.app.storage.StorageException
import li.gkd.app.storage.StorageIssue
import li.gkd.app.storage.StorageMaintenance
import li.gkd.app.storage.ZipArchive
import li.gkd.app.storage.appStorage
import li.gkd.app.storage.openFileSource
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionJson
import li.gkd.app.subscription.SubscriptionPersistence
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.util.LogUtils
import li.gkd.db.Db
import li.gkd.db.SubscriptionConfigSnapshot
import li.gkd.db.SubscriptionConfigStore
import java.io.File
import java.io.InputStream

private data class PreparedBackup(
    val dbData: SubscriptionConfigSnapshot?,
    val storeEntries: Map<String, String>,
    val subscriptions: List<RawSubscription>,
)

object BackupManager {
    private val storage get() = appStorage()
    private val json get() = SubscriptionJson.json
    private val mutationMutex = Mutex()

    suspend fun exportData(): File = mutationMutex.withLock {
        withContext(Dispatchers.IO) {
            val tempDir = StorageMaintenance.temporaryDirectory(storage)
            try {
                tempDir.resolve("store").run {
                    mkdir()
                    SettingsRepository.exportBackupEntries().forEach { (filename, text) ->
                        resolve(filename).writeText(text)
                    }
                }
                tempDir.resolve("db.json").writeText(
                    BackupFormat.encode(
                        BackupDatabaseData.fromSnapshot(SubscriptionConfigStore.capture()),
                    ),
                )
                tempDir.resolve("subscription").run {
                    mkdir()
                    SubscriptionRepository.awaitSnapshot().subscriptions.values.forEach { subs ->
                        resolve("${subs.id}.json").writeText(json.encodeToString(subs))
                    }
                }
                val file = ExportFileNames.reserve(
                    storage.sharedCache,
                    "gkd-backup-${ExportFileNames.timestamp(System.currentTimeMillis())}",
                    "zip",
                )
                try {
                    if (!ZipArchive.zipFiles(tempDir.listFiles().orEmpty().toList(), file)) {
                        throw StorageException(StorageIssue.backup_compress_failed)
                    }
                    file
                } catch (e: Throwable) {
                    file.delete()
                    throw e
                }
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    suspend fun importData(source: FileSource): Int =
        importData { openFileSource(source) }

    suspend fun importData(open: () -> InputStream) = mutationMutex.withLock {
        withContext(Dispatchers.IO) {
            val tempDir = StorageMaintenance.temporaryDirectory(storage)
            try {
                val zipFile = tempDir.resolve("file.zip")
                val unzipDir = tempDir.resolve("unzip")
                try {
                    BackupArchiveReader.extract(open, zipFile, unzipDir)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SecurityException) {
                    LogUtils.d("importBackUpData.openFile", e)
                    throw StorageException(StorageIssue.backup_reselect_file, cause = e)
                } catch (e: Exception) {
                    LogUtils.d("importBackUpData.unzipFile", e)
                    throw StorageException(StorageIssue.backup_invalid_archive, cause = e)
                }
                zipFile.delete()

                val prepared = prepareBackup(unzipDir)
                applyPreparedBackup(prepared)
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    private suspend fun applyPreparedBackup(prepared: PreparedBackup): Int =
        SubscriptionRepository.withBackupTransaction(prepared.subscriptions) { subscriptions ->
            val previousFiles = subscriptions.associate { subscription ->
                subscription.id to SubscriptionRepository.files.readBytes(subscription.id)
            }
            SettingsRepository.withBackupRestore(prepared.storeEntries) {
                try {
                    Db.withTransaction {
                        val skipped =
                            prepared.dbData?.let { SubscriptionConfigStore.merge(it) } ?: 0
                        subscriptions.forEach { SubscriptionPersistence.save(it) }
                        skipped
                    }
                } catch (error: Throwable) {
                    previousFiles.forEach { (id, bytes) ->
                        runCatching { SubscriptionRepository.files.restore(id, bytes) }
                            .exceptionOrNull()?.let(error::addSuppressed)
                    }
                    throw error
                }
            }
        }

    private suspend fun prepareBackup(unzipDir: File): PreparedBackup =
        withContext(Dispatchers.IO) {
            val dbFile = unzipDir.resolve("db.json")
            val dbData = if (dbFile.exists() && dbFile.isFile) {
                val text = dbFile.readText()
                withContext(Dispatchers.Default) {
                    BackupFormat.decode(text).toSnapshot()
                }
            } else {
                null
            }
            val storeEntries = SettingsRepository.backupFilenames.mapNotNull { filename ->
                val file = unzipDir.resolve("store/$filename")
                if (!file.exists() || !file.isFile) return@mapNotNull null
                filename to file.readText()
            }
            val subsDir = unzipDir.resolve("subscription")
            val subscriptions = if (subsDir.exists() && subsDir.isDirectory) {
                (subsDir.listFiles { file ->
                    file.isFile && file.name.endsWith(".json")
                } ?: emptyArray()).filterNotNull().sortedBy { it.name }.map { file ->
                    val fileId = file.nameWithoutExtension.toLongOrNull()
                        ?: throw StorageException(
                            StorageIssue.subscription_invalid_filename,
                            file.name
                        )
                    val text = file.readText()
                    withContext(Dispatchers.Default) { json.decodeFromString<RawSubscription>(text) }.also { subscription ->
                        if (!(subscription.id == fileId)) {
                            throw StorageException(
                                StorageIssue.subscription_file_id_mismatch_detail,
                                fileId,
                                subscription.id
                            )
                        }
                    }
                }.also { list ->
                    if (!(list.map { it.id }.distinct().size == list.size)) {
                        throw StorageException(StorageIssue.backup_duplicate_subscription_id)
                    }
                }
            } else {
                emptyList()
            }
            PreparedBackup(
                dbData = dbData,
                storeEntries = storeEntries.toMap(),
                subscriptions = subscriptions,
            )
        }
}
