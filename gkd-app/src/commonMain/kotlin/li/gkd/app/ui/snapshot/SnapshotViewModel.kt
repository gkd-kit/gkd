package li.gkd.app.ui.snapshot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import li.gkd.app.snapshot.SnapshotStore
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.util.LogUtils
import li.gkd.db.Db
import li.gkd.db.Snapshot

data class SnapshotUiState(
    val snapshots: List<Snapshot>,
)

data class SnapshotDeleteResult(
    val deletedIds: Set<Long>,
    val failedCount: Int,
)

class SnapshotViewModel : BaseViewModel() {
    val uiState = Db.snapshotDao.query().map(::SnapshotUiState).stateLoadable()


    suspend fun deleteSnapshots(snapshots: List<Snapshot>): SnapshotDeleteResult {
        val deletedIds = mutableSetOf<Long>()
        var failedCount = 0
        snapshots.forEach { snapshot ->
            try {
                SnapshotStore.delete(snapshot)
                deletedIds.add(snapshot.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.d(e)
                failedCount++
            }
        }
        return SnapshotDeleteResult(deletedIds, failedCount)
    }

}
