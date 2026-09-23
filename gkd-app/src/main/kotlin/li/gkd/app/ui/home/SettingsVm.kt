package li.gkd.app.ui.home

import android.net.Uri
import li.gkd.app.text.UiStrings
import li.gkd.app.service.fixRestartAutomatorService
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.store.AppStore
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.data.backup.BackupManager
import li.gkd.app.util.ToastUtils.toast
import java.io.File

class SettingsVm : BaseViewModel() {

    fun saveActionToast(enabled: Boolean, useSystemToast: Boolean, text: String): Boolean {
        require(text.isNotEmpty() && text.length <= 64)
        val store = storeFlow.value
        if (store.toastWhenClick == enabled && store.useSystemToast == useSystemToast && store.actionToast == text) return false
        AppStore.updateSettings {
            it.copy(toastWhenClick = enabled, useSystemToast = useSystemToast, actionToast = text)
        }
        return true
    }

    fun saveNotificationText(enabled: Boolean, title: String, text: String): Boolean {
        val store = storeFlow.value
        if (store.useCustomNotifText == enabled && store.customNotifTitle == title && store.customNotifText == text) return false
        AppStore.updateSettings {
            it.copy(
                useCustomNotifText = enabled,
                customNotifTitle = title,
                customNotifText = text,
            )
        }
        return true
    }

    fun setExcludeFromRecents(enabled: Boolean) {
        AppStore.updateSettings { it.copy(excludeFromRecents = enabled) }
    }

    fun setBlockA11yAppListEnabled(enabled: Boolean) {
        AppStore.updateSettings { it.copy(enableBlockA11yAppList = enabled) }
        if (!enabled) {
            fixRestartAutomatorService()
        }
    }

    fun setDarkTheme(value: Boolean?) {
        AppStore.updateSettings { it.copy(enableDarkTheme = value) }
    }

    fun setDynamicColor(enabled: Boolean) {
        AppStore.updateSettings { it.copy(enableDynamicColor = enabled) }
    }

    suspend fun importBackup(uri: Uri) {
        toast(UiStrings.backup_import_progress)
        val skipped = BackupManager.importData(uri)
        toast(if (skipped > 0) UiStrings.backup_import_skipped_config_count(skipped) else UiStrings.import_success)
    }

    suspend fun exportBackup(): File = BackupManager.exportData()
}
