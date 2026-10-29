package li.gkd.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import li.gkd.app.ui.ImagePreviewPage
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.PrivilegeServicePage
import li.gkd.app.ui.WebViewPage
import li.gkd.app.ui.crash.CrashReportPage
import li.gkd.app.ui.home.ActionToastPage
import li.gkd.app.ui.home.BlockA11ySetupPage
import li.gkd.app.ui.home.HomePage
import li.gkd.app.ui.home.NotificationTextPage
import li.gkd.app.ui.log.A11yEventLogPage
import li.gkd.app.ui.log.ActivityLogPage
import li.gkd.app.ui.log.MatchDebugPage
import li.gkd.app.ui.settings.AboutPage
import li.gkd.app.ui.settings.AdvancedPage
import li.gkd.app.ui.settings.AppWhitelistPage
import li.gkd.app.ui.settings.DebugSettingsPage
import li.gkd.app.ui.settings.ExternalCallsPage
import li.gkd.app.ui.settings.KeepAlivePage
import li.gkd.app.ui.settings.QuickSettingsTilesPage
import li.gkd.app.ui.settings.ServiceScopeAppListPage
import li.gkd.app.ui.settings.ServiceScopeKind
import li.gkd.app.ui.settings.WorkModePage
import li.gkd.app.ui.snapshot.SnapshotPage
import li.gkd.app.ui.snapshot.SnapshotPreviewPage
import li.gkd.app.ui.snapshot.SnapshotSettingsPage
import li.gkd.app.ui.subscription.ActionLogPage
import li.gkd.app.ui.subscription.AppConfigPage
import li.gkd.app.ui.subscription.CategoryEditorPage
import li.gkd.app.ui.subscription.RuleExcludeEditorPage
import li.gkd.app.ui.subscription.SubsAppGroupListPage
import li.gkd.app.ui.subscription.SubsAppListPage
import li.gkd.app.ui.subscription.SubsCategoryGroupPage
import li.gkd.app.ui.subscription.SubsCategoryPage
import li.gkd.app.ui.subscription.SubsGlobalGroupExcludePage
import li.gkd.app.ui.subscription.SubsGlobalGroupListPage
import li.gkd.app.ui.subscription.UpsertRuleGroupPage

/** Shared production route registry for both hosts. */
@Composable
fun GkAppNavigation() {
    val mainVm = MainViewModel.requireCurrent()
    GkNavigation(mainVm.navigator.backStack, mainVm.navigator::pop) { entry ->
        val route = entry.route
        NavEntry(
            entry,
            contentKey = entry.id,
            metadata = route.transition.toMetadata(),
        ) {
            when (route) {
                is SubsAppListRoute -> SubsAppListPage(route)
                is SubsAppGroupListRoute -> SubsAppGroupListPage(route)
                is SubsGlobalGroupListRoute -> SubsGlobalGroupListPage(route)
                is SubsGlobalGroupExcludeRoute -> SubsGlobalGroupExcludePage(route)
                is SubsCategoryRoute -> SubsCategoryPage(route)
                is SubsCategoryGroupRoute -> SubsCategoryGroupPage(route)
                is RuleExcludeEditorRoute -> RuleExcludeEditorPage(route)
                is AppConfigRoute -> AppConfigPage(route)
                is ActionLogRoute -> ActionLogPage(route)
                is UpsertRuleGroupRoute -> UpsertRuleGroupPage(route)
                is CategoryEditorRoute -> CategoryEditorPage(route)
                is SnapshotPreviewRoute -> SnapshotPreviewPage(route)
                is WebViewRoute -> WebViewPage(route)
                is ImagePreviewRoute -> ImagePreviewPage(route)

                ActivityLogRoute -> ActivityLogPage()
                A11yEventLogRoute -> A11yEventLogPage()
                A11YScopeAppListRoute -> ServiceScopeAppListPage(ServiceScopeKind.Accessibility)
                AutomationScopeAppListRoute -> ServiceScopeAppListPage(ServiceScopeKind.Automation)
                BlockA11yAppListRoute -> ServiceScopeAppListPage(ServiceScopeKind.Disabled)
                EditBlockAppListRoute -> AppWhitelistPage()
                SnapshotPageRoute -> SnapshotPage()
                HomeRoute -> HomePage()
                ActionToastRoute -> ActionToastPage()
                NotificationTextRoute -> NotificationTextPage()
                WorkModeRoute -> WorkModePage()
                AboutRoute -> AboutPage()
                BlockA11ySetupRoute -> BlockA11ySetupPage()
                AdvancedPageRoute -> AdvancedPage(entry)
                DebugSettingsRoute -> DebugSettingsPage()
                KeepAliveRoute -> KeepAlivePage()
                MatchDebugRoute -> MatchDebugPage()
                ExternalCallsRoute -> ExternalCallsPage()
                QuickSettingsTilesRoute -> QuickSettingsTilesPage()
                PrivilegeServiceRoute -> PrivilegeServicePage()
                SnapshotSettingsRoute -> SnapshotSettingsPage(entry)
                CrashReportRoute -> CrashReportPage()
            }
        }
    }
}
