package li.gkd.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.model.date
import li.gkd.app.rule.RuleGroupTarget
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.home.HomeViewModel
import li.gkd.app.ui.log.A11yEventLogPage
import li.gkd.app.ui.log.ActivityLogPage
import li.gkd.app.ui.settings.AppWhitelistPage
import li.gkd.app.ui.settings.ScopeAppPage
import li.gkd.app.ui.snapshot.SnapshotActionCoordinator
import li.gkd.app.ui.snapshot.snapshotPlatformActions
import li.gkd.app.ui.subscription.ActionLogPage
import li.gkd.app.ui.subscription.AppConfigPage
import li.gkd.app.ui.subscription.CategoryEditorPage
import li.gkd.app.ui.subscription.RuleExcludeEditorPage
import li.gkd.app.ui.subscription.RuleGroupState
import li.gkd.app.ui.subscription.SubsAppGroupListPage
import li.gkd.app.ui.subscription.SubsAppListPage
import li.gkd.app.ui.subscription.SubsCategoryGroupPage
import li.gkd.app.ui.subscription.SubsCategoryPage
import li.gkd.app.ui.subscription.SubsGlobalGroupExcludePage
import li.gkd.app.ui.subscription.SubsGlobalGroupListPage
import li.gkd.app.ui.subscription.SubsLinkDialogState
import li.gkd.app.ui.subscription.SubsSheetState
import li.gkd.app.ui.subscription.UpsertRuleGroupPage
import li.gkd.app.ui.upload.GithubUploadState
import li.gkd.app.ui.upload.createSnapshotUploadItem

/** One production route registry; pages receive only the events they consume. */
@Composable
fun GkAppNavigation(
    backStack: List<NavKey>,
    onBack: () -> Unit,
    window: AppWindow,
    homeNavigation: li.gkd.app.ui.home.HomeNavigation,
    subsSheet: SubsSheetState,
    subsLinks: SubsLinkDialogState,
    onNavigate: (AppRoute) -> Unit,
    replaceRoute: (AppRoute) -> Unit,
    showToast: (String) -> Unit,
    showText: (String) -> Unit,
    topRoute: () -> NavKey,
    dialogs: DialogRequests,
    ruleGroups: RuleGroupState,
    githubUpload: GithubUploadState,
    confirmDelete: ConfirmDeletion,
    scope: CoroutineScope,
    updateStatus: li.gkd.app.ui.update.UpdateStatus?,
    onExportLogs: () -> Unit,
    takeCrashDataList: () -> List<li.gkd.app.crash.CrashData>,
    browsersRunning: () -> Boolean = { true },
    onBrowserKey: (Int) -> Unit = {},
    entryContainer: @Composable (NavKey, @Composable () -> Unit) -> Unit = { _, content -> content() },
    diagnosticContent: @Composable (NavKey) -> Unit = { error("Unknown route: $it") },
) {
    val copyText: (String) -> Unit = { copyText(it, showToast) }
    val hideIme = window::hideIme
    val imageLoader = window.imageLoader()
    val appIcon: @Composable (String, Dp) -> Unit = { id, size -> window.GkAppIcon(id, size) }
    val systemBars: @Composable (Boolean) -> Unit = { window.GkSystemBars(it) }
    val editorFrame: EditorFrame = { editor, onSaved, content ->
        GkEditor(editor, onBack, topRoute, hideIme, scope, onSaved, content)
    }
    val showRuleGroup: ShowRuleGroup = { subscriptionId, appId, group, pageAppId ->
        scope.launch {
            withContext(Dispatchers.Default) { group.cacheStr }
            ruleGroups.showGroup(
                if (group is li.gkd.app.subscription.RawSubscription.RawGlobalGroup) {
                    RuleGroupTarget.Global(subscriptionId, group.key, pageAppId)
                } else {
                    RuleGroupTarget.App(subscriptionId, requireNotNull(appId), group.key)
                },
            )
        }
    }
    val snapshotActions: SnapshotActionFactory = { vm, onReplaced, beforeDelete, deleteFailed ->
        SnapshotActionCoordinator(
            platform = window.snapshotPlatformActions(),
            scope = scope,
            dialogs = dialogs,
            toast = showToast,
            startUpload = { githubUpload.startTask(createSnapshotUploadItem(it, it.appId)) },
            fileScope = vm.scope,
            onReplaced = onReplaced,
            onBeforeDelete = beforeDelete,
            onDeleteFailed = deleteFailed,
        )
    }
    val uploadSnapshots: (List<li.gkd.db.Snapshot>, () -> Unit) -> Boolean? =
        { snapshots, onFinished ->
            githubUpload.startBatchTask(snapshots.map {
                createSnapshotUploadItem(it, it.appId + " · " + it.date)
            }, onFinished)
        }
    GkNavigation(backStack, onBack) { route ->
        NavEntry(
            route, metadata = when (route) {
                ActionToastRoute, NotificationTextRoute, EditBlockAppListRoute,
                BlockA11ySetupRoute, is UpsertRuleGroupRoute, is CategoryEditorRoute,
                is RuleExcludeEditorRoute -> GkNavigationTransitions.editor

                else -> emptyMap()
            }
        ) {
            entryContainer(route) {
                if (route is AppRoute) {
                    when (route) {
                        is SubsAppListRoute -> SubsAppListPage(
                            route,
                            onBack,
                            onNavigate,
                            showToast,
                            dialogs,
                            hideIme,
                            appIcon
                        )

                        is SubsAppGroupListRoute -> SubsAppGroupListPage(
                            route,
                            onBack,
                            onNavigate,
                            showToast,
                            dialogs,
                            showRuleGroup,
                            copyText
                        )

                        is SubsGlobalGroupListRoute -> SubsGlobalGroupListPage(
                            route,
                            onBack,
                            onNavigate,
                            showToast,
                            dialogs,
                            showRuleGroup
                        )

                        is SubsGlobalGroupExcludeRoute -> SubsGlobalGroupExcludePage(
                            route,
                            onBack,
                            onNavigate,
                            showToast,
                            dialogs,
                            showRuleGroup,
                            hideIme,
                            appIcon
                        )

                        is SubsCategoryRoute -> SubsCategoryPage(
                            route,
                            onBack,
                            onNavigate,
                            showToast,
                            dialogs
                        )

                        is SubsCategoryGroupRoute -> SubsCategoryGroupPage(
                            route,
                            onBack,
                            onNavigate,
                            showToast,
                            dialogs,
                            confirmDelete,
                            showRuleGroup
                        )

                        is RuleExcludeEditorRoute -> RuleExcludeEditorPage(
                            route,
                            onBack,
                            showToast,
                            editorFrame
                        )

                        is AppConfigRoute -> AppConfigPage(
                            route,
                            onBack,
                            onNavigate,
                            showToast,
                            dialogs,
                            showRuleGroup,
                            copyText
                        )

                        is ActionLogRoute -> ActionLogPage(
                            route,
                            onBack,
                            onNavigate,
                            showToast,
                            appIcon
                        )

                        is UpsertRuleGroupRoute -> UpsertRuleGroupPage(
                            route,
                            onBack,
                            replaceRoute,
                            showToast,
                            editorFrame
                        )

                        is CategoryEditorRoute -> CategoryEditorPage(
                            route,
                            onBack,
                            showToast,
                            editorFrame
                        )

                        ActivityLogRoute -> ActivityLogPage(onBack, onNavigate, showText, appIcon)
                        A11yEventLogRoute -> A11yEventLogPage(onBack, onNavigate, copyText, appIcon)
                        A11YScopeAppListRoute -> ScopeAppPage(
                            onBack,
                            showToast,
                            dialogs,
                            hideIme,
                            appIcon,
                            false
                        )

                        BlockA11yAppListRoute -> ScopeAppPage(
                            onBack,
                            showToast,
                            dialogs,
                            hideIme,
                            appIcon,
                            true
                        )

                        EditBlockAppListRoute -> AppWhitelistPage(showToast, editorFrame)
                        SnapshotPageRoute -> li.gkd.app.ui.snapshot.SnapshotPage(
                            onBack,
                            onNavigate,
                            showToast,
                            showText,
                            dialogs,
                            copyText,
                            imageLoader,
                            snapshotActions,
                            uploadSnapshots
                        )

                        is SnapshotPreviewRoute -> li.gkd.app.ui.snapshot.SnapshotPreviewPage(
                            route,
                            onBack,
                            onNavigate,
                            replaceRoute,
                            topRoute,
                            copyText,
                            imageLoader,
                            systemBars,
                            snapshotActions
                        )

                        HomeRoute -> {
                            val vm = viewModel { HomeViewModel() }
                            DisposableEffect(vm, homeNavigation) {
                                homeNavigation.bind(vm.homeState)
                                onDispose { homeNavigation.unbind(vm.homeState) }
                            }
                            li.gkd.app.ui.home.HomePage(
                                window, vm, onNavigate, showToast, dialogs, subsSheet, subsLinks,
                            )
                        }

                        ActionToastRoute -> li.gkd.app.ui.home.ActionToastPage(
                            showToast,
                            editorFrame,
                            window::previewActionToast
                        )

                        NotificationTextRoute -> li.gkd.app.ui.home.NotificationTextPage(
                            showToast,
                            editorFrame
                        )

                        WorkModeRoute -> li.gkd.app.ui.settings.WorkModePage(
                            window,
                            onBack,
                            onNavigate
                        )

                        AboutRoute -> li.gkd.app.ui.settings.AboutPage(
                            window,
                            onBack,
                            onNavigate,
                            showToast,
                            updateStatus,
                            onExportLogs,
                            dialogs,
                        )

                        BlockA11ySetupRoute -> li.gkd.app.ui.home.BlockA11ySetupPage(
                            window,
                            onBack,
                            onNavigate
                        )

                        AdvancedPageRoute -> li.gkd.app.ui.settings.AdvancedPage(
                            window,
                            onBack,
                            onNavigate,
                            showToast,
                            githubUpload::editCookie
                        )

                        PrivilegeServiceRoute -> li.gkd.app.ui.PrivilegeServicePage(window)
                        SnapshotSettingsRoute -> li.gkd.app.ui.snapshot.SnapshotSettingsPage(
                            window,
                            onBack,
                            onNavigate,
                            showToast
                        )

                        is WebViewRoute -> li.gkd.app.ui.WebViewPage(
                            route,
                            window,
                            browsersRunning,
                            onBrowserKey
                        )

                        is ImagePreviewRoute -> li.gkd.app.ui.ImagePreviewPage(
                            route,
                            onBack,
                            window::openExternalUrl,
                            imageLoader,
                            systemBars
                        )

                        CrashReportRoute -> li.gkd.app.ui.crash.CrashReportPage(
                            androidx.lifecycle.viewmodel.compose.viewModel {
                                li.gkd.app.ui.crash.CrashReportViewModel(
                                    takeCrashDataList()
                                )
                            },
                            dialogs,
                            onBack,
                            { window.openExternalUrl(li.gkd.app.network.AppLinks.Issues) },
                            onExportLogs,
                            copyText,
                            showToast,
                        )
                    }
                } else {
                    diagnosticContent(route)
                }
            }
        }
    }
}
