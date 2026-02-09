package li.songe.gkd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import li.songe.gkd.data.ActionLog
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.subsMapFlow

class ActionLogVm(val route: ActionLogRoute) : ViewModel() {

    val pagingDataFlow = Pager(PagingConfig(pageSize = 100)) {
        if (route.subsId != null) {
            DbSet.actionLogDao.pagingSubsSource(subsId = route.subsId)
        } else if (route.appId != null) {
            DbSet.actionLogDao.pagingAppSource(appId = route.appId)
        } else {
            DbSet.actionLogDao.pagingSource()
        }
    }
        .flow
        .cachedIn(viewModelScope)
        .combine(subsMapFlow) { pagingData, subsMap ->
            pagingData.map { c ->
                val group = if (c.groupType == SubsConfig.AppGroupType) {
                    val app = subsMap[c.subsId]?.apps?.find { a -> a.id == c.appId }
                    app?.groups?.find { g -> g.key == c.groupKey }
                } else {
                    subsMap[c.subsId]?.globalGroups?.find { g -> g.key == c.groupKey }
                }
                val rule = group?.rules?.run {
                    if (c.ruleKey != null) {
                        find { r -> r.key == c.ruleKey }
                    } else {
                        getOrNull(c.ruleIndex)
                    }
                }
                Triple(c, group, rule)
            }
        }

    val showActionLogFlow = MutableStateFlow<ActionLog?>(null)

}