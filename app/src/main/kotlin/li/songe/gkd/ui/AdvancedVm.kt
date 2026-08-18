package li.songe.gkd.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.util.appInfoMapFlow
import li.songe.gkd.util.toast
import li.songe.selector.Selector

class AdvancedVm : BaseViewModel() {

    val showEditPortDialogFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val showCaptureScreenshotDialogFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val httpSettingsDialogVisibleFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setEditPortDialogVisible(visible: Boolean) {
        showEditPortDialogFlow.value = visible
    }

    fun setCaptureScreenshotDialogVisible(visible: Boolean) {
        showCaptureScreenshotDialogFlow.value = visible
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

    fun saveCaptureScreenshotConfig(
        appId: String,
        eventSelector: String,
    ): Boolean {
        val store = storeFlow.value
        if (
            appId == store.screenshotTargetAppId &&
            eventSelector == store.screenshotEventSelector
        ) {
            return true
        }
        if (appId.isNotEmpty() && !appInfoMapFlow.value.contains(appId)) {
            toast("无效应用ID")
            return false
        }
        if (eventSelector.isNotEmpty() && Selector.parseOrNull(eventSelector) == null) {
            toast("无效事件选择器")
            return false
        }
        storeFlow.update {
            it.copy(
                screenshotTargetAppId = appId,
                screenshotEventSelector = eventSelector,
            )
        }
        toast("更新成功")
        return true
    }

    fun setAutoClearMemorySubs(enabled: Boolean) {
        storeFlow.update { it.copy(autoClearMemorySubs = enabled) }
    }

    fun setCaptureVolumeChange(enabled: Boolean) {
        storeFlow.update { it.copy(captureVolumeChange = enabled) }
    }

    fun setCaptureScreenshot(enabled: Boolean) {
        val store = storeFlow.value
        storeFlow.update { it.copy(captureScreenshot = enabled) }
        if (
            enabled && (
                store.screenshotTargetAppId.isEmpty() ||
                    store.screenshotEventSelector.isEmpty()
            )
        ) {
            toast("请配置目标应用和特征事件选择器")
        }
    }

    fun setHideSnapshotStatusBar(enabled: Boolean) {
        storeFlow.update { it.copy(hideSnapshotStatusBar = enabled) }
    }

    fun setShowSaveSnapshotToast(enabled: Boolean) {
        storeFlow.update { it.copy(showSaveSnapshotToast = enabled) }
    }

}
