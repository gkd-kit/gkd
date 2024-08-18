package li.songe.gkd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.UploadOptions

class AdvancedVm : ViewModel() {
    val snapshotCountFlow =
        DbSet.snapshotDao.count().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val shizukuErrorFlow = MutableStateFlow(false)

    val uploadOptions = UploadOptions(viewModelScope)
}