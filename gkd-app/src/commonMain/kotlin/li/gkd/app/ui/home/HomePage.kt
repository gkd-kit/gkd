package li.gkd.app.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_continue
import li.gkd.app.resources.hide_from_recents
import li.gkd.app.resources.hide_from_recents_warning
import li.gkd.app.resources.notification_text
import li.gkd.app.rule.ruleGroupState
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.Loadable
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkSettingItem
import li.gkd.app.ui.navigation.AboutRoute
import li.gkd.app.ui.navigation.ActionLogRoute
import li.gkd.app.ui.navigation.ActionToastRoute
import li.gkd.app.ui.navigation.ActivityLogRoute
import li.gkd.app.ui.navigation.AdvancedPageRoute
import li.gkd.app.ui.navigation.AppConfigRoute
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.navigation.BlockA11yAppListRoute
import li.gkd.app.ui.navigation.BlockA11ySetupRoute
import li.gkd.app.ui.navigation.EditBlockAppListRoute
import li.gkd.app.ui.navigation.GkAppIcon
import li.gkd.app.ui.navigation.GkBackHandler
import li.gkd.app.ui.navigation.NotificationTextRoute
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.navigation.WebViewRoute
import li.gkd.app.ui.navigation.WorkModeRoute
import li.gkd.app.ui.navigation.dynamicColorAvailable
import li.gkd.app.ui.navigation.hideIme
import li.gkd.app.ui.navigation.launchUi
import li.gkd.app.ui.navigation.requestQueryPackages
import li.gkd.app.ui.navigation.setStatusServiceEnabled
import li.gkd.app.ui.navigation.switchAutomator
import li.gkd.app.ui.option.AutomatorModeOption
import li.gkd.app.ui.option.findOption
import li.gkd.app.ui.page.GkBackupDialogs
import li.gkd.app.ui.page.SettingsContent
import li.gkd.app.ui.page.SettingsDestination
import li.gkd.app.ui.page.SettingsUiActions
import li.gkd.app.ui.settings.appVersion
import li.gkd.app.ui.settings.toUiState
import li.gkd.app.ui.subscription.SubsLinkDialogState
import li.gkd.app.ui.subscription.SubsSheetState
import li.gkd.db.RuleGroupType
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomePage(
    window: AppWindow, vm: HomeViewModel, onNavigate: (AppRoute) -> Unit,
    toast: (String) -> Unit, dialogs: DialogRequests,
    subsSheet: SubsSheetState, subsLinks: SubsLinkDialogState,
) {
    val homeState = vm.homeState
    val appName = window.appVersion().appName
    val scope = rememberCoroutineScope()
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val scopeApps by SettingsRepository.a11yScopeAppList.collectAsStateWithLifecycle()
    val platform = window.dashboardPlatformState()
    var showBackup by rememberSaveable { mutableStateOf(false) }
    GkBackupDialogs(
        showBackup,
        { showBackup = false },
        window::importAppBackup,
        window::shareAppBackup,
        window::saveAppBackup
    )
    GkHomeHost(homeState) { tab ->
        when (tab) {
            BottomNavItem.Dashboard -> {
                val latestState by vm.latestState.collectAsStateWithLifecycle()
                val rules by ruleGroupState.collectAsStateWithLifecycle()
                val actionCount by SettingsRepository.actionCount.collectAsStateWithLifecycle()
                val appCatalog by AppInfoRepository.state.collectAsStateWithLifecycle()
                val subscriptions by SubscriptionRepository.snapshotFlow.collectAsStateWithLifecycle()
                val latest = latestState.value?.record
                dashboardPage(
                    bindScroll = { ResetPageScrollOnRequest(homeState, tab, it) },
                    state = DashboardUiState(
                        appName = appName,
                        serviceSubtitle = stringResource(platform.subtitle(store, scopeApps)),
                        serviceEnabled = platform.serviceEnabled(store, scopeApps),
                        mode = AutomatorModeOption.objects.findOption(store.automatorMode).label,
                        statusEnabled = platform.statusRunning && store.enableStatusService,
                        subsStatus = dashboardSummary(rules, actionCount),
                        latestRecord = HomeDataText.latest(
                            latest, subscriptions.value?.subscriptions.orEmpty(),
                            appCatalog.snapshot?.apps.orEmpty(),
                        ),
                        latestIsGlobal = latest?.groupType == RuleGroupType.Global,
                        latestLoadFailed = latestState is Loadable.Failure,
                        privilegeStatus = platform.privilegeStatus,
                        activityLogVisible = platform.activityRunning,
                        restricted = platform.restricted,
                    ),
                    actions = DashboardUiActions(
                        onService = { enabled ->
                            val route = platform.authorizationRoute(enabled, store, scopeApps)
                            if (route != null) onNavigate(route) else window.switchAutomator()
                        },
                        onMode = { onNavigate(WorkModeRoute) },
                        onStatus = window::setStatusServiceEnabled,
                        onLog = { onNavigate(ActionLogRoute()) },
                        onLatest = {
                            latest?.let {
                                onNavigate(
                                    AppConfigRoute(
                                        it.appId,
                                        focusLog = it
                                    )
                                )
                            }
                        },
                        onActivityLog = { onNavigate(ActivityLogRoute) },
                        onHelp = { onNavigate(WebViewRoute(AppLinks.Home)) },
                        onPrivilege = { onNavigate(PrivilegeServiceRoute) },
                    ),
                )
            }

            BottomNavItem.AppList -> {
                val appState by vm.appsState.collectAsStateWithLifecycle()
                val blocked by SettingsRepository.blockMatchAppList.collectAsStateWithLifecycle()
                val controls = rememberAppListPageState()
                appListPage(
                    toast = toast, bindScroll = { ResetPageScrollOnRequest(homeState, tab, it) },
                    state = controls.content(appState, store, blocked),
                    searchBackHandler = { GkBackHandler { if (!window.hideIme()) controls.closeSearch() } },
                    editBackHandler = { GkBackHandler(onBack = controls::closeEdit) },
                    actions = AppListActions(
                        onSearch = controls::setSearchText,
                        onToggleSearch = controls::toggleSearch,
                        onToggleEdit = { controls.toggleEdit(blocked) },
                        onRefresh = vm::refreshApps,
                        onSort = vm::setSortType,
                        onGroup = vm::setAppGroupType,
                        onShowBlocked = vm::setShowBlockApp,
                        onEditWhitelist = { onNavigate(EditBlockAppListRoute) },
                        onToggleWhitelist = vm::toggleWhiteList,
                        onLeave = controls::onLeave,
                        onRequestPermission = window::requestQueryPackages,
                        onOpen = { window.hideIme(); onNavigate(AppConfigRoute(it)) },
                    ),
                    appIcon = { window.GkAppIcon(it, 32.dp) },
                    appName = { GkAppNameText(appInfo = it) },
                )
            }

            BottomNavItem.SubsManage -> subsManagePage(
                vm, homeState,
                SubsManageHost(
                    appName, onNavigate, subsSheet::show, { subsLinks.request() },
                    { title, text, error -> dialogs.confirm(title, text, error = error) },
                    toast, { onNavigate(WebViewRoute(AppLinks.BackgroundRunningHelp)) },
                ),
                selectionBackHandler = { enabled, back -> GkBackHandler(enabled, back) },
            )

            BottomNavItem.Settings -> settingsPage(bindScroll = {
                ResetPageScrollOnRequest(
                    homeState,
                    tab,
                    it
                )
            }) { padding, scroll ->
                SettingsContent(
                    state = store.toUiState(
                        platform.privilegeAvailable,
                        window.dynamicColorAvailable()
                    ),
                    actions = SettingsUiActions(
                        onNavigate = { destination ->
                            onNavigate(
                                when (destination) {
                                    SettingsDestination.ActionToast -> ActionToastRoute
                                    SettingsDestination.Advanced -> AdvancedPageRoute
                                    SettingsDestination.About -> AboutRoute
                                    SettingsDestination.BlockA11ySetup -> BlockA11ySetupRoute
                                    SettingsDestination.BlockA11yAppList -> BlockA11yAppListRoute
                                }
                            )
                        },
                        onExcludeFromRecents = { enabled ->
                            launchUi(scope, toast) {
                                if (!enabled || dialogs.confirm(
                                        title = getString(Res.string.hide_from_recents),
                                        text = getString(Res.string.hide_from_recents_warning),
                                        confirmText = getString(Res.string.action_continue),
                                    )
                                ) vm.setExcludeFromRecents(enabled)
                            }
                        },
                        onBlockA11yEnabled = vm::setBlockA11yEnabled,
                        onDarkTheme = vm::setDarkTheme,
                        onDynamicColor = vm::setDynamicColor,
                        onBackup = { showBackup = true },
                    ),
                    scrollState = scroll, modifier = Modifier.padding(padding),
                    notificationItem = {
                        val rules by ruleGroupState.collectAsStateWithLifecycle()
                        val count by SettingsRepository.actionCount.collectAsStateWithLifecycle()
                        val groups = rules.value?.groups
                        val summary = dashboardSummary(rules, count)
                        fun format(text: String) = HomeDataText.format(
                            text,
                            groups?.globalGroups?.size,
                            groups?.appSize,
                            groups?.appGroupSize,
                            count,
                        )

                        val title =
                            if (store.useCustomNotifText) format(store.customNotifTitle) else appName
                        val text =
                            if (store.useCustomNotifText) format(store.customNotifText) else summary
                        GkSettingItem(
                            stringResource(Res.string.notification_text),
                            subtitle = listOf(title, text).map {
                                it.lineSequence().joinToString(" ").trim()
                            }.filter { it.isNotEmpty() }.joinToString(" · "),
                            subtitleMaxLines = 1, subtitleOverflow = TextOverflow.Ellipsis,
                            onClick = { onNavigate(NotificationTextRoute) },
                        )
                    },
                )
            }
        }
    }
}
