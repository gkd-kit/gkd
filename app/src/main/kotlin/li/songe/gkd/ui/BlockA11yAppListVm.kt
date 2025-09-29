package li.songe.gkd.ui

import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import li.songe.gkd.store.blockA11yAppListFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.ui.share.useAppFilter
import li.songe.gkd.util.AppListString
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.findOption

class BlockA11yAppListVm : BaseViewModel() {

    val sortTypeFlow = storeFlow.mapNew {
        AppSortOption.objects.findOption(it.a11yAppSort)
    }

    val appFilter = useAppFilter(
        sortTypeFlow = sortTypeFlow,
    )
    val searchStrFlow = appFilter.searchStrFlow

    val showSearchBarFlow = MutableStateFlow(false)
    val appInfosFlow = appFilter.appListFlow

    val resetKey = mutableIntStateOf(0)
    val editableFlow = MutableStateFlow(false)

    val textFlow = MutableStateFlow("")
    val textChanged get() = blockA11yAppListFlow.value != AppListString.decode(textFlow.value)

    val indicatorTextFlow = textFlow.debounce(500).map {
        AppListString.decode(it).size.toString()
    }.stateInit("")

    init {
        showSearchBarFlow.launchCollect {
            if (!it) {
                searchStrFlow.value = ""
            }
        }
        editableFlow.launchOnChange {
            if (it) {
                showSearchBarFlow.value = false
                textFlow.value = AppListString.encode(blockA11yAppListFlow.value, append = true)
            }
        }
        appInfosFlow.launchOnChange {
            resetKey.intValue++
        }
    }
}