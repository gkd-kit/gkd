package li.songe.gkd.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.util.toast

class AdvancedVm : BaseViewModel() {

    val showEditPortDialogFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val httpSettingsDialogVisibleFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setEditPortDialogVisible(visible: Boolean) {
        showEditPortDialogFlow.value = visible
    }

    fun setHttpSettingsDialogVisible(visible: Boolean) {
        httpSettingsDialogVisibleFlow.value = visible
    }

    fun saveHttpServerPort(value: String): Boolean {
        val newPort = value.toIntOrNull()
        if (newPort == null || newPort !in 1000..65535) {
            toast("请输入 1000-65535 的整数")
            return false
        }
        if (newPort == storeFlow.value.httpServerPort) {
            return true
        }
        storeFlow.update { it.copy(httpServerPort = newPort) }
        toast("更新成功")
        return true
    }

    fun setAutoClearMemorySubs(enabled: Boolean) {
        storeFlow.update { it.copy(autoClearMemorySubs = enabled) }
    }
}
