package li.gkd.app.ui

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.data.A11yEventLog
import li.gkd.app.db.DbSet
import li.gkd.app.ui.share.BaseViewModel

class A11yEventLogVm : BaseViewModel() {
    val pagingDataFlow =
        Pager(PagingConfig(pageSize = 100)) { DbSet.a11yEventLogDao.pagingSource() }
            .flow.cachedIn(scope)

    val showEventLogFlow: StateFlow<A11yEventLog?>
        field = MutableStateFlow<A11yEventLog?>(null)

    fun showEventLog(eventLog: A11yEventLog) {
        showEventLogFlow.value = eventLog
    }

    fun dismissEventLog() {
        showEventLogFlow.value = null
    }

    suspend fun deleteAll() {
        DbSet.a11yEventLogDao.deleteAll()
    }
}
