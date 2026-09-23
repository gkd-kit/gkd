package li.gkd.app.ui.app

import li.gkd.app.MainViewModel

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import li.gkd.app.feature.subscription.CategoryEditorPage
import li.gkd.app.feature.subscription.CategoryEditorRoute
import li.gkd.app.feature.subscription.RuleExcludeEditorPage
import li.gkd.app.feature.subscription.RuleExcludeEditorRoute
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
import li.gkd.app.feature.snapshot.SnapshotPage
import li.gkd.app.feature.snapshot.SnapshotPageRoute
import li.gkd.app.feature.snapshot.SnapshotSettingsPage
import li.gkd.app.feature.snapshot.SnapshotSettingsRoute
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
import li.gkd.app.ui.home.HomePage
import li.gkd.app.ui.home.BlockA11ySetupPage
import li.gkd.app.ui.home.ActionToastPage
import li.gkd.app.ui.home.ActionToastRoute
import li.gkd.app.ui.home.NotificationTextPage
import li.gkd.app.ui.home.NotificationTextRoute
import li.gkd.app.ui.home.BlockA11ySetupRoute
import li.gkd.app.ui.home.HomeRoute

private val editorTransitions = NavDisplay.transitionSpec {
    (slideInVertically(tween(250)) { it / 8 } + fadeIn(tween(250))) togetherWith fadeOut(tween(150))
} + NavDisplay.popTransitionSpec {
    fadeIn(tween(150)) togetherWith (slideOutVertically(tween(250)) { it / 8 } + fadeOut(tween(250)))
} + NavDisplay.predictivePopTransitionSpec {
    fadeIn(tween(150)) togetherWith (slideOutVertically(tween(250)) { it / 8 } + fadeOut(tween(250)))
}

private val mainRouteEntryProvider = entryProvider {
    entry<HomeRoute> { HomePage() }
    entry<WorkModeRoute> { WorkModePage() }
    entry<AboutRoute> { AboutPage() }
    entry<ActionToastRoute>(metadata = editorTransitions) { ActionToastPage() }
    entry<NotificationTextRoute>(metadata = editorTransitions) { NotificationTextPage() }
    entry<BlockA11ySetupRoute>(metadata = editorTransitions) { BlockA11ySetupPage() }
    entry<BlockA11yAppListRoute> { BlockA11yAppListPage() }
    entry<AdvancedPageRoute> { AdvancedPage() }
    entry<PrivilegeServiceRoute> { PrivilegeServicePage() }
    entry<SnapshotPageRoute> { SnapshotPage() }
    entry<SnapshotSettingsRoute> { SnapshotSettingsPage() }
    entry<A11YScopeAppListRoute> { A11yScopeAppListPage() }
    entry<ActivityLogRoute> { ActivityLogPage() }
    entry<A11yEventLogRoute> { A11yEventLogPage() }
    entry<EditBlockAppListRoute>(metadata = editorTransitions) { EditBlockAppListPage() }
    entry<SubsAppListRoute> { SubsAppListPage(it) }
    entry<WebViewRoute> { WebViewPage(it) }
    entry<SubsCategoryRoute> { SubsCategoryPage(it) }
    entry<SubsGlobalGroupListRoute> { SubsGlobalGroupListPage(it) }
    entry<SubsGlobalGroupExcludeRoute> { SubsGlobalGroupExcludePage(it) }
    entry<ActionLogRoute> { ActionLogPage(it) }
    entry<ImagePreviewRoute> { ImagePreviewPage(it) }
    entry<UpsertRuleGroupRoute>(metadata = editorTransitions) { UpsertRuleGroupPage(it) }
    entry<CategoryEditorRoute>(metadata = editorTransitions) { CategoryEditorPage(it) }
    entry<RuleExcludeEditorRoute>(metadata = editorTransitions) { RuleExcludeEditorPage(it) }
    entry<SubsAppGroupListRoute> { SubsAppGroupListPage(it) }
    entry<AppConfigRoute> { AppConfigPage(it) }
    entry<CrashReportRoute> { CrashReportPage() }
    entry<SubsCategoryGroupRoute> { SubsCategoryGroupPage(it) }
}

@Composable
fun MainNavigation() {
    val mainVm = MainViewModel.requireCurrent()
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
