package li.songe.gkd.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.util.Option

class AboutVm : BaseViewModel() {
    val showInfoDlgFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val showShareAppDlgFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setInfoDialogVisible(visible: Boolean) {
        showInfoDlgFlow.value = visible
    }

    fun setShareAppDialogVisible(visible: Boolean) {
        showShareAppDlgFlow.value = visible
    }

    fun setUpdateChannel(value: Option<Int>) {
        storeFlow.update { it.copy(updateChannel = value.value) }
    }
}
