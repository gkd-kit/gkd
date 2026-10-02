package li.gkd.app.ui.snapshot

import li.gkd.app.snapshot.SnapshotStore
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.db.Snapshot

data class SnapshotImage(val path: String, val modifiedAt: Long, val exists: Boolean)

/** Native file and permission operations; snapshot business workflows stay in commonMain. */
interface SnapshotPlatformActions {
    suspend fun ensureSavePermission(): Boolean
    suspend fun share(file: java.io.File): li.gkd.app.platform.PlatformResult<Unit>

    /** Success(false) means the user cancelled the destination picker. */
    suspend fun save(file: java.io.File): li.gkd.app.platform.PlatformResult<Boolean>
    suspend fun pickImage(): li.gkd.app.storage.FileSource?
}

interface SnapshotActions {
    fun share(snapshot: Snapshot)
    fun saveToDownloads(snapshot: Snapshot)
    fun saveToAlbum(snapshot: Snapshot)
    fun replace(snapshot: Snapshot)
    suspend fun saveSelectedToDownloads(snapshots: List<Snapshot>): Int?
    suspend fun saveSelectedToAlbum(snapshots: List<Snapshot>): Int?
    fun upload(snapshot: Snapshot)
    fun delete(snapshot: Snapshot)
}


fun Snapshot.image(): SnapshotImage = SnapshotStore.screenshotFile(id).let {
    SnapshotImage(it.absolutePath, it.lastModified(), it.isFile)
}

expect fun AppWindow.snapshotPlatformActions(): SnapshotPlatformActions
