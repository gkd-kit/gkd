package li.gkd.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.META
import li.gkd.app.permission.AppPermission
import li.gkd.app.resources.*
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkAlertDialog
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
        val tutorial =
            stringResource(Res.string.keep_alive_tile_description, META.appName) +
                stringResource(Res.string.keep_alive_setup_heading) +
                stringResource(Res.string.keep_alive_setup_open_tiles) +
                stringResource(
                    Res.string.keep_alive_setup_add_tile,
                    META.appName,
                ) +
                stringResource(Res.string.keep_alive_setup_place_tile)
        GkAlertDialog(
            onDismissRequest = { vm.setShowKeepAlive(false) },
            title = { Text(stringResource(Res.string.keep_alive_title)) },
            text = {
                Text(
                    tutorial +
                        if (platform.writeSecureSettings) ""
                        else
                            stringResource(Res.string.keep_alive_permission_missing) +
                                stringResource(Res.string.keep_alive_permission_description),
                )
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
                title = stringResource(Res.string.keep_alive_ignore_battery_optimization),
                imageVector = GkIcons.OpenInNew,
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
                onClick = ::openAppDetails,
            )
            GkSettingItem(
                title = stringResource(Res.string.keep_alive_recents_lock),
                imageVector = GkIcons.OpenInNew,
                onClickLabel = stringResource(Res.string.recents_open),
                onClick = scope.launchUiAction { openRecents() },
            )
            GkSettingItem(
                title = stringResource(Res.string.keep_alive_title),
                onClick = { vm.setShowKeepAlive(true) },
            )
            GkPageBottomSpace()
        }
    }
}
