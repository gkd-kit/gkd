package li.gkd.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import li.gkd.app.store.blockMatchAppListFlow
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.AppListString

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
