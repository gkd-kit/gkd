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
import li.gkd.app.feature.log.A11yEventLogPage
import li.gkd.app.feature.log.A11yEventLogRoute
import li.gkd.app.ui.A11yScopeAppListPage
import li.gkd.app.feature.settings.AboutPage
import li.gkd.app.feature.settings.AboutRoute
import li.gkd.app.feature.log.ActionLogPage
import li.gkd.app.feature.log.ActionLogRoute
import li.gkd.app.feature.log.ActivityLogPage
import li.gkd.app.feature.log.ActivityLogRoute
import li.gkd.app.feature.settings.AdvancedPage
import li.gkd.app.feature.settings.AdvancedPageRoute
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
import li.gkd.app.feature.snapshot.SnapshotPage
import li.gkd.app.feature.snapshot.SnapshotPageRoute
import li.gkd.app.feature.snapshot.SnapshotSettingsPage
import li.gkd.app.feature.snapshot.SnapshotSettingsRoute
import li.gkd.app.feature.snapshot.SnapshotInspectPage
import li.gkd.app.feature.snapshot.SnapshotInspectRoute
import li.gkd.app.feature.subscription.SubsAppGroupListPage
import li.gkd.app.feature.subscription.SubsAppGroupListRoute
import li.gkd.app.feature.subscription.SubsAppListPage
import li.gkd.app.feature.subscription.SubsAppListRoute
import li.gkd.app.feature.subscription.SubsCategoryGroupPage
import li.gkd.app.feature.subscription.SubsCategoryGroupRoute
import li.gkd.app.feature.subscription.SubsCategoryPage
import li.gkd.app.feature.subscription.SubsCategoryRoute
import li.gkd.app.feature.subscription.SubsGlobalGroupExcludePage
import li.gkd.app.feature.subscription.SubsGlobalGroupExcludeRoute
import li.gkd.app.feature.subscription.SubsGlobalGroupListPage
import li.gkd.app.feature.subscription.SubsGlobalGroupListRoute
import li.gkd.app.feature.subscription.UpsertRuleGroupPage
import li.gkd.app.feature.subscription.UpsertRuleGroupRoute
import li.gkd.app.ui.WebViewPage
import li.gkd.app.ui.WebViewRoute
import li.gkd.app.feature.settings.WorkModePage
import li.gkd.app.feature.settings.WorkModeRoute
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
    entry<SnapshotInspectRoute> { SnapshotInspectPage(it) }
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
