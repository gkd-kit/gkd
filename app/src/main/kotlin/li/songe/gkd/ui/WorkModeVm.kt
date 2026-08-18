package li.songe.gkd.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import li.songe.gkd.permission.PermissionStates
import li.songe.gkd.ui.share.BaseViewModel
import kotlin.time.Duration.Companion.milliseconds

class WorkModeVm : BaseViewModel() {
    init {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                PermissionStates.refreshAll()
                delay(1000.milliseconds)
            }
        }
    }
}
