package li.songe.gkd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import li.songe.gkd.db.DbSet
import javax.inject.Inject

@HiltViewModel
class RecordVm @Inject constructor() : ViewModel() {
    val triggerLogsFlow = DbSet.triggerLogDb.triggerLogDao().query().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val subItemsFlow = DbSet.subsItemDao.query().onEach {
        withContext(Dispatchers.IO) {
            it.forEach { subs -> subs.subscriptionRaw }
        }
    }
}