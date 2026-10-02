package li.gkd.app.ui.home

import li.gkd.app.model.AppInfo
import li.gkd.app.state.Loadable
import li.gkd.app.ui.component.GkRuleStatsData

data class AppListSource(
    val apps: List<AppInfo>,
    val ruleStats: Map<String, GkRuleStatsData>,
    val actionOrder: List<String>,
    val visitOrder: Map<String, Int>,
    val canQueryPackages: Boolean = true,
    val queryPackagesAbnormal: Boolean = false,
    val refreshing: Boolean = false,
    val actionOrderState: Loadable<Unit> = Loadable.Ready(Unit),
    val visitOrderState: Loadable<Unit> = Loadable.Ready(Unit),
    val ruleStatsFailed: Boolean = false,
)

