package li.songe.gkd.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import li.songe.gkd.data.CategoryConfig
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.batchUpdateGroupEnable
import li.songe.gkd.ui.component.toGroupState
import li.songe.gkd.ui.component.updateRuleGroupEnable
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.ui.share.Loadable
import li.songe.gkd.util.toJson5String

data class SubsAppGroupConfigs(
    val subsConfigs: List<SubsConfig>,
    val categoryConfigs: List<CategoryConfig>,
)

data class SubsAppGroupListUiState(
    val subscription: RawSubscription,
    val app: RawSubscription.RawApp,
    val configs: Loadable<SubsAppGroupConfigs>,
)

class SubsAppGroupListVm(val route: SubsAppGroupListRoute) : BaseViewModel() {

    private val subscription = requiredSubscription(route.subsItemId)

    private val subsConfigsFlow =
        DbSet.subsConfigDao.queryAppGroupTypeConfig(route.subsItemId, route.appId)

    private val categoryConfigsFlow = DbSet.categoryConfigDao.queryConfig(route.subsItemId)

    val uiState = subscription.buildUiState(
        initialValue = { rawSubscription ->
            buildUiState(rawSubscription, Loadable.Loading)
        },
    ) { rawSubscription ->
        combine(subsConfigsFlow, categoryConfigsFlow) { configs, categoryConfigs ->
            buildUiState(
                rawSubscription = rawSubscription,
                configs = Loadable.Ready(
                    SubsAppGroupConfigs(
                        subsConfigs = configs,
                        categoryConfigs = categoryConfigs,
                    ),
                ),
            )
        }
    }

    private fun buildUiState(
        rawSubscription: RawSubscription,
        configs: Loadable<SubsAppGroupConfigs>,
    ) = SubsAppGroupListUiState(
        subscription = rawSubscription,
        app = rawSubscription.apps.find { it.id == route.appId }
            ?: error("订阅应用不存在: ${route.appId}"),
        configs = configs,
    )

    val focusGroupFlow: StateFlow<Triple<Long, String?, Int>?>?
        field = route.focusGroupKey?.let {
            MutableStateFlow<Triple<Long, String?, Int>?>(
                Triple(
                    route.subsItemId,
                    route.appId,
                    route.focusGroupKey,
                )
            )
        }

    fun consumeFocusGroup() {
        focusGroupFlow?.value = null
    }

    suspend fun buildSelectedGroupsText(selectedKeys: Set<Int>): String =
        withContext(Dispatchers.Default) {
            val app = uiState.value.value?.app ?: error("订阅应用尚未加载")
            toJson5String(
                app.copy(
                    groups = app.groups.filter { it.key in selectedKeys },
                ),
            )
        }

    suspend fun updateSelectedEnabled(selectedKeys: Set<Int>, enabled: Boolean?): Int {
        val app = uiState.value.value?.app ?: error("订阅应用尚未加载")
        val selectedGroups = app.groups
            .filter { it.key in selectedKeys }
            .map { it.toGroupState(route.subsItemId, route.appId) }
            .toSet()
        return batchUpdateGroupEnable(selectedGroups, enabled).size
    }

    suspend fun setGroupEnabled(
        group: RawSubscription.RawAppGroup,
        subsConfig: SubsConfig?,
        enabled: Boolean,
    ) {
        val rawSubscription = subscription.requireValue()
        updateRuleGroupEnable(rawSubscription, route.appId, group, subsConfig, enabled)
    }

    suspend fun deleteSelectedGroups(selectedKeys: Set<Int>): Int {
        var deletedSize = 0
        subscription.update { current ->
            val app = current.apps.find { it.id == route.appId }
                ?: return@update current
            val currentKeys = app.groups.mapTo(mutableSetOf()) { it.key }
            val keysToDelete = selectedKeys intersect currentKeys
            deletedSize = keysToDelete.size
            if (keysToDelete.isEmpty()) {
                current
            } else if (keysToDelete == currentKeys) {
                current.copy(
                    apps = current.apps.filter { it.id != route.appId },
                )
            } else {
                current.copy(
                    apps = current.apps.toMutableList().apply {
                        set(
                            indexOfFirst { it.id == route.appId },
                            app.copy(groups = app.groups.filterNot { it.key in keysToDelete }),
                        )
                    },
                )
            }
        }
        return deletedSize
    }
}
