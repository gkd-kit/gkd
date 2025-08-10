package li.songe.gkd.ui

import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.share.BaseViewModel

class SnapshotVm : BaseViewModel() {
    val snapshotsState = DbSet.snapshotDao.query().attachLoad()
        .stateInit(emptyList())
}