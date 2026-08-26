package li.gkd.app.ui

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import li.gkd.app.db.DbSet
import li.gkd.app.ui.share.BaseViewModel

class ActivityLogVm : BaseViewModel() {
    val pagingDataFlow = Pager(PagingConfig(pageSize = 100)) { DbSet.activityLogDao.pagingSource() }
        .flow.cachedIn(scope)
    suspend fun deleteAll() {
        DbSet.activityLogDao.deleteAll()
    }
}
