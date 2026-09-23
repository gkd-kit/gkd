package li.gkd.app.ui.home

import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.text.UiStrings
import li.gkd.app.MainActivity
import li.gkd.app.META
import li.gkd.app.data.subscription.SubscriptionState
import li.gkd.app.notif.replaceNotificationTemplate
import li.gkd.app.store.AppStore.actionCountFlow
import li.gkd.app.ui.share.statusText
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.store.AppStore.storeFlow
import li.gkd.app.feature.settings.AboutRoute
import li.gkd.app.feature.settings.AdvancedPageRoute
import li.gkd.app.ui.BlockA11yAppListRoute
import li.gkd.app.ui.style.titleItemPadding
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.DarkThemeOption
import li.gkd.app.util.findOption
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.ui.component.GkSettingItem
import li.gkd.app.ui.component.GkTextListDialog
import li.gkd.app.ui.component.GkTextMenu
import li.gkd.app.ui.component.GkTextSwitch
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.rememberColumnScrollState

private const val ZIP_MIME_TYPE = "application/zip"

@Composable
fun useSettingsPage(): ScaffoldExt {
    val mainVm = MainViewModel.requireCurrent()
    val context = LocalActivity.current as MainActivity
    val vm = viewModel<SettingsVm>()
    val privilegeAvailable = privilegeContextFlow.collectAsStateWithLifecycle().value != null
    val store by storeFlow.collectAsStateWithLifecycle()
    val actionScope = vm.scope
    var showBackupDialog by rememberSaveable { mutableStateOf(false) }
    var showExportBackupDialog by rememberSaveable { mutableStateOf(false) }

    if (showBackupDialog) {
        GkTextListDialog(
            onDismiss = { showBackupDialog = false },
            textList = listOf(
                UiStrings.backup_import_label to {
                    actionScope.launchUi {
                        val uri = mainVm.activityResults.openDocument(ZIP_MIME_TYPE)
                        if (uri == null) {
                            toast(UiStrings.file_not_selected)
                            return@launchUi
                        }
                        vm.importBackup(uri)
                    }
                },
                UiStrings.backup_export to {
                    showExportBackupDialog = true
                },
            )
        )
    }
    if (showExportBackupDialog) {
        GkTextListDialog(
            onDismiss = { showExportBackupDialog = false },
            textList = listOf(
                UiStrings.action_share_to_apps to {
                    actionScope.launchUi {
                        val file = vm.exportBackup()
                        context.shareFile(file, UiStrings.backup_share)
                    }
                },
                UiStrings.action_save_to_downloads to {
                    actionScope.launchUi {
                        val file = vm.exportBackup()
                        context.saveFileToDownloads(file)
                    }
                },
            )
        )
    }

    val pageScrollState = rememberColumnScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val scrollState = pageScrollState.scrollState
    ResetPageScrollOnRequest(BottomNavItem.Settings, pageScrollState::resetScrollAndAwait)
    return ScaffoldExt(
        navItem = BottomNavItem.Settings,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = BottomNavItem.Settings.label,
                    )
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(contentPadding)
        ) {

            Text(
                text = UiStrings.settings_general,
                modifier = Modifier.titleItemPadding(showTop = false),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            GkSettingItem(
                title = UiStrings.action_toast,
                subtitle = listOf(
                    if (!store.toastWhenClick) UiStrings.turned_off
                    else if (store.useSystemToast) UiStrings.action_toast_style_system
                    else "",
                    store.actionToast.lineSequence().joinToString(" ").trim(),
                ).filter { it.isNotEmpty() }.joinToString(" · "),
                subtitleMaxLines = 1,
                subtitleOverflow = TextOverflow.Ellipsis,
                onClick = { mainVm.navigatePage(ActionToastRoute) },
            )

            NotificationTextSettingItem(
                useCustomText = store.useCustomNotifText,
                customTitle = store.customNotifTitle,
                customText = store.customNotifText,
                onClick = { mainVm.navigatePage(NotificationTextRoute) },
            )

            GkTextSwitch(
                title = UiStrings.hide_from_recents,
                subtitle = UiStrings.hide_from_recents_description,
                checked = store.excludeFromRecents,
                onCheckedChange = { enabled ->
                    actionScope.launchUi {
                        if (enabled) {
                            if (!mainVm.dialogRequests.confirm(
                                title = UiStrings.hide_from_recents,
                                text = UiStrings.hide_from_recents_warning,
                                confirmText = UiStrings.action_continue,
                            )) return@launchUi
                        }
                        vm.setExcludeFromRecents(enabled)
                    }
                })

            AnimatedVisibility(visible = store.enableBlockA11yAppList) {
                Text(
                    text = UiStrings.a11y_label,
                    modifier = Modifier.titleItemPadding(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            GkTextSwitch(
                title = UiStrings.service_partial_disable,
                subtitle = if (store.enableBlockA11yAppList && !privilegeAvailable)
                    UiStrings.privilege_service_disconnected
                else UiStrings.service_partial_disable_description,
                checked = store.enableBlockA11yAppList,
                onClick = { mainVm.navigatePage(BlockA11ySetupRoute) },
                onClickLabel = UiStrings.partial_disable_setup_open,
                onCheckedChange = {
                    if (it) {
                        mainVm.navigatePage(BlockA11ySetupRoute)
                    } else {
                        vm.setBlockA11yAppListEnabled(false)
                    }
                },
            )
            AnimatedVisibility(visible = store.enableBlockA11yAppList) {
                GkSettingItem(title = UiStrings.whitelist_title, onClickLabel = UiStrings.a11y_whitelist_open, onClick = {
                    mainVm.navigatePage(BlockA11yAppListRoute)
                })
            }

            Text(
                text = UiStrings.settings_appearance,
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            GkTextMenu(
                title = UiStrings.theme_mode,
                option = DarkThemeOption.objects.findOption(store.enableDarkTheme),
                onOptionChange = {
                    vm.setDarkTheme(it.value)
                }
            )

            if (AndroidTarget.S) {
                GkTextSwitch(
                    title = UiStrings.dynamic_colors,
                    checked = store.enableDynamicColor,
                    onCheckedChange = {
                        vm.setDynamicColor(it)
                    }
                )
            }

            Text(
                text = UiStrings.settings_other,
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            GkSettingItem(title = UiStrings.advanced_settings, onClick = {
                mainVm.navigatePage(AdvancedPageRoute)
            })
            GkSettingItem(title = UiStrings.backup_restore, onClick = {
                showBackupDialog = true
            })

            GkSettingItem(title = UiStrings.about_title, onClick = {
                mainVm.navigatePage(AboutRoute)
            })

            GkPageBottomSpace()
        }
    }
}

@Composable
private fun NotificationTextSettingItem(
    useCustomText: Boolean,
    customTitle: String,
    customText: String,
    onClick: () -> Unit,
) {
    val ruleSummary by SubscriptionState.ruleSummaryFlow.collectAsStateWithLifecycle()
    val actionCount by actionCountFlow.collectAsStateWithLifecycle()
    val title = if (useCustomText) customTitle.replaceNotificationTemplate(ruleSummary, actionCount)
    else META.appName
    val text = if (useCustomText) customText.replaceNotificationTemplate(ruleSummary, actionCount)
    else ruleSummary.statusText(actionCount)
    val subtitle = listOf(title, text)
        .map { it.lineSequence().joinToString(" ").trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
    GkSettingItem(
        title = UiStrings.notification_text,
        subtitle = subtitle,
        subtitleMaxLines = 1,
        subtitleOverflow = TextOverflow.Ellipsis,
        onClick = onClick,
    )
}
