package li.gkd.app.snapshot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import li.gkd.app.model.AppInfo
import li.gkd.app.model.ComplexSnapshot
import li.gkd.app.model.DeviceInfo
import li.gkd.app.model.NodeInfo
import li.gkd.app.platform.PlatformResult
import li.gkd.app.storage.FileExports
import li.gkd.app.storage.saveToDownloads

enum class SnapshotScreenshotStatus { Captured, Unavailable, LikelyProtected }

/** Platform resources must be released before returning this value, including on cancellation. */
class SnapshotCaptureInput(
    val appId: String,
    val activityId: String?,
    val screenHeight: Int,
    val screenWidth: Int,
    val isLandscape: Boolean,
    val nodes: List<NodeInfo>,
    val appInfo: AppInfo?,
    val gkdAppInfo: AppInfo?,
    val device: DeviceInfo,
    val screenshot: ByteArray,
    val screenshotStatus: SnapshotScreenshotStatus,
)

sealed interface SnapshotAutoExportResult {
    data object Disabled : SnapshotAutoExportResult
    data object PermissionDenied : SnapshotAutoExportResult
    data class Completed(val result: PlatformResult<String>) : SnapshotAutoExportResult
    data class Failed(val cause: Exception) : SnapshotAutoExportResult
}

data class SnapshotCaptureResult(
    val snapshot: ComplexSnapshot,
    val screenshotStatus: SnapshotScreenshotStatus,
    val autoExport: SnapshotAutoExportResult,
)

class SnapshotCaptureBusyException :
    IllegalStateException("Snapshot capture is already in progress")

/** Application-owned orchestration. Callers own coroutines and user feedback. */
object SnapshotCaptureRepository {
    private val mutex = Mutex()
    val isCapturing: Boolean get() = mutex.isLocked

    /** Cancellation propagates; a snapshot already committed by the store remains saved. */
    suspend fun capture(
        autoExport: Boolean,
        collect: suspend () -> SnapshotCaptureInput,
    ): SnapshotCaptureResult {
        if (!mutex.tryLock()) throw SnapshotCaptureBusyException()
        try {
            currentCoroutineContext().ensureActive()
            val id = System.currentTimeMillis()
            val input = collect()
            val snapshot = ComplexSnapshot(
                id, input.appId, input.activityId, input.screenHeight, input.screenWidth,
                input.isLandscape, input.appInfo, input.gkdAppInfo, input.device, input.nodes,
            )
            SnapshotStore.save(snapshot, input.screenshot)
            val exported = if (autoExport) export(snapshot) else SnapshotAutoExportResult.Disabled
            return SnapshotCaptureResult(snapshot, input.screenshotStatus, exported)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun export(snapshot: ComplexSnapshot): SnapshotAutoExportResult = try {
        if (!canExportDownloads()) {
            SnapshotAutoExportResult.PermissionDenied
        } else {
            SnapshotAutoExportResult.Completed(
                FileExports.withTemporaryFile(
                    create = {
                        SnapshotStore.createArchive(
                            snapshot.id,
                            snapshot.appId,
                            snapshot.activityId
                        )
                    },
                    delete = SnapshotStore::deleteArchive,
                    consume = ::saveToDownloads,
                )
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SnapshotAutoExportResult.Failed(e)
    }
}

expect suspend fun canExportDownloads(): Boolean
