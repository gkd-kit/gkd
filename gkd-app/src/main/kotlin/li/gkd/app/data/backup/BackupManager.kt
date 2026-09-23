package li.gkd.app.data.backup

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import li.gkd.app.text.UiStrings
import li.gkd.app.store.AppStore
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.subscription.SubscriptionRepository
import li.gkd.app.data.subscription.SubscriptionFileStore
import li.gkd.app.data.subscription.SubscriptionPersistence
import li.gkd.app.util.FolderUtils
import li.gkd.app.util.ExportFileNames
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ZipUtils
import li.gkd.app.util.json
import java.io.File
import java.io.IOException
import li.gkd.db.Db
import li.gkd.db.SubscriptionConfigSnapshot

private data class PreparedBackup(
    val dbData: SubscriptionConfigSnapshot?,
    val storeEntries: Map<String, String>,
    val subscriptions: List<RawSubscription>,
)

object BackupManager {
    private val mutationMutex = Mutex()

    suspend fun exportData(): File = mutationMutex.withLock {
        withContext(Dispatchers.IO) {
            val tempDir = FolderUtils.createGkdTempDir()
            try {
                tempDir.resolve("store").run {
                    mkdir()
                    AppStore.exportBackupEntries().forEach { (filename, text) ->
                        resolve(filename).writeText(text)
                    }
                }
                tempDir.resolve("db.json").writeText(
                    BackupFormat.encode(
                        BackupDatabaseData.fromSnapshot(Db.subscriptionConfigStore.capture()),
                    ),
                )
                tempDir.resolve("subscription").run {
                    mkdir()
                    SubscriptionRepository.awaitSnapshot().subscriptions.values.forEach { subs ->
                        resolve("${subs.id}.json").writeText(json.encodeToString(subs))
                    }
                }
                val file = ExportFileNames.reserve(
                    FolderUtils.sharedDir,
                    "gkd-backup-${ExportFileNames.timestamp(System.currentTimeMillis())}",
                    "zip",
                )
                try {
                    if (!ZipUtils.zipFiles(tempDir.listFiles().orEmpty().toList(), file)) {
                        throw IOException(UiStrings.backup_compress_failed)
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

    suspend fun importData(uri: Uri) = mutationMutex.withLock {
        withContext(Dispatchers.IO) {
            val tempDir = FolderUtils.createGkdTempDir()
            try {
                val zipFile = tempDir.resolve("file.zip")
                val unzipDir = tempDir.resolve("unzip")
                try {
                    BackupArchiveReader.extract(uri, zipFile, unzipDir)
                } catch (e: SecurityException) {
                    LogUtils.d("importBackUpData.openFile", e)
                    throw IllegalArgumentException(UiStrings.backup_reselect_file, e)
                } catch (e: Exception) {
                    LogUtils.d("importBackUpData.unzipFile", e)
                    throw IllegalArgumentException(UiStrings.backup_invalid_archive, e)
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
                subscription.id to SubscriptionFileStore.readBytes(subscription.id)
            }
            AppStore.withBackupRestore(prepared.storeEntries) {
                try {
                    Db.withTransaction {
                        val skipped = prepared.dbData?.let { Db.subscriptionConfigStore.merge(it) } ?: 0
                        subscriptions.forEach { SubscriptionPersistence.save(it) }
                        skipped
                    }
                } catch (error: Throwable) {
                    previousFiles.forEach { (id, bytes) ->
                        runCatching { SubscriptionFileStore.restore(id, bytes) }
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
                withContext(Dispatchers.Default) { BackupFormat.decode(text).toSnapshot() }
            } else {
                null
            }
            val storeEntries = AppStore.backupFilenames.mapNotNull { filename ->
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
                        ?: error(UiStrings.subscription_invalid_filename(file.name))
                    val text = file.readText()
                    withContext(Dispatchers.Default) { json.decodeFromString<RawSubscription>(text) }.also { subscription ->
                        require(subscription.id == fileId) {
                            UiStrings.subscription_file_id_mismatch_detail(fileId, subscription.id)
                        }
                    }
                }.also { list ->
                    require(list.map { it.id }.distinct().size == list.size) {
                        UiStrings.backup_duplicate_subscription_id
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
