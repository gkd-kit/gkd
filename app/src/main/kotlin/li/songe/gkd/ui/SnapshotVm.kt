package li.songe.gkd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.LinkLoad

class SnapshotVm : ViewModel() {
    val linkLoad = LinkLoad(viewModelScope)
    val snapshotsState = DbSet.snapshotDao.query().let(linkLoad::invoke)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}