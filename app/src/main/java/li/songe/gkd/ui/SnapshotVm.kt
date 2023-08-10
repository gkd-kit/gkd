package li.songe.gkd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.db.DbSet
import javax.inject.Inject

@HiltViewModel
class SnapshotVm @Inject constructor() : ViewModel() {
    val snapshotsState = DbSet.snapshotDao.query().map { it.reversed() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}