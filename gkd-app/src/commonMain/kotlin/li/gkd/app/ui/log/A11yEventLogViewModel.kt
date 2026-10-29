package li.gkd.app.ui.log

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import li.gkd.app.model.A11yEventLogItem
import li.gkd.app.model.toLogItem
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.db.Db

class A11yEventLogViewModel : BaseViewModel() {
    val shownEventLog: StateFlow<A11yEventLogItem?>
        field = MutableStateFlow(null)

    fun setShownEventLog(value: A11yEventLogItem?) {
        shownEventLog.value = value
    }

    private val logItemCache = mutableMapOf<Int, A11yEventLogItem>()

    val pagingDataFlow =
        Pager(PagingConfig(pageSize = 100)) { Db.a11yEventLogDao.pagingSource() }
            .flow
            .map { pagingData ->
                pagingData.map { log -> logItemCache.getOrPut(log.id) { log.toLogItem() } }
            }
            .cachedIn(scope)
}
