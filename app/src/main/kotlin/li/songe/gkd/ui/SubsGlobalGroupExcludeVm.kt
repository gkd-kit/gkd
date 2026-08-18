package li.songe.gkd.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import li.songe.gkd.MainViewModel
import li.songe.gkd.data.AppInfo
import li.songe.gkd.data.ExcludeData
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.share.BaseViewModel
import li.songe.gkd.ui.share.Loadable
import li.songe.gkd.ui.share.globalGroupAppOrderListState
import li.songe.gkd.ui.share.useAppFilter
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.findOption

data class SubsGlobalGroupExcludeConfig(
    val subsConfig: SubsConfig?,
    val excludeData: ExcludeData,
)

data class SubsGlobalGroupExcludeUiState(
    val subscription: RawSubscription,
    val group: RawSubscription.RawGlobalGroup,
    val config: Loadable<SubsGlobalGroupExcludeConfig>,
    val showAppInfos: List<AppInfo>,
    val showAllApps: Boolean,
)

class SubsGlobalGroupExcludeVm(
    val route: SubsGlobalGroupExcludeRoute,
    mainVm: MainViewModel,
) : BaseViewModel() {

    private val subscription = requiredSubscription(route.subsItemId)
    private val subsConfigFlow = DbSet.subsConfigDao
        .queryGlobalGroupTypeConfig(route.subsItemId, route.groupKey)

    private val appFilter = useAppFilter(
        mainVm = mainVm,
        appGroupType = { it.subsExcludeAppGroupType },
        sortType = { AppSortOption.objects.findOption(it.subsExcludeSort) },
        showBlockApps = { it.subsExcludeShowBlockApp },
        appOrderListState = globalGroupAppOrderListState(
            route.subsItemId,
            route.groupKey,
        ),
    )
    val searchStrFlow: StateFlow<String>
        field = appFilter.searchStrFlow
    val showSearchBarFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val uiState = subscription.buildUiState(
        initialValue = ::buildCurrentUiState,
    ) { rawSubscription ->
        val group = rawSubscription.globalGroups.find { it.key == route.groupKey }
            ?: error("全局规则不存在: ${route.groupKey}")
        val configState = subsConfigFlow.map { config ->
            Loadable.Ready(
                SubsGlobalGroupExcludeConfig(
                    subsConfig = config,
                    excludeData = ExcludeData.parse(config?.exclude),
                ),
            )
        }
        val showAppInfosFlow = combine(
            appFilter.appListFlow,
            storeFlow,
        ) { apps, store ->
            filterInnerDisabledApps(
                rawSubscription = rawSubscription,
                group = group,
                apps = apps,
                showInnerDisabledApps = store.subsExcludeShowInnerDisabledApp,
            )
        }
        combine(
            configState,
            showAppInfosFlow,
            appFilter.showAllAppFlow,
        ) { config, showAppInfos, showAllApps ->
            buildUiState(
                rawSubscription = rawSubscription,
                config = config,
                showAppInfos = showAppInfos,
                showAllApps = showAllApps,
            )
        }
    }

    private fun buildCurrentUiState(
        rawSubscription: RawSubscription,
    ): SubsGlobalGroupExcludeUiState {
        val group = rawSubscription.globalGroups.find { it.key == route.groupKey }
            ?: error("全局规则不存在: ${route.groupKey}")
        return buildUiState(
            rawSubscription = rawSubscription,
            config = Loadable.Loading,
            showAppInfos = filterInnerDisabledApps(
                rawSubscription = rawSubscription,
                group = group,
                apps = appFilter.appListFlow.value,
                showInnerDisabledApps = storeFlow.value.subsExcludeShowInnerDisabledApp,
            ),
            showAllApps = appFilter.showAllAppFlow.value,
        )
    }

    private fun buildUiState(
        rawSubscription: RawSubscription,
        config: Loadable<SubsGlobalGroupExcludeConfig>,
        showAppInfos: List<AppInfo>,
        showAllApps: Boolean,
    ) = SubsGlobalGroupExcludeUiState(
        subscription = rawSubscription,
        group = rawSubscription.globalGroups.find { it.key == route.groupKey }
            ?: error("全局规则不存在: ${route.groupKey}"),
        config = config,
        showAppInfos = showAppInfos,
        showAllApps = showAllApps,
    )

    private fun filterInnerDisabledApps(
        rawSubscription: RawSubscription,
        group: RawSubscription.RawGlobalGroup,
        apps: List<AppInfo>,
        showInnerDisabledApps: Boolean,
    ): List<AppInfo> = if (showInnerDisabledApps) {
        apps
    } else {
        apps.filterNot { appInfo ->
            rawSubscription.getGlobalGroupInnerDisabled(group, appInfo.id)
        }
    }

    val excludeTextFlow: StateFlow<String>
        field = MutableStateFlow("")
    val editableFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    private val changedValue: ExcludeData?
        get() {
            val currentExclude = uiState.value.value?.config?.value?.excludeData
                ?: return null
            val newExclude = ExcludeData.parse(excludeTextFlow.value)
            return if (newExclude != currentExclude) {
                newExclude
            } else {
                null
            }
        }

    val hasUnsavedChanges: Boolean
        get() = changedValue != null

    fun setSearchText(value: String) {
        searchStrFlow.value = value
    }

    fun setSearchBarVisible(visible: Boolean) {
        showSearchBarFlow.value = visible
    }

    fun setEditable(value: Boolean) {
        if (value && !editableFlow.value) {
            val excludeData = uiState.value.value?.config?.value?.excludeData ?: return
            excludeTextFlow.value = excludeData.stringify()
        }
        editableFlow.value = value
    }

    fun setExcludeText(value: String) {
        excludeTextFlow.value = value
    }

    fun setSortType(value: AppSortOption) {
        storeFlow.update { it.copy(subsExcludeSort = value.value) }
    }

    fun setAppGroupType(value: Int) {
        storeFlow.update { it.copy(subsExcludeAppGroupType = value) }
    }

    fun toggleShowInnerDisabledApps() {
        storeFlow.update {
            it.copy(subsExcludeShowInnerDisabledApp = !it.subsExcludeShowInnerDisabledApp)
        }
    }

    fun toggleShowBlockApps() {
        storeFlow.update {
            it.copy(subsExcludeShowBlockApp = !it.subsExcludeShowBlockApp)
        }
    }

    suspend fun saveExcludeText(): Boolean {
        val newExclude = changedValue ?: return false
        val currentConfig = uiState.value.value?.config?.value
            ?: error("订阅配置尚未加载")
        val subsConfig = (currentConfig.subsConfig ?: SubsConfig(
            type = SubsConfig.GlobalGroupType,
            subsId = route.subsItemId,
            groupKey = route.groupKey,
        )).copy(exclude = newExclude.stringify())
        DbSet.subsConfigDao.insert(subsConfig)
        return true
    }

    suspend fun setAppChecked(appId: String, checked: Boolean) {
        val state = uiState.value.value ?: error("订阅尚未加载")
        val config = state.config.value ?: error("订阅配置尚未加载")
        val excludeData = config.excludeData
        val subsConfig = (config.subsConfig ?: SubsConfig(
            type = SubsConfig.GlobalGroupType,
            subsId = route.subsItemId,
            groupKey = route.groupKey,
        )).copy(
            exclude = excludeData.copy(
                appIds = excludeData.appIds.toMutableMap().apply {
                    set(appId, !checked)
                },
            ).stringify(),
        )
        DbSet.subsConfigDao.insert(subsConfig)
    }

}
