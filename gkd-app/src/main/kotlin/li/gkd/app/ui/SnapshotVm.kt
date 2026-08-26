package li.songe.gkd.ui

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import li.songe.gkd.data.Snapshot
import li.songe.gkd.db.DbSet
import li.songe.gkd.snapshot.SnapshotStore
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.util.ImageUtils
import li.songe.gkd.util.appInfoMapFlow
import java.io.File

data class SnapshotUiState(
    val snapshots: List<Snapshot>,
    val appNames: Map<String, String>,
)

class SnapshotVm : BaseViewModel() {
    private val snapshotsFlow = DbSet.snapshotDao.query()

    val uiState = combine(
        snapshotsFlow,
        appInfoMapFlow,
    ) { snapshots, appInfoMap ->
        SnapshotUiState(
            snapshots = snapshots,
            appNames = appInfoMap.mapValues { it.value.name },
        )
    }.stateLoadable()

    suspend fun deleteAllSnapshots() = SnapshotStore.deleteAll()

    suspend fun deleteSnapshot(snapshot: Snapshot) = SnapshotStore.delete(snapshot)

    suspend fun buildShareArchive(snapshot: Snapshot): File {
        return SnapshotStore.createArchive(snapshot.id, snapshot.appId, snapshot.activityId)
    }

    suspend fun buildUploadArchive(snapshot: Snapshot): File =
        SnapshotStore.createArchive(snapshot.id)

    suspend fun saveScreenshotToAlbum(snapshot: Snapshot) = withContext(Dispatchers.IO) {
        ImageUtils.save2Album(BitmapFactory.decodeFile(snapshot.screenshotFile.absolutePath))
    }

    suspend fun markUploaded(snapshot: Snapshot, githubAssetId: Int) =
        withContext(Dispatchers.IO) {
            DbSet.snapshotDao.update(snapshot.copy(githubAssetId = githubAssetId))
        }

    suspend fun replaceScreenshot(snapshot: Snapshot, newBytes: ByteArray): Boolean {
        return SnapshotStore.replaceScreenshot(snapshot, newBytes)
    }
}
