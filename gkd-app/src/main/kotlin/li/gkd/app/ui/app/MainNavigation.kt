package li.gkd.app.ui.app

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import li.gkd.app.ui.A11YScopeAppListRoute
import li.gkd.app.ui.A11yEventLogPage
import li.gkd.app.ui.A11yEventLogRoute
import li.gkd.app.ui.A11yScopeAppListPage
import li.gkd.app.ui.AboutPage
import li.gkd.app.ui.AboutRoute
import li.gkd.app.ui.ActionLogPage
import li.gkd.app.ui.ActionLogRoute
import li.gkd.app.ui.ActivityLogPage
import li.gkd.app.ui.ActivityLogRoute
import li.gkd.app.ui.AdvancedPage
import li.gkd.app.ui.AdvancedPageRoute
import li.gkd.app.ui.AppConfigPage
import li.gkd.app.ui.AppConfigRoute
import li.gkd.app.ui.BlockA11yAppListPage
import li.gkd.app.ui.BlockA11yAppListRoute
import li.gkd.app.ui.CrashReportPage
import li.gkd.app.ui.CrashReportRoute
import li.gkd.app.ui.EditBlockAppListPage
import li.gkd.app.ui.EditBlockAppListRoute
import li.gkd.app.ui.ImagePreviewPage
import li.gkd.app.ui.ImagePreviewRoute
import li.gkd.app.ui.PrivilegeServicePage
import li.gkd.app.ui.PrivilegeServiceRoute
import li.gkd.app.ui.SlowGroupPage
import li.gkd.app.ui.SlowGroupRoute
import li.gkd.app.ui.SnapshotPage
import li.gkd.app.ui.SnapshotPageRoute
import li.gkd.app.ui.SnapshotSettingsPage
import li.gkd.app.ui.SnapshotSettingsRoute
import li.gkd.app.ui.SubsAppGroupListPage
import li.gkd.app.ui.SubsAppGroupListRoute
import li.gkd.app.ui.SubsAppListPage
import li.gkd.app.ui.SubsAppListRoute
import li.gkd.app.ui.SubsCategoryGroupPage
import li.gkd.app.ui.SubsCategoryGroupRoute
import li.gkd.app.ui.SubsCategoryPage
import li.gkd.app.ui.SubsCategoryRoute
import li.gkd.app.ui.SubsGlobalGroupExcludePage
import li.gkd.app.ui.SubsGlobalGroupExcludeRoute
import li.gkd.app.ui.SubsGlobalGroupListPage
import li.gkd.app.ui.SubsGlobalGroupListRoute
import li.gkd.app.ui.UpsertRuleGroupPage
import li.gkd.app.ui.UpsertRuleGroupRoute
import li.gkd.app.ui.WebViewPage
import li.gkd.app.ui.WebViewRoute
import li.gkd.app.ui.WorkModePage
import li.gkd.app.ui.WorkModeRoute
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.home.HomePage
import li.gkd.app.ui.home.HomeRoute

private val mainRouteEntryProvider = entryProvider {
    entry<HomeRoute> { HomePage() }
    entry<WorkModeRoute> { WorkModePage() }
    entry<AboutRoute> { AboutPage() }
    entry<BlockA11yAppListRoute> { BlockA11yAppListPage() }
    entry<AdvancedPageRoute> { AdvancedPage() }
    entry<PrivilegeServiceRoute> { PrivilegeServicePage() }
    entry<SnapshotPageRoute> { SnapshotPage() }
    entry<SnapshotSettingsRoute> { SnapshotSettingsPage() }
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
fun MainNavigation() {
    val mainVm = LocalMainViewModel.current
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
