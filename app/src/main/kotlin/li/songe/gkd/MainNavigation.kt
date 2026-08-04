package li.songe.gkd

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import li.songe.gkd.ui.A11YScopeAppListRoute
import li.songe.gkd.ui.A11yEventLogPage
import li.songe.gkd.ui.A11yEventLogRoute
import li.songe.gkd.ui.A11yScopeAppListPage
import li.songe.gkd.ui.AboutPage
import li.songe.gkd.ui.AboutRoute
import li.songe.gkd.ui.ActionLogPage
import li.songe.gkd.ui.ActionLogRoute
import li.songe.gkd.ui.ActivityLogPage
import li.songe.gkd.ui.ActivityLogRoute
import li.songe.gkd.ui.AdvancedPage
import li.songe.gkd.ui.AdvancedPageRoute
import li.songe.gkd.ui.AppConfigPage
import li.songe.gkd.ui.AppConfigRoute
import li.songe.gkd.ui.AppOpsAllowPage
import li.songe.gkd.ui.AppOpsAllowRoute
import li.songe.gkd.ui.AuthA11yPage
import li.songe.gkd.ui.AuthA11yRoute
import li.songe.gkd.ui.BlockA11yAppListPage
import li.songe.gkd.ui.BlockA11yAppListRoute
import li.songe.gkd.ui.CrashReportPage
import li.songe.gkd.ui.CrashReportRoute
import li.songe.gkd.ui.EditBlockAppListPage
import li.songe.gkd.ui.EditBlockAppListRoute
import li.songe.gkd.ui.ImagePreviewPage
import li.songe.gkd.ui.ImagePreviewRoute
import li.songe.gkd.ui.PrivilegePage
import li.songe.gkd.ui.PrivilegePageRoute
import li.songe.gkd.ui.SlowGroupPage
import li.songe.gkd.ui.SlowGroupRoute
import li.songe.gkd.ui.SnapshotPage
import li.songe.gkd.ui.SnapshotPageRoute
import li.songe.gkd.ui.SubsAppGroupListPage
import li.songe.gkd.ui.SubsAppGroupListRoute
import li.songe.gkd.ui.SubsAppListPage
import li.songe.gkd.ui.SubsAppListRoute
import li.songe.gkd.ui.SubsCategoryGroupPage
import li.songe.gkd.ui.SubsCategoryGroupRoute
import li.songe.gkd.ui.SubsCategoryPage
import li.songe.gkd.ui.SubsCategoryRoute
import li.songe.gkd.ui.SubsGlobalGroupExcludePage
import li.songe.gkd.ui.SubsGlobalGroupExcludeRoute
import li.songe.gkd.ui.SubsGlobalGroupListPage
import li.songe.gkd.ui.SubsGlobalGroupListRoute
import li.songe.gkd.ui.UpsertRuleGroupPage
import li.songe.gkd.ui.UpsertRuleGroupRoute
import li.songe.gkd.ui.WebViewPage
import li.songe.gkd.ui.WebViewRoute
import li.songe.gkd.ui.home.HomePage
import li.songe.gkd.ui.home.HomeRoute

private val mainRouteEntryProvider = entryProvider {
    entry<HomeRoute> { HomePage() }
    entry<AuthA11yRoute> { AuthA11yPage() }
    entry<AboutRoute> { AboutPage() }
    entry<BlockA11yAppListRoute> { BlockA11yAppListPage() }
    entry<AdvancedPageRoute> { AdvancedPage() }
    entry<PrivilegePageRoute> { PrivilegePage() }
    entry<SnapshotPageRoute> { SnapshotPage() }
    entry<AppOpsAllowRoute> { AppOpsAllowPage() }
    entry<A11YScopeAppListRoute> { A11yScopeAppListPage() }
    entry<ActivityLogRoute> { ActivityLogPage() }
    entry<A11yEventLogRoute> { A11yEventLogPage() }
    entry<EditBlockAppListRoute> { EditBlockAppListPage() }
    entry<SlowGroupRoute> { SlowGroupPage() }
    entry<SubsAppListRoute> { SubsAppListPage(it) }
    entry<WebViewRoute> { WebViewPage(it) }
    entry<SubsCategoryRoute> { SubsCategoryPage(it) }
    entry<SubsGlobalGroupListRoute> { SubsGlobalGroupListPage(it) }
    entry<SubsGlobalGroupExcludeRoute> { SubsGlobalGroupExcludePage(it) }
    entry<ActionLogRoute> { ActionLogPage(it) }
    entry<ImagePreviewRoute> { ImagePreviewPage(it) }
    entry<UpsertRuleGroupRoute> { UpsertRuleGroupPage(it) }
    entry<SubsAppGroupListRoute> { SubsAppGroupListPage(it) }
    entry<AppConfigRoute> { AppConfigPage(it) }
    entry<CrashReportRoute> { CrashReportPage() }
    entry<SubsCategoryGroupRoute> { SubsCategoryGroupPage(it) }
}

@Composable
fun MainNavigation(mainVm: MainViewModel) {
    NavDisplay(
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        backStack = mainVm.backStack,
        onBack = mainVm::popPage,
        entryProvider = mainRouteEntryProvider,
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
        },
    )
}
