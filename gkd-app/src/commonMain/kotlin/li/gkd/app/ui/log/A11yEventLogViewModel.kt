package li.gkd.app.ui.log

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.db.Db

class A11yEventLogViewModel :
    BaseViewModel() {
    val pagingDataFlow =
        Pager(PagingConfig(pageSize = 100)) { Db.a11yEventLogDao.pagingSource() }
            .flow.cachedIn(scope)

}
