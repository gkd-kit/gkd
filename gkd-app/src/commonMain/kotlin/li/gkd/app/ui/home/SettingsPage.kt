package li.gkd.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.resources.Res
import li.gkd.app.resources.a11y_label
import li.gkd.app.resources.a11y_whitelist_open
import li.gkd.app.resources.about_title
import li.gkd.app.resources.action_continue
import li.gkd.app.resources.action_toast
import li.gkd.app.resources.action_toast_style_system
import li.gkd.app.resources.advanced_settings
import li.gkd.app.resources.backup_restore
import li.gkd.app.resources.dynamic_colors
import li.gkd.app.resources.hide_from_recents
import li.gkd.app.resources.hide_from_recents_description
import li.gkd.app.resources.hide_from_recents_warning
import li.gkd.app.resources.notification_text
import li.gkd.app.resources.partial_disable_setup_open
import li.gkd.app.resources.privilege_service_disconnected
import li.gkd.app.resources.service_partial_disable
import li.gkd.app.resources.service_partial_disable_description
import li.gkd.app.resources.settings_appearance
import li.gkd.app.resources.settings_general
import li.gkd.app.resources.settings_other
import li.gkd.app.resources.theme_mode
import li.gkd.app.resources.turned_off
import li.gkd.app.resources.whitelist_title
import li.gkd.app.rule.ruleGroupState
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkSettingItem
import li.gkd.app.ui.component.GkTextMenu
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.rememberColumnScrollState
import li.gkd.app.ui.navigation.AboutRoute
import li.gkd.app.ui.navigation.ActionToastRoute
import li.gkd.app.ui.navigation.AdvancedPageRoute
import li.gkd.app.ui.navigation.BlockA11yAppListRoute
import li.gkd.app.ui.navigation.BlockA11ySetupRoute
import li.gkd.app.ui.navigation.NotificationTextRoute
import li.gkd.app.ui.option.DarkThemeOption
import li.gkd.app.ui.option.findOption
import li.gkd.app.ui.page.GkBackupDialogs
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.platform.dynamicColorAvailable
import li.gkd.app.ui.platform.privilegeAvailable
import li.gkd.app.ui.settings.appVersion
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.style.titleItemPadding
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun settingsPage(
    host: UiHost,
    vm: HomeViewModel,
): ScaffoldExt {
    val mainVm = MainViewModel.requireCurrent()
    val scope = rememberCoroutineScope()
    val appName = appVersion().appName
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val privilegeAvailable = privilegeAvailable()
    val showBackup by vm.showBackup.collectAsStateWithLifecycle()
    GkBackupDialogs(
        showBackup,
        { vm.setShowBackup(false) },
        host::importAppBackup,
        host::shareAppBackup,
        host::saveAppBackup,
    )
    fun setExcludeFromRecents(enabled: Boolean) {
        scope.launchUi {
            if (
                !enabled ||
                    mainVm.dialogRequests.confirm(
                        title = getString(Res.string.hide_from_recents),
                        text = getString(Res.string.hide_from_recents_warning),
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
        modifier = Modifier.nestedScroll(scroll.scrollBehavior.nestedScrollConnection),
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
                val summary = dashboardSummary(rules, count)
                fun format(text: String) =
                    HomeDataText.format(
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
                        listOf(title, text)
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
                    subtitle = stringResource(Res.string.hide_from_recents_description),
                    checked = store.excludeFromRecents,
                    onCheckedChange = ::setExcludeFromRecents,
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
                    subtitle =
                        if (store.enableBlockA11yAppList && !privilegeAvailable)
                            stringResource(Res.string.privilege_service_disconnected)
                        else stringResource(Res.string.service_partial_disable_description),
                    checked = store.enableBlockA11yAppList,
                    onClick = { mainVm.navigator.navigate(BlockA11ySetupRoute) },
                    onClickLabel = stringResource(Res.string.partial_disable_setup_open),
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
                    option = DarkThemeOption.objects.findOption(store.enableDarkTheme),
                    onOptionChange = {
                        SettingsRepository.updateSettings { store -> store.copy(enableDarkTheme = it.value) }
                    },
                )

                if (dynamicColorAvailable()) {
                    GkTextSwitch(
                        title = stringResource(Res.string.dynamic_colors),
                        checked = store.enableDynamicColor,
                        onCheckedChange = {
                            SettingsRepository.updateSettings { store -> store.copy(enableDynamicColor = it) }
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
                    title = stringResource(Res.string.advanced_settings),
                    onClick = {
                        mainVm.navigator.navigate(AdvancedPageRoute)
                    },
                )
                GkSettingItem(
                    title = stringResource(Res.string.backup_restore),
                    onClick = {
                        { vm.setShowBackup(true) }()
                    },
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
