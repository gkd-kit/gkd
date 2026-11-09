package li.gkd.app.util

import android.os.Environment
import android.os.UserManager
import java.io.File
import li.gkd.app.app
import li.gkd.app.storage.AppStorageLayout
import li.gkd.app.storage.DirectorySelectionRecord
import li.gkd.app.storage.StorageLocation
import li.gkd.app.storage.recordStartupLog

object AndroidStorage {
    private data class StorageInitialization(
        val filesDir: File,
        val directorySelectionRecord: DirectorySelectionRecord,
    )

    private val initialization: StorageInitialization by lazy {
        val internalFiles = app.filesDir
        val markFile = internalFiles.resolve(".gkd")
        val marked = markFile.isFile
        val external = app.getExternalFilesDir(null)
        val userUnlocked = app.getSystemService(UserManager::class.java)?.isUserUnlocked == true
        val externalState = Environment.getExternalStorageState()
        val selection =
            selectStorageRoot(
                internalFiles = internalFiles,
                external = external,
                markerExists = marked,
                userUnlocked = userUnlocked,
                externalPermanentlyUnavailable =
                    externalState == Environment.MEDIA_REMOVED ||
                        externalState == Environment.MEDIA_BAD_REMOVAL,
            )
        if (selection.removeMarker) markFile.delete()
        val markerCreated = if (selection.createMarker) markFile.createNewFile() else null
        val record =
            DirectorySelectionRecord(
                markerBefore = marked,
                externalResult = external?.absolutePath,
                reason = selection.reason,
                markerCreated = markerCreated,
                selectedPath = selection.selected.absolutePath,
                storageLocation =
                    if (selection.selected.absolutePath == external?.absolutePath) {
                        StorageLocation.External
                    } else {
                        StorageLocation.Internal
                    },
            )
        recordStartupLog(record, internalFiles, external)
        StorageInitialization(selection.selected, record)
    }

    val filesDir
        get() = initialization.filesDir

    val directorySelectionRecord
        get() = initialization.directorySelectionRecord

    private val cacheDir by lazy { app.externalCacheDir ?: app.cacheDir }

    val storage by lazy { AppStorageLayout(filesDir, cacheDir, app.filesDir) }
    val privilegeCrashDirectory by lazy { app.getExternalFilesDir("priv-crash") }
}
