package li.gkd.app.ui.snapshot

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.ui.state.BaseViewModel

class SnapshotSettingsViewModel : BaseViewModel() {
    val showCaptureScreenshotDialog: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val appIdDraft: StateFlow<String>
        field = MutableStateFlow("")
    val eventSelectorDraft: StateFlow<String>
        field = MutableStateFlow("")

    fun editCaptureScreenshot(appId: String, eventSelector: String) {
        appIdDraft.value = appId
        eventSelectorDraft.value = eventSelector
        showCaptureScreenshotDialog.value = true
    }

    fun setAppIdDraft(value: String) {
        appIdDraft.value = value
    }

    fun setEventSelectorDraft(value: String) {
        eventSelectorDraft.value = value
    }

    fun closeCaptureScreenshotDialog() {
        showCaptureScreenshotDialog.value = false
    }
}
