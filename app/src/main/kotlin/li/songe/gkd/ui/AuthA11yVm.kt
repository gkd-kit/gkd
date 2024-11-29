package li.songe.gkd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import li.songe.gkd.permission.writeSecureSettingsState

class AuthA11yVm : ViewModel() {
    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                writeSecureSettingsState.updateAndGet()
                delay(1000)
            }
        }
    }

    val showCopyDlgFlow = MutableStateFlow(false)
}
