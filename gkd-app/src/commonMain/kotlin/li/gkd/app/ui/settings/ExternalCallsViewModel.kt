package li.gkd.app.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.external.ExternalCalls
import li.gkd.app.resources.*
import li.gkd.app.ui.state.BaseViewModel
import li.gkd.app.util.ToastUtils

class ExternalCallsViewModel : BaseViewModel() {
    val externalToken: StateFlow<String>
        field = MutableStateFlow(ExternalCalls.token())
    val showTokenDialog: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val confirmTokenReset: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setShowTokenDialog(value: Boolean) {
        showTokenDialog.value = value
    }

    fun setConfirmTokenReset(value: Boolean) {
        confirmTokenReset.value = value
    }

    fun resetExternalToken() {
        confirmTokenReset.value = false
        externalToken.value = ExternalCalls.resetToken()
        ToastUtils.show(Res.string.external_calls_reset_success.getSync())
    }
}
