package li.songe.gkd.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import li.songe.gkd.store.blockMatchAppListFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.util.AppListString

class EditBlockAppListVm : BaseViewModel() {

    val textFlow = MutableStateFlow(
        AppListString.encode(
            blockMatchAppListFlow.value,
            append = true,
        )
    )

    val indicatorTextFlow = textFlow.debounce(500).map {
        AppListString.decode(it).size.toString()
    }.stateInit("")

    fun getChangedSet(): Set<String>? {
        val newSet = AppListString.decode(textFlow.value)
        if (blockMatchAppListFlow.value != newSet) {
            return newSet
        }
        return null
    }

}