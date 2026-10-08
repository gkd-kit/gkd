package li.gkd.app.ui.snapshot

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.ui.state.BaseViewModel

class SnapshotSettingsViewModel : BaseViewModel() {
    val showCaptureScreenshotDialog: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setShowCaptureScreenshotDialog(value: Boolean) {
        showCaptureScreenshotDialog.value = value
    }
}
