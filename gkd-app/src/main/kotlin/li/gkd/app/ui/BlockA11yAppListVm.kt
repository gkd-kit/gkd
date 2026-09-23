package li.gkd.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import li.gkd.app.text.UiStrings
import li.gkd.app.MainViewModel
import li.gkd.app.service.fixRestartAutomatorService
import li.gkd.app.store.AppStore.blockA11yAppListFlow
import li.gkd.app.store.AppStore
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.ui.share.useAppFilter
import li.gkd.app.util.AppListString
import li.gkd.app.util.AppSortOption
import li.gkd.app.util.findOption
import li.gkd.app.util.switchItem
import li.gkd.app.util.ToastUtils.toast

class BlockA11yAppListVm(mainVm: MainViewModel) : BaseViewModel() {
    val appFilter = useAppFilter(
        mainVm = mainVm,
        appGroupType = { it.a11yAppGroupType },
        sortType = { AppSortOption.objects.findOption(it.a11yAppSort) },
    )
    val searchStrFlow = appFilter.searchStrFlow

    val showSearchBarFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val appInfosFlow = appFilter.appListFlow

    val editableFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val textFlow: StateFlow<String>
        field = MutableStateFlow("")
    val textChanged get() = blockA11yAppListFlow.value != AppListString.decode(textFlow.value)

    val indicatorSizeFlow = textFlow.debounce(500).map {
        AppListString.decode(it).size
    }.stateInit(AppListString.decode(textFlow.value).size)

    fun setSortType(value: AppSortOption) {
        AppStore.updateSettings { it.copy(a11yAppSort = value.value) }
    }

    fun setAppGroupType(value: Int) {
        AppStore.updateSettings { it.copy(a11yAppGroupType = value) }
    }

    fun toggleFollowMatchList() {
        setSearchBarVisible(false)
        AppStore.updateSettings {
            it.copy(blockA11yAppListFollowMatch = !it.blockA11yAppListFollowMatch)
        }
        fixRestartAutomatorService()
    }

    fun setSearchStr(value: String) {
        appFilter.updateSearchStr(value.trim())
    }

    fun setSearchBarVisible(visible: Boolean) {
        showSearchBarFlow.value = visible
        if (!visible) appFilter.updateSearchStr("")
    }

    fun toggleSearchBar() {
        if (!showSearchBarFlow.value) {
            showSearchBarFlow.value = true
        } else if (searchStrFlow.value.isEmpty()) {
            setSearchBarVisible(false)
        } else {
            appFilter.updateSearchStr("")
        }
    }

    fun setEditable(editable: Boolean) {
        if (editable && !editableFlow.value) {
            setSearchBarVisible(false)
            textFlow.value = AppListString.encode(blockA11yAppListFlow.value, append = true)
        }
        editableFlow.value = editable
    }

    fun setText(value: String) {
        textFlow.value = value
    }

    fun saveText() {
        if (textChanged) {
            AppStore.replaceBlockA11yAppList(AppListString.decode(textFlow.value))
            toast(UiStrings.update_success)
        } else {
            toast(UiStrings.unchanged)
        }
        editableFlow.value = false
    }

    fun toggleApp(appId: String) {
        AppStore.updateBlockA11yAppList { it.switchItem(appId) }
    }
}
