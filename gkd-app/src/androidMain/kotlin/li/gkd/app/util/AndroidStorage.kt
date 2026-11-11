package li.gkd.app.util

import android.util.Log
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.launch
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.storage.AppStorageLayout
import li.gkd.app.storage.DirectorySelectionRecord
import li.gkd.app.storage.ExternalStorageMigration
import li.gkd.app.storage.HistoryDirectory
import li.gkd.app.storage.StorageSelectionReason
import li.gkd.app.storage.recordStartupLog

object AndroidStorage {
    private data class StorageInitialization(
        val migration: ExternalStorageMigration,
        val directorySelectionRecord: DirectorySelectionRecord,
        val history: Map<HistoryDirectory, CompletableDeferred<Unit>>,
    )

    private val initialization: StorageInitialization by lazy {
        val internalFiles = app.filesDir
        val migration =
            ExternalStorageMigration(
                internalFiles,
                app.noBackupFilesDir.resolve("external-migration"),
            )
        val record = migration.prepare(::externalFiles)
        recordStartupLog(record, internalFiles, record.externalResult?.let(::File))
        val history =
            if (record.reason == StorageSelectionReason.ExternalMigration)
                HistoryDirectory.entries.associateWith { CompletableDeferred<Unit>() }
            else emptyMap()
        StorageInitialization(migration, record, history)
    }

    private fun externalFiles() = app.getExternalFilesDir(null)

    val directorySelectionRecord
        get() = initialization.directorySelectionRecord

    fun initialize() {
        val (migration, _, history) = initialization
        if (history.isEmpty()) return
        // These directories can receive new reports while history is being migrated.
        storage.log
        storage.crashTemp
        appScope.launch(Dispatchers.IO) {
            try {
                val migrated =
                    migration.migrateHistory(::externalFiles) { directory, result ->
                        history.getValue(directory).completeWith(result)
                        result.exceptionOrNull()?.let {
                            Log.e("GKD", "Cannot migrate external ${directory.path}", it)
                        }
                    }
                if (!migrated) return@launch
            } catch (e: CancellationException) {
                history.values.forEach { it.cancel(e) }
                throw e
            } catch (e: Exception) {
                history.values.forEach { it.completeExceptionally(e) }
                Log.e("GKD", "Cannot migrate external history", e)
                return@launch
            }
            try {
                migration.cleanUp(::externalFiles)
            } catch (e: Exception) {
                Log.e("GKD", "Cannot clean up migrated external files", e)
            }
        }
    }

    val storage by lazy {
        val history = initialization.history
        AppStorageLayout(app.filesDir, app.cacheDir, app.filesDir) { directory ->
            history[directory]?.await()
        }
    }
}
