package li.gkd.app.ui.log

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.db.Db

class ActivityLogViewModel :
    BaseViewModel() {
    val pagingDataFlow = Pager(PagingConfig(pageSize = 100)) {
        Db.activityLogDao.pagingSource()
    }
        .flow.cachedIn(scope)
}
