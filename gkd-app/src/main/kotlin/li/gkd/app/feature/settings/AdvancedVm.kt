package li.gkd.app.feature.settings

import li.gkd.app.service.TrackService
import li.gkd.app.text.UiStrings
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.store.AppStore
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.ToastUtils.toast

class AdvancedVm : BaseViewModel() {

    fun setTrackServiceEnabled(enabled: Boolean) {
        if (enabled) TrackService.start() else TrackService.stop()
    }

    fun saveHttpServerPort(value: String): Boolean {
        val newPort = value.toIntOrNull()
        if (newPort == null || newPort !in 1000..65535) {
            toast(UiStrings.http_port_input_hint)
            return false
        }
        if (newPort == storeFlow.value.httpServerPort) {
            return true
        }
        AppStore.updateSettings { it.copy(httpServerPort = newPort) }
        toast(UiStrings.update_success)
        return true
    }

    fun setAutoClearMemorySubs(enabled: Boolean) {
        AppStore.updateSettings { it.copy(autoClearMemorySubs = enabled) }
    }
}
