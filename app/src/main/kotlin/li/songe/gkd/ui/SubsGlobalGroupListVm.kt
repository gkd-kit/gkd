package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramcosta.composedestinations.generated.destinations.SubsGlobalGroupListPageDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.RuleGroupExtVm
import li.songe.gkd.ui.component.ShowGroupState
import li.songe.gkd.util.map
import li.songe.gkd.util.subsIdToRawFlow

class SubsGlobalGroupListVm(stateHandle: SavedStateHandle) : ViewModel(), RuleGroupExtVm {
    private val args = SubsGlobalGroupListPageDestination.argsFrom(stateHandle)
    val subsRawFlow = subsIdToRawFlow.map(viewModelScope) { s -> s[args.subsItemId] }

    val subsConfigsFlow = DbSet.subsConfigDao.queryGlobalGroupTypeConfig(args.subsItemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isSelectedModeFlow = MutableStateFlow(false)
    val selectedDataSetFlow = MutableStateFlow(emptySet<ShowGroupState>())

    override val focusGroupKeyFlow = MutableStateFlow<Int?>(args.focusGroupKey)
}