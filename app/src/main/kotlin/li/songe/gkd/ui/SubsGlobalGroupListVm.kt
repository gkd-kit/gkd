package li.songe.gkd.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.batchUpdateGroupEnable
import li.songe.gkd.ui.component.toGroupState
import li.songe.gkd.ui.component.updateRuleGroupEnable
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.ui.share.Loadable

data class SubsGlobalGroupListUiState(
    val subscription: RawSubscription,
    val subsConfigs: Loadable<List<SubsConfig>>,
)

class SubsGlobalGroupListVm(val route: SubsGlobalGroupListRoute) : BaseViewModel() {
    private val subscription = requiredSubscription(route.subsItemId)

    private val subsConfigsFlow =
        DbSet.subsConfigDao.queryGlobalGroupTypeConfig(route.subsItemId)

    val uiState = subscription.buildUiState(
        initialValue = { rawSubscription ->
            buildUiState(rawSubscription, Loadable.Loading)
        },
    ) { rawSubscription ->
        subsConfigsFlow.map { configs ->
            buildUiState(rawSubscription, Loadable.Ready(configs))
        }
    }

    private fun buildUiState(
        rawSubscription: RawSubscription,
        subsConfigs: Loadable<List<SubsConfig>>,
    ) = SubsGlobalGroupListUiState(
        subscription = rawSubscription,
        subsConfigs = subsConfigs,
    )

    val focusGroupFlow: StateFlow<Triple<Long, String?, Int>?>?
        field = route.focusGroupKey?.let {
            MutableStateFlow<Triple<Long, String?, Int>?>(
                Triple(
                    route.subsItemId,
                    null,
                    route.focusGroupKey,
                )
            )
        }

    fun consumeFocusGroup() {
        focusGroupFlow?.value = null
    }

    suspend fun updateSelectedEnabled(selectedKeys: Set<Int>, enabled: Boolean?): Int {
        val rawSubscription = subscription.requireValue()
        val selectedGroups = rawSubscription.globalGroups
            .filter { it.key in selectedKeys }
            .map { it.toGroupState(route.subsItemId) }
            .toSet()
        return batchUpdateGroupEnable(selectedGroups, enabled).size
    }

    suspend fun setGroupEnabled(
        group: RawSubscription.RawGlobalGroup,
        subsConfig: SubsConfig?,
        enabled: Boolean,
    ) {
        val rawSubscription = subscription.requireValue()
        updateRuleGroupEnable(rawSubscription, null, group, subsConfig, enabled)
    }

    suspend fun deleteSelectedGroups(selectedKeys: Set<Int>) {
        subscription.update { current ->
            current.copy(globalGroups = current.globalGroups.filterNot {
                it.key in selectedKeys
            })
        }
        DbSet.subsConfigDao.batchDeleteGlobalGroupConfig(route.subsItemId, selectedKeys.toList())
    }
}
