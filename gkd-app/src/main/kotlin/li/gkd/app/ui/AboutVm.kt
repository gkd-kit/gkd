package li.gkd.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.Option

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
