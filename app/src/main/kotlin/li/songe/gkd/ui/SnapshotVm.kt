package li.songe.gkd.ui

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import li.songe.gkd.data.Snapshot
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.util.ImageUtils
import li.songe.gkd.util.SnapshotExt
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

    suspend fun deleteAllSnapshots() = withContext(Dispatchers.IO) {
        snapshotsFlow.first().forEach { snapshot ->
            SnapshotExt.removeSnapshot(snapshot.id)
        }
        DbSet.snapshotDao.deleteAll()
    }

    suspend fun deleteSnapshot(snapshot: Snapshot) = withContext(Dispatchers.IO) {
        DbSet.snapshotDao.delete(snapshot)
        SnapshotExt.removeSnapshot(snapshot.id)
    }

    suspend fun buildShareArchive(snapshot: Snapshot): File = withContext(Dispatchers.IO) {
        SnapshotExt.snapshotZipFile(snapshot.id, snapshot.appId, snapshot.activityId)
    }

    suspend fun buildUploadArchive(snapshot: Snapshot): File = withContext(Dispatchers.IO) {
        SnapshotExt.snapshotZipFile(snapshot.id)
    }

    suspend fun saveScreenshotToAlbum(snapshot: Snapshot) = withContext(Dispatchers.IO) {
        ImageUtils.save2Album(BitmapFactory.decodeFile(snapshot.screenshotFile.absolutePath))
    }

    suspend fun markUploaded(snapshot: Snapshot, githubAssetId: Int) =
        withContext(Dispatchers.IO) {
            DbSet.snapshotDao.update(snapshot.copy(githubAssetId = githubAssetId))
        }

    suspend fun replaceScreenshot(snapshot: Snapshot, newBytes: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            val oldBitmap = BitmapFactory.decodeFile(snapshot.screenshotFile.absolutePath)
            val newBitmap = BitmapFactory.decodeByteArray(newBytes, 0, newBytes.size)
            if (oldBitmap.width != newBitmap.width || oldBitmap.height != newBitmap.height) {
                return@withContext false
            }
            snapshot.screenshotFile.writeBytes(newBytes)
            if (snapshot.githubAssetId != null) {
                DbSet.snapshotDao.deleteGithubAssetId(snapshot.id)
            }
            true
        }
}
