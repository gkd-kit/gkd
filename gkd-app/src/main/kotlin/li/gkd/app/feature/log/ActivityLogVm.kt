package li.gkd.app.feature.log

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.db.Db

class ActivityLogVm : BaseViewModel() {
    val pagingDataFlow = Pager(PagingConfig(pageSize = 100)) {
        Db.activityLogDao.pagingSource()
    }
        .flow.cachedIn(scope)
}
