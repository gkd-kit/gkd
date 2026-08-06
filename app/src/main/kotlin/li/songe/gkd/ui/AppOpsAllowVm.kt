package li.songe.gkd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import li.songe.gkd.permission.PermissionStates
import kotlin.time.Duration.Companion.milliseconds

class AppOpsAllowVm : ViewModel() {
    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                PermissionStates.foregroundServiceSpecialUse.updateAndGet()
                delay(1000.milliseconds)
            }
        }
    }
}
