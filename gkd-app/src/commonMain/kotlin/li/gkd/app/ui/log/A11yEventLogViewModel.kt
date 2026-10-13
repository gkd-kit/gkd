package li.gkd.app.ui.log

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.db.Db

class A11yEventLogViewModel :
    BaseViewModel() {
    val shownEventLog: StateFlow<li.gkd.db.A11yEventLog?>
        field = MutableStateFlow(null)

    fun setShownEventLog(value: li.gkd.db.A11yEventLog?) {
        shownEventLog.value = value
    }

    val pagingDataFlow =
        Pager(PagingConfig(pageSize = 100)) { Db.a11yEventLogDao.pagingSource() }
            .flow.cachedIn(scope)

}
