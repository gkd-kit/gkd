package li.gkd.app.ui.share

import androidx.navigation3.runtime.NavKey
import li.gkd.app.feature.subscription.CategoryEditorRoute
import li.gkd.app.feature.subscription.RuleExcludeEditorRoute
import li.gkd.app.feature.subscription.SubsAppGroupListRoute
import li.gkd.app.feature.subscription.SubsAppListRoute
import li.gkd.app.feature.subscription.SubsCategoryGroupRoute
import li.gkd.app.feature.subscription.SubsCategoryRoute
import li.gkd.app.feature.subscription.SubsGlobalGroupExcludeRoute
import li.gkd.app.feature.subscription.SubsGlobalGroupListRoute
import li.gkd.app.feature.subscription.UpsertRuleGroupRoute

sealed interface DeletionTarget {
    data class Subscription(val subsId: Long) : DeletionTarget
    data class Category(val subsId: Long, val categoryKey: Int) : DeletionTarget
    data class App(val subsId: Long, val appId: String) : DeletionTarget
    data class Group(val subsId: Long, val appId: String?, val groupKey: Int) : DeletionTarget

    fun owns(route: NavKey): Boolean {
        val routeSubsId = when (route) {
            is SubsAppListRoute -> route.subsItemId
            is SubsAppGroupListRoute -> route.subsItemId
            is SubsCategoryRoute -> route.subsItemId
            is SubsCategoryGroupRoute -> route.subsId
            is CategoryEditorRoute -> route.subsId
            is SubsGlobalGroupListRoute -> route.subsItemId
            is SubsGlobalGroupExcludeRoute -> route.subsItemId
            is RuleExcludeEditorRoute -> route.subsId
            is UpsertRuleGroupRoute -> route.subsId
            else -> return false
        }
        return when (this) {
            is Subscription -> subsId == routeSubsId
            is Category -> subsId == routeSubsId && when (route) {
                is SubsCategoryGroupRoute -> categoryKey == route.categoryKey
                is CategoryEditorRoute -> categoryKey == route.categoryKey
                else -> false
            }
            is App -> subsId == routeSubsId && when (route) {
                is SubsAppGroupListRoute -> appId == route.appId
                is RuleExcludeEditorRoute -> appId == route.appId
                is UpsertRuleGroupRoute -> appId == route.appId
                else -> false
            }
            is Group -> subsId == routeSubsId && when (route) {
                is SubsGlobalGroupExcludeRoute -> appId == null && groupKey == route.groupKey
                is RuleExcludeEditorRoute -> appId == route.appId && groupKey == route.groupKey
                is UpsertRuleGroupRoute -> appId == route.appId && groupKey == route.groupKey
                else -> false
            }
        }
    }
}
