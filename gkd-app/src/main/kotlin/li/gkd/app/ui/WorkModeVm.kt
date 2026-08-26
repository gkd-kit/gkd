package li.gkd.app.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import li.gkd.app.permission.PermissionStates
import li.gkd.app.ui.share.BaseViewModel
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
