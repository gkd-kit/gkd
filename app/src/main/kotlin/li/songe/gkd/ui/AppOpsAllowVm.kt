package li.songe.gkd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import li.songe.gkd.permission.foregroundServiceSpecialUseState

class AppOpsAllowVm : ViewModel() {
    val showCopyDlgFlow = MutableStateFlow(false)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                foregroundServiceSpecialUseState.updateAndGet()
                delay(1000)
            }
        }
    }
}