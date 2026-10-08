package li.gkd.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.Res
import li.gkd.app.resources.about_easter_egg
import li.gkd.app.resources.about_title
import li.gkd.app.resources.action_continue
import li.gkd.app.resources.action_update
import li.gkd.app.resources.beta_channel_warning
import li.gkd.app.resources.donate
import li.gkd.app.resources.feedback_notice
import li.gkd.app.resources.feedback_report
import li.gkd.app.resources.feedback_title
import li.gkd.app.resources.logs_export
import li.gkd.app.resources.notice_title
import li.gkd.app.resources.privacy_policy
import li.gkd.app.resources.source_code
import li.gkd.app.resources.terms_of_use
import li.gkd.app.resources.update_channel
import li.gkd.app.resources.update_check
import li.gkd.app.resources.version_channel
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkRotatingLoadingIcon
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkSettingItem
import li.gkd.app.ui.component.GkTextMenu
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.icon.GkAnimatedLogoIcon
import li.gkd.app.ui.option.UpdateChannelOption
import li.gkd.app.ui.option.findOption
import li.gkd.app.ui.page.GkVersionInfoDialog
import li.gkd.app.ui.page.feedbackNotice
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.share.LocalDarkTheme
import li.gkd.app.ui.style.itemPadding
import li.gkd.app.ui.style.titleItemPadding
import li.gkd.app.ui.text.displayMessage
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun AboutPage(
    host: UiHost,
) {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { AboutViewModel() }
    val version = appVersion()
    val showVersionInfoDialog by vm.showVersionInfoDialog.collectAsStateWithLifecycle()
    val showShareAppDialog by vm.showShareAppDialog.collectAsStateWithLifecycle()
    val store by settings.collectAsStateWithLifecycle()

    val checkingUpdate: Boolean =
        mainVm.updateStatus?.checkUpdatingFlow?.collectAsStateWithLifecycle()?.value ?: false

    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    fun launchAction(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mainVm.dialogRequests.showMessage(getString(Res.string.notice_title), e.displayMessage())
            }
        }
    }
    val updateChannel = UpdateChannelOption.objects.findOption(store.updateChannel)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    GkScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = {
                            mainVm.navigator.pop()
                        },
                    )
                },
                title = { Text(text = stringResource(Res.string.about_title)) },
                actions = {
                    GkIconButton(
                        imageVector = GkIcons.Share,
                        onClick = { vm.setShowShareAppDialog(true) },
                    )
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GkAnimatedLogoIcon(
                    tint = Color(if (LocalDarkTheme.current) 0xFFFCFCFC else 0xFF111111),
                    modifier =
                        Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { ToastUtils.show(Res.string.about_easter_egg.getSync()) },
                            )
                            .fillMaxWidth(0.33f)
                            .aspectRatio(1f),
                )
                Column(
                    modifier =
                        Modifier.clip(MaterialTheme.shapes.extraSmall)
                            .clickable(onClick = { vm.setShowVersionInfoDialog(true) })
                            .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = version.appName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = version.versionName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            GkSettingItem(
                imageVector = null,
                title = stringResource(Res.string.source_code),
                onClick = {
                    mainVm.textDialog.showUrl(AppLinks.Repository)
                },
            )
            if (version.isGkdChannel) {
                GkSettingItem(
                    imageVector = null,
                    title = stringResource(Res.string.donate),
                    onClick = {
                        mainVm.navigator.openWebPage(AppLinks.Donate)
                    },
                )
            }
            GkSettingItem(
                imageVector = null,
                title = stringResource(Res.string.terms_of_use),
                onClick = {
                    mainVm.navigator.openWebPage(AppLinks.TermsOfService)
                },
            )
            GkSettingItem(
                imageVector = null,
                title = stringResource(Res.string.privacy_policy),
                onClick = {
                    mainVm.navigator.openWebPage(AppLinks.PrivacyPolicy)
                },
            )

            FeedbackSection {
                launchAction {
                    if (
                        mainVm.dialogRequests.confirm(
                            title = getString(Res.string.feedback_notice),
                            text = feedbackNotice(primary),
                            confirmText = getString(Res.string.action_continue),
                            dismissOnRequest = true,
                        )
                    )
                        mainVm.textDialog.showUrl(AppLinks.Issues)
                }
            }
            GkSettingItem(
                title = stringResource(Res.string.logs_export),
                imageVector = GkIcons.Share,
                onClick = {
                    mainVm.shareLog.show()
                },
            )
            if (mainVm.updateStatus != null) {
                Text(
                    text = stringResource(Res.string.action_update),
                    modifier = Modifier.titleItemPadding(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                GkTextMenu(
                    title = stringResource(Res.string.update_channel),
                    option = updateChannel,
                    onOptionChange = { option ->
                        launchAction {
                            if (
                                option != UpdateChannelOption.Beta ||
                                    mainVm.dialogRequests.confirm(
                                        title = getString(Res.string.version_channel),
                                        text = getString(Res.string.beta_channel_warning),
                                    )
                            )
                                SettingsRepository.updateSettings { it.copy(updateChannel = option.value) }
                        }
                    },
                )

                Row(
                    modifier =
                        Modifier.clickable(onClick = { mainVm.updateStatus.checkUpdate(true) })
                            .fillMaxWidth()
                            .itemPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.update_check),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    GkRotatingLoadingIcon(loading = checkingUpdate)
                }
            }
            GkPageBottomSpace()
        }
    }

    GkVersionInfoDialog(
        showVersionInfoDialog,
        version.channel,
        version.versionCode,
        version.versionName,
        version.commitLabel,
        version.commitTime,
        { mainVm.textDialog.showUrl(version.commitUrl) },
        { vm.setShowVersionInfoDialog(false) },
    )
    host.GkShareAppDialog(
        visible = showShareAppDialog,
        dialogs = mainVm.dialogRequests,
        scope = mainVm.scope,
        onOpenUrl = mainVm.textDialog::showUrl,
    ) { vm.setShowShareAppDialog(false) }
}

@Composable expect fun UiHost.GkShareAppDialog(
    visible: Boolean,
    dialogs: DialogRequests,
    scope: CoroutineScope,
    onOpenUrl: (String) -> Unit,
    onDismissRequest: () -> Unit,
)

@Composable
private fun FeedbackSection(onClick: () -> Unit) {
    Text(
        stringResource(Res.string.feedback_title),
        modifier = Modifier.titleItemPadding(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Column(Modifier.clickable(onClick = onClick).fillMaxWidth().itemPadding()) {
        Text(stringResource(Res.string.feedback_report), style = MaterialTheme.typography.bodyLarge)
    }
}
