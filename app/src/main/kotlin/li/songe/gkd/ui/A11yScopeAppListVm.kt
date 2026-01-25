package li.songe.gkd.ui

import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import li.songe.gkd.store.a11yScopeAppListFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.ui.share.asMutableStateFlow
import li.songe.gkd.ui.share.useAppFilter
import li.songe.gkd.util.AppListString
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.findOption

class A11yScopeAppListVm : BaseViewModel() {
    val sortTypeFlow = storeFlow.asMutableStateFlow(
        getter = { AppSortOption.objects.findOption(it.a11yScopeAppSort) },
        setter = {
            storeFlow.value.copy(a11yScopeAppSort = it.value)
        }
    )
    val appGroupTypeFlow = storeFlow.asMutableStateFlow(
        getter = { it.a11yScopeAppGroupType },
        setter = {
            storeFlow.value.copy(a11yScopeAppGroupType = it)
        }
    )
    val appFilter = useAppFilter(
        appGroupTypeFlow = appGroupTypeFlow,
        sortTypeFlow = sortTypeFlow,
    )
    val searchStrFlow = appFilter.searchStrFlow

    val showSearchBarFlow = MutableStateFlow(false)
    val appInfosFlow = appFilter.appListFlow

    val resetKey = mutableIntStateOf(0)
    val editableFlow = MutableStateFlow(false)

    val textFlow = MutableStateFlow("")
    val textChanged get() = a11yScopeAppListFlow.value != AppListString.decode(textFlow.value)

    val indicatorSizeFlow = textFlow.debounce(500).map {
        AppListString.decode(it).size
    }.stateInit(0)

    init {
        showSearchBarFlow.launchCollect {
            if (!it) {
                searchStrFlow.value = ""
            }
        }
        editableFlow.launchOnChange {
            if (it) {
                showSearchBarFlow.value = false
                textFlow.value = AppListString.encode(a11yScopeAppListFlow.value, append = true)
            }
        }
        appInfosFlow.launchOnChange {
            resetKey.intValue++
        }
    }
}