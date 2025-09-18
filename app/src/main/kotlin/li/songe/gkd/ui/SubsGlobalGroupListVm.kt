package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import com.ramcosta.composedestinations.generated.destinations.SubsGlobalGroupListPageDestination
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.ShowGroupState
import li.songe.gkd.ui.share.BaseViewModel

class SubsGlobalGroupListVm(stateHandle: SavedStateHandle) : BaseViewModel() {
    private val args = SubsGlobalGroupListPageDestination.argsFrom(stateHandle)
    val subsRawFlow = mapSafeSubs(args.subsItemId)

    val subsConfigsFlow = DbSet.subsConfigDao.queryGlobalGroupTypeConfig(args.subsItemId)
        .stateInit(emptyList())

    val isSelectedModeFlow = MutableStateFlow(false)
    val selectedDataSetFlow = MutableStateFlow(emptySet<ShowGroupState>())
    val focusGroupFlow = args.focusGroupKey?.let {
        MutableStateFlow<Triple<Long, String?, Int>?>(
            Triple(
                args.subsItemId,
                null,
                args.focusGroupKey
            )
        )
    }
}