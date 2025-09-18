package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import com.ramcosta.composedestinations.generated.destinations.SubsAppGroupListPageDestination
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.ShowGroupState
import li.songe.gkd.ui.share.BaseViewModel

class SubsAppGroupListVm(stateHandle: SavedStateHandle) : BaseViewModel() {
    private val args = SubsAppGroupListPageDestination.argsFrom(stateHandle)

    val subsFlow = mapSafeSubs(args.subsItemId)
    val subsAppFlow = subsFlow.mapNew { it.getApp(args.appId) }

    val subsConfigsFlow = DbSet.subsConfigDao.queryAppGroupTypeConfig(args.subsItemId, args.appId)
        .stateInit(emptyList())

    val categoryConfigsFlow = DbSet.categoryConfigDao.queryConfig(args.subsItemId)
        .stateInit(emptyList())


    val isSelectedModeFlow = MutableStateFlow(false)
    val selectedDataSetFlow = MutableStateFlow(emptySet<ShowGroupState>())

    val focusGroupFlow = args.focusGroupKey?.let {
        MutableStateFlow<Triple<Long, String?, Int>?>(
            Triple(
                args.subsItemId,
                args.appId,
                args.focusGroupKey
            )
        )
    }
}