package li.songe.gkd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.data.Tuple3
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.subsIdToRawFlow
import javax.inject.Inject

@HiltViewModel
class ClickLogVm @Inject constructor() : ViewModel() {

    val clickDataListFlow =
        combine(DbSet.clickLogDao.query(), subsIdToRawFlow) { clickLogs, subsIdToRaw ->
            clickLogs.map { c ->
                val app = subsIdToRaw[c.subsId]?.apps?.find { a -> a.id == c.appId }
                val group = app?.groups?.find { g -> g.key == c.groupKey }
                val rule = group?.rules?.run {
                    if (c.ruleKey != null) {
                        find { r -> r.key == c.ruleKey }
                    } else {
                        getOrNull(c.ruleIndex)
                    }
                }
                Tuple3(c, group, rule)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val clickLogCountFlow =
        DbSet.clickLogDao.count().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

}