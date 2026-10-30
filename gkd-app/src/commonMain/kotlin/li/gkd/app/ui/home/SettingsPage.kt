package li.gkd.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import li.gkd.app.META
import li.gkd.app.model.findOption
import li.gkd.app.notif.formatNotificationTemplate
import li.gkd.app.resources.*
import li.gkd.app.rule.ruleGroupState
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.Loadable
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkSettingItem
import li.gkd.app.ui.component.GkTextMenu
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.gkTopAppBarScroll
import li.gkd.app.ui.component.rememberColumnScrollState
import li.gkd.app.ui.navigation.AboutRoute
import li.gkd.app.ui.navigation.ActionToastRoute
import li.gkd.app.ui.navigation.AdvancedPageRoute
import li.gkd.app.ui.navigation.BlockA11yAppListRoute
import li.gkd.app.ui.navigation.BlockA11ySetupRoute
import li.gkd.app.ui.navigation.KeepAliveRoute
import li.gkd.app.ui.navigation.MatchDebugRoute
import li.gkd.app.ui.navigation.NotificationTextRoute
import li.gkd.app.ui.option.DarkThemeOption
import li.gkd.app.ui.page.BackupDialog
import li.gkd.app.ui.page.GkBackupDialogs
import li.gkd.app.ui.platform.LocalUiHost
import li.gkd.app.ui.platform.dynamicColorAvailable
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.style.titleItemPadding
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun settingsPage(
    vm: HomeViewModel,
): ScaffoldExt {
    val host = LocalUiHost.current
    val mainVm = MainViewModel.requireCurrent()
    val scope = rememberCoroutineScope()
    val appName = META.appName
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    var selectedTheme by
        remember(store.enableDarkTheme) {
            mutableStateOf(store.enableDarkTheme)
        }
    var themeUpdateJob by remember { mutableStateOf<Job?>(null) }
    val backupDialog by vm.backupDialog.collectAsStateWithLifecycle()
    GkBackupDialogs(
        dialog = backupDialog,
        onDismiss = { vm.setBackupDialog(null) },
        onExport = { vm.setBackupDialog(BackupDialog.Export) },
        onImport = host::importAppBackup,
        onShare = host::shareAppBackup,
        onSaveDownloads = host::saveAppBackup,
    )
    fun setExcludeFromRecents(enabled: Boolean) {
        scope.launchUi {
            if (
                !enabled ||
                    mainVm.dialogRequests.confirm(
                        title = getString(Res.string.hide_from_recents),
                        text =
                            getString(Res.string.hide_from_recents_description) +
                                "\n\n" +
                                getString(Res.string.hide_from_recents_warning),
                        confirmText = getString(Res.string.action_continue),
                    )
            )
                SettingsRepository.updateSettings { it.copy(excludeFromRecents = enabled) }
        }
    }
    val scroll = rememberColumnScrollState()
    ResetPageScrollOnRequest(vm.homeState, BottomNavItem.Settings, scroll::resetScrollAndAwait)
    return ScaffoldExt(
        BottomNavItem.Settings,
        modifier = Modifier.gkTopAppBarScroll(scroll.scrollBehavior),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scroll.scrollBehavior,
                title = { Text(BottomNavItem.Settings.label) },
            )
        },
        content = { padding ->
            Column(modifier = Modifier.padding(padding).verticalScroll(scroll.scrollState)) {
                Text(
                    text = stringResource(Res.string.settings_general),
                    modifier = Modifier.titleItemPadding(showTop = false),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                GkSettingItem(
                    title = stringResource(Res.string.action_toast),
                    subtitle =
                        listOf(
                                if (!store.toastWhenClick) stringResource(Res.string.turned_off)
                                else if (store.useSystemToast)
                                    stringResource(Res.string.action_toast_style_system)
                                else "",
                                store.actionToast.lineSequence().joinToString(" ").trim(),
                            )
                            .filter { it.isNotEmpty() }
                            .joinToString(" · "),
                    subtitleMaxLines = 1,
                    subtitleOverflow = TextOverflow.Ellipsis,
                    onClick = { mainVm.navigator.navigate(ActionToastRoute) },
                )

                val rules by ruleGroupState.collectAsStateWithLifecycle()
                val count by SettingsRepository.actionCount.collectAsStateWithLifecycle()
                val groups = rules.value?.groups
                val summary =
                    when (val state = rules) {
                        Loadable.Loading -> null
                        is Loadable.Failure -> stringResource(Res.string.data_load_failed)
                        is Loadable.Ready -> {
                            val ruleSummary =
                                dashboardSummary(state.value.groups)
                                    ?: stringResource(Res.string.rules_empty)
                            if (count > 0)
                                stringResource(
                                    Res.string.action_count_summary,
                                    ruleSummary,
                                    count.toString(),
                                )
                            else ruleSummary
                        }
                    }
                fun format(text: String) =
                    formatNotificationTemplate(
                        text,
                        groups?.globalGroups?.size,
                        groups?.appSize,
                        groups?.appGroupSize,
                        count,
                    )

                val title =
                    if (store.useCustomNotifText) format(store.customNotifTitle) else appName
                val text = if (store.useCustomNotifText) format(store.customNotifText) else summary
                GkSettingItem(
                    stringResource(Res.string.notification_text),
                    subtitle =
                        listOfNotNull(title, text)
                            .map {
                                it.lineSequence().joinToString(" ").trim()
                            }
                            .filter { it.isNotEmpty() }
                            .joinToString(" · "),
                    subtitleMaxLines = 1,
                    subtitleOverflow = TextOverflow.Ellipsis,
                    onClick = { mainVm.navigator.navigate(NotificationTextRoute) },
                )

                GkTextSwitch(
                    title = stringResource(Res.string.hide_from_recents),
                    checked = store.excludeFromRecents,
                    onCheckedChange = ::setExcludeFromRecents,
                )

                GkSettingItem(
                    title = stringResource(Res.string.keep_alive_strategy),
                    onClick = { mainVm.navigator.navigate(KeepAliveRoute) },
                )

                AnimatedVisibility(visible = store.enableBlockA11yAppList) {
                    Text(
                        text = stringResource(Res.string.a11y_label),
                        modifier = Modifier.titleItemPadding(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                GkTextSwitch(
                    title = stringResource(Res.string.service_partial_disable),
                    checked = store.enableBlockA11yAppList,
                    onCheckedChange = {
                        if (it) {
                            mainVm.navigator.navigate(BlockA11ySetupRoute)
                        } else {
                            SettingsRepository.setBlockA11yAppListEnabled(false)
                        }
                    },
                )
                AnimatedVisibility(visible = store.enableBlockA11yAppList) {
                    GkSettingItem(
                        title = stringResource(Res.string.whitelist_title),
                        onClickLabel = stringResource(Res.string.a11y_whitelist_open),
                        onClick = {
                            mainVm.navigator.navigate(BlockA11yAppListRoute)
                        },
                    )
                }

                Text(
                    text = stringResource(Res.string.settings_appearance),
                    modifier = Modifier.titleItemPadding(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                GkTextMenu(
                    title = stringResource(Res.string.theme_mode),
                    option = DarkThemeOption.objects.findOption(selectedTheme),
                    onOptionChange = { option ->
                        selectedTheme = option.value
                        themeUpdateJob?.cancel()
                        themeUpdateJob =
                            mainVm.scope.launch {
                                // Let the menu close before changing the application's colors.
                                delay(300)
                                SettingsRepository.updateSettings {
                                    it.copy(enableDarkTheme = option.value)
                                }
                            }
                    },
                )

                if (dynamicColorAvailable()) {
                    GkTextSwitch(
                        title = stringResource(Res.string.dynamic_colors),
                        checked = store.enableDynamicColor,
                        onCheckedChange = {
                            SettingsRepository.updateSettings { store ->
                                store.copy(enableDynamicColor = it)
                            }
                        },
                    )
                }

                Text(
                    text = stringResource(Res.string.settings_other),
                    modifier = Modifier.titleItemPadding(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                GkSettingItem(
                    title = stringResource(Res.string.backup_restore),
                    onClick = {
                        vm.setBackupDialog(BackupDialog.Main)
                    },
                )
                GkSettingItem(
                    title = stringResource(Res.string.advanced_settings),
                    onClick = {
                        mainVm.navigator.navigate(AdvancedPageRoute)
                    },
                )
                GkSettingItem(
                    title = stringResource(Res.string.advanced_logs_diagnostics),
                    onClick = { mainVm.navigator.navigate(MatchDebugRoute) },
                )
                GkSettingItem(
                    title = stringResource(Res.string.about_title),
                    onClick = {
                        mainVm.navigator.navigate(AboutRoute)
                    },
                )

                GkPageBottomSpace()
            }
        },
    )
}
