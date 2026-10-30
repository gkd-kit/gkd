package li.gkd.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.META
import li.gkd.app.permission.AppPermission
import li.gkd.app.resources.*
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkSettingItem
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.gkTopAppBarScroll
import li.gkd.app.ui.home.dashboardPlatformState
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.platform.LocalUiHost
import li.gkd.app.ui.platform.ignoresBatteryOptimizations
import li.gkd.app.ui.platform.openAppDetails
import li.gkd.app.ui.platform.openRecents
import li.gkd.app.ui.platform.setStatusServiceEnabled
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.ui.style.titleItemPadding
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun KeepAlivePage() {
    val host = LocalUiHost.current
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { KeepAliveViewModel() }
    val scope = rememberCoroutineScope()
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val platform = dashboardPlatformState()
    val batteryUnrestricted = ignoresBatteryOptimizations()
    val showKeepAlive by vm.showKeepAlive.collectAsStateWithLifecycle()
    if (showKeepAlive) {
        GkAlertDialog(
            onDismissRequest = { vm.setShowKeepAlive(false) },
            title = { Text(stringResource(Res.string.keep_alive_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        stringResource(Res.string.keep_alive_tile_description, META.appName).trim(),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(Res.string.keep_alive_setup_heading).trim(),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(stringResource(Res.string.keep_alive_setup_open_tiles).trim())
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.width(64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GkIcon(
                                    imageVector = GkIcons.TileStatus,
                                    modifier = Modifier.size(32.dp),
                                    contentDescription = null,
                                )
                                Text(
                                    META.appName,
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Text(
                                stringResource(Res.string.keep_alive_setup_add_tile, META.appName)
                                    .trim(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(stringResource(Res.string.keep_alive_setup_place_tile))
                    }
                    if (!platform.writeSecureSettings) {
                        Text(
                            stringResource(Res.string.keep_alive_permission_missing).trimStart() +
                                stringResource(Res.string.keep_alive_permission_description),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.setShowKeepAlive(false)
                        if (!platform.writeSecureSettings)
                            mainVm.navigator.navigate(PrivilegeServiceRoute)
                    },
                ) {
                    Text(
                        stringResource(
                            if (platform.writeSecureSettings) Res.string.action_understood
                            else Res.string.settings_go_to,
                        ),
                    )
                }
            },
            dismissButton =
                if (platform.writeSecureSettings) null
                else {
                    {
                        TextButton(
                            onClick = {
                                vm.setShowKeepAlive(false)
                            },
                        ) {
                            Text(stringResource(Res.string.action_close))
                        }
                    }
                },
        )
    }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    GkScaffold(
        modifier = Modifier.gkTopAppBarScroll(scrollBehavior),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(imageVector = GkIcons.ArrowBack, onClick = mainVm.navigator::pop)
                },
                title = { Text(stringResource(Res.string.keep_alive_strategy)) },
            )
        },
    ) { contentPadding ->
        Column(
            Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(Res.string.keep_alive_app_measures),
                modifier = Modifier.titleItemPadding(showTop = false),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            GkTextSwitch(
                title = stringResource(Res.string.persistent_notification),
                checked = platform.statusRunning && store.enableStatusService,
                onCheckedChange = host::setStatusServiceEnabled,
            )
            GkTextSwitch(
                title = stringResource(Res.string.status_overlay_keep_alive),
                checked = store.enableStatusOverlay,
                onCheckedChange = vm::requestStatusOverlayEnabled,
            )
            Text(
                stringResource(Res.string.keep_alive_system_settings),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            GkSettingItem(
                title = stringResource(Res.string.battery_optimization_ignore),
                imageVector = if (batteryUnrestricted) GkIcons.Check else GkIcons.OpenInNew,
                onClickLabel =
                    stringResource(
                        if (batteryUnrestricted) Res.string.app_details_open
                        else Res.string.battery_optimization_settings_open,
                    ),
                onClick =
                    if (batteryUnrestricted) ::openAppDetails
                    else
                        scope.launchUiAction {
                            mainVm.permissions.ensurePermissions(
                                AppPermission.IgnoreBatteryOptimizations,
                            )
                        },
            )
            GkSettingItem(
                title = stringResource(Res.string.autostart_allow),
                imageVector = GkIcons.OpenInNew,
                onClickLabel = stringResource(Res.string.app_details_open),
                onClick =
                    scope.launchUiAction {
                        if (
                            mainVm.dialogRequests.confirm(
                                title = getString(Res.string.autostart_allow),
                                text = getString(Res.string.autostart_manual_description),
                                confirmText = getString(Res.string.settings_go_to),
                                dismissText = getString(Res.string.action_cancel),
                                dismissOnRequest = true,
                            )
                        ) {
                            openAppDetails()
                        }
                    },
            )
            GkSettingItem(
                title = stringResource(Res.string.keep_alive_recents_lock),
                imageVector = GkIcons.OpenInNew,
                onClickLabel = stringResource(Res.string.recents_open),
                onClick =
                    scope.launchUiAction {
                        if (
                            mainVm.dialogRequests.confirm(
                                title = getString(Res.string.keep_alive_recents_lock),
                                text = getString(Res.string.recents_lock_manual_description),
                                confirmText = getString(Res.string.settings_go_to),
                                dismissText = getString(Res.string.action_cancel),
                                dismissOnRequest = true,
                            )
                        ) {
                            openRecents()
                        }
                    },
            )
            GkSettingItem(
                title = stringResource(Res.string.keep_alive_title),
                onClick = { vm.setShowKeepAlive(true) },
            )
            GkPageBottomSpace()
        }
    }
}
