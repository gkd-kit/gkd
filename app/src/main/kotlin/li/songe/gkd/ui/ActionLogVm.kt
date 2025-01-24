package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import com.ramcosta.composedestinations.generated.destinations.ActionLogPageDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import li.songe.gkd.data.ActionLog
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.subsIdToRawFlow

class ActionLogVm(stateHandle: SavedStateHandle) : ViewModel() {
    private val args = ActionLogPageDestination.argsFrom(stateHandle)

    val pagingDataFlow = Pager(PagingConfig(pageSize = 100)) {
        if (args.subsId != null) {
            DbSet.actionLogDao.pagingSubsSource(subsId = args.subsId)
        } else if (args.appId != null) {
            DbSet.actionLogDao.pagingAppSource(appId = args.appId)
        } else {
            DbSet.actionLogDao.pagingSource()
        }
    }
        .flow
        .combine(subsIdToRawFlow) { pagingData, subsIdToRaw ->
            pagingData.map { c ->
                val group = if (c.groupType == SubsConfig.AppGroupType) {
                    val app = subsIdToRaw[c.subsId]?.apps?.find { a -> a.id == c.appId }
                    app?.groups?.find { g -> g.key == c.groupKey }
                } else {
                    subsIdToRaw[c.subsId]?.globalGroups?.find { g -> g.key == c.groupKey }
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
        .cachedIn(viewModelScope)

    val showActionLogFlow = MutableStateFlow<ActionLog?>(null)

}