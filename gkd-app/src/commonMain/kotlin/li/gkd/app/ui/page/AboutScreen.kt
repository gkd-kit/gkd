package li.gkd.app.ui.page

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import li.gkd.app.resources.Res
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
import li.gkd.app.ui.share.LocalDarkTheme
import li.gkd.app.ui.style.itemPadding
import li.gkd.app.ui.style.titleItemPadding
import li.gkd.app.ui.text.displayMessage
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun AboutScreen(
    appName: String, versionName: String, isGkdChannel: Boolean,
    updateChannelValue: Int, updateAvailable: Boolean, checkingUpdate: Boolean,
    onBack: () -> Unit, onShare: () -> Unit, onVersion: () -> Unit, onLogo: () -> Unit,
    onSource: () -> Unit, onDonate: () -> Unit, onTerms: () -> Unit, onPrivacy: () -> Unit,
    onFeedback: () -> Unit, onExportLogs: () -> Unit,
    onUpdateChannel: (Int) -> Unit, onCheckUpdate: () -> Unit,
) {

    val dialogs = remember { DialogRequests() }
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    fun launchAction(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dialogs.showMessage(getString(Res.string.notice_title), e.displayMessage())
            }
        }
    }
    dialogs.Render()
    val updateChannel = UpdateChannelOption.objects.findOption(updateChannelValue)
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
                            onBack()
                        },
                    )
                },
                title = { Text(text = stringResource(Res.string.about_title)) },
                actions = {
                    GkIconButton(
                        imageVector = GkIcons.Share,
                        onClick = { onShare() },
                    )
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GkAnimatedLogoIcon(
                    tint = Color(if (LocalDarkTheme.current) 0xFFFCFCFC else 0xFF111111),
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onLogo
                        )
                        .fillMaxWidth(0.33f)
                        .aspectRatio(1f)
                )
                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClick = { onVersion() })
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = appName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = versionName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            GkSettingItem(
                imageVector = null,
                title = stringResource(Res.string.source_code),
                onClick = {
                    onSource()
                },
            )
            if (isGkdChannel) {
                GkSettingItem(
                    imageVector = null,
                    title = stringResource(Res.string.donate),
                    onClick = {
                        onDonate()
                    },
                )
            }
            GkSettingItem(
                imageVector = null,
                title = stringResource(Res.string.terms_of_use),
                onClick = {
                    onTerms()
                },
            )
            GkSettingItem(
                imageVector = null,
                title = stringResource(Res.string.privacy_policy),
                onClick = {
                    onPrivacy()
                },
            )

            FeedbackSection {
                launchAction {
                    if (dialogs.confirm(
                            title = getString(Res.string.feedback_notice),
                            text = feedbackNotice(primary),
                            confirmText = getString(Res.string.action_continue),
                            dismissOnRequest = true,
                        )
                    ) onFeedback()
                }
            }
            GkSettingItem(
                title = stringResource(Res.string.logs_export),
                imageVector = GkIcons.Share,
                onClick = {
                    onExportLogs()
                }
            )
            if (updateAvailable) {
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
                            if (option != UpdateChannelOption.Beta || dialogs.confirm(
                                    title = getString(Res.string.version_channel),
                                    text = getString(Res.string.beta_channel_warning),
                                )
                            ) onUpdateChannel(option.value)
                        }
                    },
                )

                Row(
                    modifier = Modifier
                        .clickable(
                            onClick = onCheckUpdate
                        )
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
}

@Composable
private fun FeedbackSection(onClick: () -> Unit) {
    Text(
        stringResource(Res.string.feedback_title), modifier = Modifier.titleItemPadding(),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary
    )
    Column(Modifier.clickable(onClick = onClick).fillMaxWidth().itemPadding()) {
        Text(stringResource(Res.string.feedback_report), style = MaterialTheme.typography.bodyLarge)
    }
}
