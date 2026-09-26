package li.gkd.app.feature.log

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import li.gkd.db.Db
import li.gkd.app.ui.share.BaseViewModel

class A11yEventLogVm : BaseViewModel() {
    val pagingDataFlow =
        Pager(PagingConfig(pageSize = 100)) { Db.a11yEventLogDao.pagingSource() }
            .flow.cachedIn(scope)

}
