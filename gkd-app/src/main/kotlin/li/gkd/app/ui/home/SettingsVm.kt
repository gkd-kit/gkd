package li.gkd.app.ui.home

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import li.gkd.app.service.TrackService
import li.gkd.app.service.fixRestartAutomatorService
import li.gkd.app.store.actionCountFlow
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.BackupUtils
import li.gkd.app.util.getSubsStatus
import li.gkd.app.util.ruleSummaryFlow
import java.io.File

class SettingsVm : BaseViewModel() {

    val showActionToastDialogFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val showNotificationTextDialogFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val showA11yBlockDialogFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val showBackupDialogFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val showExportBackupDialogFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val toastSettingsDialogVisibleFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setActionToastDialogVisible(visible: Boolean) {
        showActionToastDialogFlow.value = visible
    }

    fun setNotificationTextDialogVisible(visible: Boolean) {
        showNotificationTextDialogFlow.value = visible
    }

    fun setA11yBlockDialogVisible(visible: Boolean) {
        showA11yBlockDialogFlow.value = visible
    }

    fun setBackupDialogVisible(visible: Boolean) {
        showBackupDialogFlow.value = visible
    }

    fun setExportBackupDialogVisible(visible: Boolean) {
        showExportBackupDialogFlow.value = visible
    }

    fun setToastSettingsDialogVisible(visible: Boolean) {
        toastSettingsDialogVisibleFlow.value = visible
    }

    val subsStatusFlow = combine(ruleSummaryFlow, actionCountFlow) { ruleSummary, count ->
        getSubsStatus(ruleSummary, count)
    }.stateInit(getSubsStatus(ruleSummaryFlow.value, actionCountFlow.value))

    fun saveActionToast(value: String): Boolean {
        if (value == storeFlow.value.actionToast) return false
        storeFlow.update { it.copy(actionToast = value) }
        return true
    }

    fun saveNotificationText(title: String, text: String): Boolean {
        val store = storeFlow.value
        if (store.customNotifTitle == title && store.customNotifText == text) return false
        storeFlow.update {
            it.copy(
                customNotifTitle = title,
                customNotifText = text,
            )
        }
        return true
    }

    fun setToastWhenClick(enabled: Boolean) {
        storeFlow.update { it.copy(toastWhenClick = enabled) }
    }

    fun setUseSystemToast(enabled: Boolean) {
        storeFlow.update { it.copy(useSystemToast = enabled) }
    }

    fun setTrackServiceEnabled(enabled: Boolean) {
        if (enabled) TrackService.start() else TrackService.stop()
    }

    fun setUseCustomNotificationText(enabled: Boolean) {
        storeFlow.update { it.copy(useCustomNotifText = enabled) }
    }

    fun setExcludeFromRecents(enabled: Boolean) {
        storeFlow.update { it.copy(excludeFromRecents = enabled) }
    }

    fun setBlockA11yAppListEnabled(enabled: Boolean) {
        storeFlow.update { it.copy(enableBlockA11yAppList = enabled) }
        if (!enabled) {
            fixRestartAutomatorService()
        }
    }

    fun setDarkTheme(value: Boolean?) {
        storeFlow.update { it.copy(enableDarkTheme = value) }
    }

    fun setDynamicColor(enabled: Boolean) {
        storeFlow.update { it.copy(enableDynamicColor = enabled) }
    }

    suspend fun importBackup(uri: Uri) {
        withContext(Dispatchers.IO) {
            BackupUtils.importBackUpData(uri)
        }
    }

    suspend fun exportBackup(): File = withContext(Dispatchers.IO) {
        BackupUtils.exportBackUpData()
    }
}
