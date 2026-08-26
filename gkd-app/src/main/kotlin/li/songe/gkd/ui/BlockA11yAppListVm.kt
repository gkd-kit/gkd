package li.songe.gkd.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import li.songe.gkd.MainViewModel
import li.songe.gkd.service.fixRestartAutomatorService
import li.songe.gkd.store.blockA11yAppListFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.ui.share.useAppFilter
import li.songe.gkd.util.AppListString
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.findOption
import li.songe.gkd.util.toast

class BlockA11yAppListVm(mainVm: MainViewModel) : BaseViewModel() {
    val appFilter = useAppFilter(
        mainVm = mainVm,
        appGroupType = { it.a11yAppGroupType },
        sortType = { AppSortOption.objects.findOption(it.a11yAppSort) },
    )
    val searchStrFlow: StateFlow<String>
        field = appFilter.searchStrFlow

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
        storeFlow.update { it.copy(a11yAppSort = value.value) }
    }

    fun setAppGroupType(value: Int) {
        storeFlow.update { it.copy(a11yAppGroupType = value) }
    }

    fun toggleFollowMatchList() {
        setSearchBarVisible(false)
        storeFlow.update {
            it.copy(blockA11yAppListFollowMatch = !it.blockA11yAppListFollowMatch)
        }
        fixRestartAutomatorService()
    }

    fun setSearchStr(value: String) {
        searchStrFlow.value = value.trim()
    }

    fun setSearchBarVisible(visible: Boolean) {
        showSearchBarFlow.value = visible
        if (!visible) searchStrFlow.value = ""
    }

    fun toggleSearchBar() {
        if (!showSearchBarFlow.value) {
            showSearchBarFlow.value = true
        } else if (searchStrFlow.value.isEmpty()) {
            setSearchBarVisible(false)
        } else {
            searchStrFlow.value = ""
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
            blockA11yAppListFlow.value = AppListString.decode(textFlow.value)
            toast("更新成功")
        } else {
            toast("未修改")
        }
        editableFlow.value = false
    }
}
