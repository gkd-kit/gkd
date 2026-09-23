package li.gkd.app.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import li.gkd.app.text.UiStrings
import li.gkd.app.appScope
import li.gkd.app.data.settings.SettingsRepository
import li.gkd.app.data.settings.SettingsStore
import li.gkd.app.priv.PrivilegeOwnerLifecycle
import li.gkd.app.util.AppListString
import li.gkd.app.util.FolderUtils
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.util.launchLogged

object AppStore {
    private val repository by lazy {
        SettingsRepository(
            storeFolder = FolderUtils.storeFolder,
            scope = appScope,
            defaultSettings = { SettingsStore() },
            defaultBlockMatchAppList = AppListString::getDefaultBlockList,
        )
    }

    fun initialize() {
        var configuredEnableAutomator = storeFlow.value.enableAutomator
        PrivilegeOwnerLifecycle.configure(configuredEnableAutomator)
        appScope.launchLogged(Dispatchers.IO) {
            storeFlow.collect { settings ->
                if (settings.enableAutomator != configuredEnableAutomator) {
                    configuredEnableAutomator = settings.enableAutomator
                    PrivilegeOwnerLifecycle.configure(configuredEnableAutomator)
                }
            }
        }
    }

    val storeFlow: StateFlow<SettingsStore>
        get() = repository.settings

    val actionCountFlow: StateFlow<Long>
        get() = repository.actionCount

    val blockMatchAppListFlow: StateFlow<Set<String>>
        get() = repository.blockMatchAppList

    val blockA11yAppListFlow: StateFlow<Set<String>>
        get() = repository.blockA11yAppList

    val actualBlockA11yAppList: Set<String>
        get() = if (storeFlow.value.blockA11yAppListFollowMatch) {
            blockMatchAppListFlow.value
        } else {
            blockA11yAppListFlow.value
        }

    val a11yScopeAppListFlow: StateFlow<Set<String>>
        get() = repository.a11yScopeAppList

    val actualA11yScopeAppList: Set<String>
        get() = if (storeFlow.value.useAutomation) {
            a11yScopeAppListFlow.value
        } else {
            emptySet()
        }

    fun checkAppBlockMatch(appId: String): Boolean {
        if (blockMatchAppListFlow.value.contains(appId)) {
            return true
        }
        if (storeFlow.value.enableBlockA11yAppList) {
            return actualBlockA11yAppList.contains(appId)
        }
        return false
    }

    fun updateSettings(transform: (SettingsStore) -> SettingsStore) =
        repository.updateSettings(transform)

    fun incrementActionCount() = repository.incrementActionCount()

    fun updateBlockMatchAppList(transform: (Set<String>) -> Set<String>) =
        repository.updateBlockMatchAppList(transform)

    fun replaceBlockMatchAppList(value: Set<String>) = repository.replaceBlockMatchAppList(value)

    fun updateBlockA11yAppList(transform: (Set<String>) -> Set<String>) =
        repository.updateBlockA11yAppList(transform)

    fun replaceBlockA11yAppList(value: Set<String>) = repository.replaceBlockA11yAppList(value)

    fun updateA11yScopeAppList(transform: (Set<String>) -> Set<String>) =
        repository.updateA11yScopeAppList(transform)

    fun replaceA11yScopeAppList(value: Set<String>) = repository.replaceA11yScopeAppList(value)

    val backupFilenames: Set<String>
        get() = repository.backupFilenames

    fun exportBackupEntries(): Map<String, String> = repository.exportBackupEntries()

    suspend fun <T> withBackupRestore(
        entries: Map<String, String>,
        block: suspend () -> T,
    ): T = repository.withBackupRestore(entries, block)

    fun toggleEnableMatch() {
        if (storeFlow.value.enableMatch) {
            toast(UiStrings.rule_matching_pause)
        } else {
            toast(UiStrings.rule_matching_enable)
        }
        updateSettings { it.copy(enableMatch = !it.enableMatch) }
    }

    fun updateEnableAutomator(value: Boolean) {
        if (value == storeFlow.value.enableAutomator) return
        updateSettings { it.copy(enableAutomator = value) }
    }

    fun updateAutomatorMode(value: Int) {
        updateSettings {
            it.copy(automatorMode = value, enableAutomator = false)
        }
    }
}
