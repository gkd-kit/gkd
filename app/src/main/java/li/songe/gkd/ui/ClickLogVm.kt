package li.songe.gkd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.data.ClickLog
import li.songe.gkd.db.DbSet
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
import javax.inject.Inject

@HiltViewModel
class ClickLogVm @Inject constructor() : ViewModel() {
    val clickLogsFlow = DbSet.clickLogDb.clickLogDao().query()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val clickLogCountFlow = DbSet.clickLogDb.clickLogDao().count()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun getGroup(clickLog: ClickLog): SubscriptionRaw.GroupRaw? {
        val subsItem = subsItemsFlow.value.find { s -> s.id == clickLog.subsId } ?: return null
        return subsIdToRawFlow.value[subsItem.id]?.apps?.find { a -> a.id == clickLog.appId }?.groups?.find { g -> g.key == clickLog.groupKey }
    }
}