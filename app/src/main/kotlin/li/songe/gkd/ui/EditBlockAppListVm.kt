package li.songe.gkd.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import li.songe.gkd.store.blockMatchAppListFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.util.AppListString

class EditBlockAppListVm : BaseViewModel() {

    val textFlow: StateFlow<String>
        field = MutableStateFlow(
            AppListString.encode(
                blockMatchAppListFlow.value,
                append = true,
            )
        )

    val indicatorSizeFlow = textFlow.debounce(500).map {
        AppListString.decode(it).size
    }.stateInit(AppListString.decode(textFlow.value).size)

    fun getChangedSet(): Set<String>? {
        val newSet = AppListString.decode(textFlow.value)
        if (blockMatchAppListFlow.value != newSet) {
            return newSet
        }
        return null
    }

    fun setText(value: String) {
        textFlow.value = value
    }

}
