package li.gkd.app.ui.settings

import li.gkd.app.platform.requestAutomatorRestart
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.home.scopeAppSource
import li.gkd.app.ui.option.AppSortOption
import li.gkd.app.ui.state.BaseViewModel

class BlockA11yAppListViewModel : BaseViewModel() {
    val uiState = scopeAppSource.stateLoadable()
    val editor = AppIdListEditor(
        { SettingsRepository.blockA11yAppList.value },
        SettingsRepository::replaceBlockA11yAppList
    )


    fun setSortType(value: AppSortOption) {
        SettingsRepository.updateSettings { it.copy(a11yAppSort = value.value) }
    }

    fun setAppGroupType(value: Int) {
        SettingsRepository.updateSettings { it.copy(a11yAppGroupType = value) }
    }

    fun toggleApp(appId: String) {
        SettingsRepository.updateBlockA11yAppList { if (appId in it) it - appId else it + appId }
    }

    fun toggleFollowMatchList() {
        SettingsRepository.updateSettings { it.copy(blockA11yAppListFollowMatch = !it.blockA11yAppListFollowMatch) }
        requestAutomatorRestart()
    }
}
