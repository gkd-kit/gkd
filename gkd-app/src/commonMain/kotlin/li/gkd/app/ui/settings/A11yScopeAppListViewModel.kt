package li.gkd.app.ui.settings

import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.home.scopeAppSource
import li.gkd.app.ui.option.AppSortOption
import li.gkd.app.ui.state.BaseViewModel

class A11yScopeAppListViewModel : BaseViewModel() {
    val uiState = scopeAppSource.stateLoadable()
    val editor = AppIdListEditor(
        { SettingsRepository.a11yScopeAppList.value },
        SettingsRepository::replaceA11yScopeAppList
    )


    fun setSortType(value: AppSortOption) {
        SettingsRepository.updateSettings { it.copy(a11yScopeAppSort = value.value) }
    }

    fun setAppGroupType(value: Int) {
        SettingsRepository.updateSettings { it.copy(a11yScopeAppGroupType = value) }
    }

    fun toggleApp(appId: String) {
        SettingsRepository.updateA11yScopeAppList { if (appId in it) it - appId else it + appId }
    }
}
