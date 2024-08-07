package li.songe.gkd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.UploadOptions
import javax.inject.Inject

@HiltViewModel
class AdvancedVm @Inject constructor() : ViewModel() {
    val snapshotCountFlow =
        DbSet.snapshotDao.count().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val shizukuErrorFlow = MutableStateFlow(false)

    val uploadOptions = UploadOptions(viewModelScope)
}