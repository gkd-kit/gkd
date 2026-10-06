package li.gkd.app.util

import li.gkd.app.app
import li.gkd.app.storage.AppStorageLayout
import li.gkd.app.storage.StorageLocation
import li.gkd.app.storage.DirectorySelectionRecord
import li.gkd.app.storage.StorageSelectionReason
import li.gkd.app.storage.recordStartupLog
import java.io.File

object AndroidStorage {
    private data class StorageInitialization(val filesDir: File, val directorySelectionRecord: DirectorySelectionRecord)

    private val initialization: StorageInitialization by lazy {
        val internalFiles = app.filesDir
        val markFile = internalFiles.resolve(".gkd")
        val marked = markFile.isFile
        val external = if (marked) null else app.getExternalFilesDir(null)
        // fix #1333: once external storage is unavailable, keep using internal storage.
        val createMarker = !marked && external == null
        val markerCreated = if (createMarker) markFile.createNewFile() else null
        val selected = external ?: internalFiles
        val record = DirectorySelectionRecord(
            markerBefore = marked,
            externalResult = external?.absolutePath,
            reason = when {
                marked -> StorageSelectionReason.InternalMarkerExists
                external != null -> StorageSelectionReason.ExternalAvailable
                else -> StorageSelectionReason.ExternalNullFallback
            },
            markerCreated = markerCreated,
            selectedPath = selected.absolutePath,
            storageLocation = if (external != null) StorageLocation.External else StorageLocation.Internal,
        )
        recordStartupLog(record, internalFiles, external)
        StorageInitialization(selected, record)
    }

    val filesDir get() = initialization.filesDir
    val directorySelectionRecord get() = initialization.directorySelectionRecord
    private val cacheDir by lazy { app.externalCacheDir ?: app.cacheDir }

    val storage by lazy { AppStorageLayout(filesDir, cacheDir, app.filesDir) }
    val privilegeCrashDirectory by lazy { app.getExternalFilesDir("priv-crash") }
}
