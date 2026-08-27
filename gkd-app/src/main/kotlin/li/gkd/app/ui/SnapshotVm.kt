package li.gkd.app.ui

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import li.gkd.app.data.screenshotFile
import li.gkd.app.snapshot.SnapshotStore
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.ImageUtils
import li.gkd.app.util.appInfoMapFlow
import li.gkd.db.Db
import li.gkd.db.Snapshot
import java.io.File

data class SnapshotUiState(
    val snapshots: List<Snapshot>,
    val appNames: Map<String, String>,
)

class SnapshotVm : BaseViewModel() {
    private val snapshotsFlow = Db.snapshotDao.query()

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
            Db.snapshotDao.update(snapshot.copy(githubAssetId = githubAssetId))
        }

    suspend fun replaceScreenshot(snapshot: Snapshot, newBytes: ByteArray): Boolean {
        return SnapshotStore.replaceScreenshot(snapshot, newBytes)
    }
}
