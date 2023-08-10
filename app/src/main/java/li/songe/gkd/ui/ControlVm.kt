package li.songe.gkd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.db.DbSet
import javax.inject.Inject

@HiltViewModel
class ControlVm @Inject constructor() : ViewModel() {
    val recordCountFlow = DbSet.triggerLogDb.triggerLogDao().count().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
}