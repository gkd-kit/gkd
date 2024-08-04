package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.db.DbSet
import javax.inject.Inject

@HiltViewModel
class ActivityLogVm @Inject constructor(stateHandle: SavedStateHandle) : ViewModel() {
    val pagingDataFlow = Pager(PagingConfig(pageSize = 100)) { DbSet.activityLogDao.pagingSource() }
        .flow.cachedIn(viewModelScope)

    val logCountFlow =
        DbSet.activityLogDao.count().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
}