package li.gkd.app.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.matching.diagnostics.startDiagnosticCapture
import li.gkd.app.resources.*
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.state.BaseViewModel
import org.jetbrains.compose.resources.getString

class AdvancedViewModel : BaseViewModel() {
    fun requestDiagnosticCapture() {
        scope.launchUi {
            val confirmed =
                MainViewModel.requireCurrent()
                    .dialogRequests
                    .confirm(
                        title = getString(Res.string.match_debug_start_title),
                        text = getString(Res.string.match_debug_start_description),
                        confirmText = getString(Res.string.match_debug_start_confirm),
                        dismissOnRequest = true,
                    )
            if (confirmed) startDiagnosticCapture()
        }
    }

    val showTrackNotice: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setShowTrackNotice(value: Boolean) {
        showTrackNotice.value = value
    }
}
