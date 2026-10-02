package li.gkd.app.ui.home

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import li.gkd.app.app.AppCatalogState
import li.gkd.app.model.AppInfo
import li.gkd.app.rule.ResolvedAppGroup
import li.gkd.app.state.Loadable
import li.gkd.app.ui.component.GkRuleStatsData

object AppListSources {
    fun <T : Any> observe(
        catalog: Flow<AppCatalogState>,
        rules: Flow<Loadable<T>>,
        actionOrder: Flow<List<String>>,
        visits: Flow<List<String>>,
        globalGroupCounts: (T) -> Map<String, Int>,
        appGroups: (T) -> Map<String, List<ResolvedAppGroup>>,
    ): Flow<Loadable<AppListSource>> = combine(
        catalog,
        rules,
        loadOrder(actionOrder),
        loadOrder(visits)
    ) { state, loaded, order, visited ->
        val snapshot = state.snapshot
        when {
            snapshot == null && state.failure != null -> Loadable.Failure(state.failure)
            snapshot == null -> Loadable.Loading
            else -> Loadable.Ready(
                build(
                    snapshot.visibleApps, order.value.orEmpty(), visited.value.orEmpty(),
                    loaded.value?.let(globalGroupCounts), loaded.value?.let(appGroups).orEmpty(),
                    snapshot.canQueryPackages, snapshot.queryPackagesAbnormal, state.refreshing,
                ).copy(
                    actionOrderState = order.mapUnit(), visitOrderState = visited.mapUnit(),
                    ruleStatsFailed = loaded is Loadable.Failure,
                )
            )
        }
    }

    private fun loadOrder(source: Flow<List<String>>): Flow<Loadable<List<String>>> = source
        .map<List<String>, Loadable<List<String>>> { Loadable.Ready(it) }
        .onStart { emit(Loadable.Loading) }
        .catch { emit(Loadable.Failure(it)) }

    private fun Loadable<*>.mapUnit(): Loadable<Unit> = when (this) {
        Loadable.Loading -> Loadable.Loading
        is Loadable.Failure -> this
        is Loadable.Ready -> Loadable.Ready(Unit)
    }

    fun build(
        apps: List<AppInfo>,
        actionOrder: List<String>,
        visits: List<String>,
        globalGroupCounts: Map<String, Int>? = null,
        appGroups: Map<String, List<ResolvedAppGroup>> = emptyMap(),
        canQueryPackages: Boolean = true,
        queryPackagesAbnormal: Boolean = false,
        refreshing: Boolean = false,
    ) = AppListSource(
        apps = apps,
        ruleStats = if (globalGroupCounts == null) emptyMap() else apps.associate { info ->
            info.id to GkRuleStatsData(
                globalGroups = globalGroupCounts[info.id] ?: 0,
                appGroups = appGroups[info.id]?.count { it.enable } ?: 0,
                enabledOnly = true,
            )
        },
        actionOrder = actionOrder,
        visitOrder = visits.mapIndexed { index, id -> id to index }.toMap(),
        canQueryPackages = canQueryPackages,
        queryPackagesAbnormal = queryPackagesAbnormal,
        refreshing = refreshing,
    )
}
