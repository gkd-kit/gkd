package li.songe.gkd.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class AboutVm : ViewModel() {
    val showInfoDlgFlow = MutableStateFlow(false)
    val showShareLogDlgFlow = MutableStateFlow(false)
    val showShareAppDlgFlow = MutableStateFlow(false)
}