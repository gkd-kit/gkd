package li.songe.gkd.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import li.songe.gkd.data.Snapshot
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.util.appInfoMapFlow

data class SnapshotAppGroup(
    val appId: String,
    val appName: String,
    val latestSnapshotId: Long,
    val snapshots: List<Snapshot>,
)

class SnapshotVm : BaseViewModel() {
    val snapshotsState = DbSet.snapshotDao.query().attachLoad()
        .stateInit(emptyList())

    // 双栏页只关心“某个 App 的快照集合”，先在 VM 内聚合可减少页面层的分支和重复排序。
    val appGroupsState = combine(snapshotsState, appInfoMapFlow) { snapshots, appInfoMap ->
        snapshots
            .groupBy { it.appId }
            .map { (appId, appSnapshots) ->
                val sortedSnapshots = appSnapshots.sortedByDescending { it.id }
                SnapshotAppGroup(
                    appId = appId,
                    appName = appInfoMap[appId]?.name ?: appId,
                    latestSnapshotId = sortedSnapshots.first().id,
                    snapshots = sortedSnapshots,
                )
            }
            .sortedWith(
                compareByDescending<SnapshotAppGroup> { it.latestSnapshotId }
                    .thenBy { it.appName }
            )
    }
        .flowOn(Dispatchers.Default)
        .stateInit(emptyList())
}
