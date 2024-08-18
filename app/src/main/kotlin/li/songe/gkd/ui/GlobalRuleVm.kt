package li.songe.gkd.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.destinations.GlobalRulePageDestination
import li.songe.gkd.util.map
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow

class GlobalRuleVm (stateHandle: SavedStateHandle) : ViewModel() {
    private val args = GlobalRulePageDestination.argsFrom(stateHandle)
    val subsItemFlow =
        subsItemsFlow.map(viewModelScope) { s -> s.find { v -> v.id == args.subsItemId } }
    val subsRawFlow = subsIdToRawFlow.map(viewModelScope) { s -> s[args.subsItemId] }

    val subsConfigsFlow = DbSet.subsConfigDao.queryGlobalGroupTypeConfig(args.subsItemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

}