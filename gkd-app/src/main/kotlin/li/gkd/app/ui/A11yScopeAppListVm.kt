package li.gkd.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import li.gkd.app.text.UiStrings
import li.gkd.app.MainViewModel
import li.gkd.app.store.AppStore.a11yScopeAppListFlow
import li.gkd.app.store.AppStore
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.ui.share.useAppFilter
import li.gkd.app.util.AppListString
import li.gkd.app.util.AppSortOption
import li.gkd.app.util.findOption
import li.gkd.app.util.switchItem
import li.gkd.app.util.ToastUtils.toast

class A11yScopeAppListVm(mainVm: MainViewModel) : BaseViewModel() {
    val appFilter = useAppFilter(
        mainVm = mainVm,
        appGroupType = { it.a11yScopeAppGroupType },
        sortType = { AppSortOption.objects.findOption(it.a11yScopeAppSort) },
    )
    val searchStrFlow = appFilter.searchStrFlow

    val showSearchBarFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val appInfosFlow = appFilter.appListFlow

    val editableFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val textFlow: StateFlow<String>
        field = MutableStateFlow("")
    val textChanged get() = a11yScopeAppListFlow.value != AppListString.decode(textFlow.value)

    val indicatorSizeFlow = textFlow.debounce(500).map {
        AppListString.decode(it).size
    }.stateInit(AppListString.decode(textFlow.value).size)

    fun setSortType(value: AppSortOption) {
        AppStore.updateSettings { it.copy(a11yScopeAppSort = value.value) }
    }

    fun setAppGroupType(value: Int) {
        AppStore.updateSettings { it.copy(a11yScopeAppGroupType = value) }
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
            textFlow.value = AppListString.encode(a11yScopeAppListFlow.value, append = true)
        }
        editableFlow.value = editable
    }

    fun setText(value: String) {
        textFlow.value = value
    }

    fun saveText() {
        if (textChanged) {
            AppStore.replaceA11yScopeAppList(AppListString.decode(textFlow.value))
            toast(UiStrings.update_success)
        } else {
            toast(UiStrings.unchanged)
        }
        editableFlow.value = false
    }

    fun toggleApp(appId: String) {
        AppStore.updateA11yScopeAppList { it.switchItem(appId) }
    }
}
