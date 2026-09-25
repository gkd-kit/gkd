package li.gkd.app.feature.settings

import li.gkd.app.MainViewModel

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import li.gkd.app.text.UiStrings
import li.gkd.app.META
import li.gkd.app.MainActivity
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkTextListDialog
import li.gkd.app.util.PLAY_STORE_URL
import li.gkd.app.util.ShortUrlSet
import li.gkd.app.util.format
import li.gkd.app.util.getShareApkFile
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.util.IntentUtils

@Composable
fun AboutDialogs(
    showVersionInfo: Boolean,
    onDismissVersionInfo: () -> Unit,
    showShareApp: Boolean,
    onDismissShareApp: () -> Unit,
) {
    VersionInfoDialog(showVersionInfo, onDismissVersionInfo)
    ShareAppDialog(showShareApp, onDismissShareApp)
}

@Composable
private fun VersionInfoDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    if (visible) {
        GkAlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = UiStrings.version_info) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text(text = UiStrings.build_channel)
                        Text(text = META.channel)
                    }
                    Column {
                        Text(text = UiStrings.version_code)
                        Text(text = META.versionCode.toString())
                    }
                    Column {
                        Text(text = UiStrings.version_name)
                        Text(text = META.versionName)
                    }
                    Column {
                        Text(text = UiStrings.code_history)
                        Text(
                            modifier = Modifier.clickable { IntentUtils.openUri(META.commitUrl) },
                            text = META.tagName ?: META.commitId.substring(0, 16),
                            color = MaterialTheme.colorScheme.primary,
                            style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                        )
                    }
                    Column {
                        Text(text = UiStrings.commit_time)
                        Text(text = META.commitTime.format("yyyy-MM-dd HH:mm:ss ZZ"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = UiStrings.action_close)
                }
            },
        )
    }
}

@Composable
private fun ShareAppDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    val context = LocalActivity.current as MainActivity
    val mainVm = MainViewModel.requireCurrent()
    if (visible) {
        val exportPlayTipText = buildAnnotatedString {
            append(UiStrings.apk_google_services_required)
            withLink(
                LinkAnnotation.Url(
                    ShortUrlSet.URL13,
                    TextLinkStyles(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    )
                )
            ) {
                append(UiStrings.apk_download_official_link)
            }
            append(UiStrings.apk_continue_suffix)
        }
        GkTextListDialog(
            onDismiss = onDismissRequest,
            textList = listOf(
                UiStrings.action_share to mainVm.scope.launchUiAction(Dispatchers.IO) {
                    if (!META.isGkdChannel) {
                        if (!mainVm.dialogRequests.confirm(
                            title = UiStrings.share_notice,
                            text = exportPlayTipText,
                            confirmText = UiStrings.action_continue,
                        )) return@launchUiAction
                    }
                    context.shareFile(getShareApkFile(), UiStrings.apk_share)
                },
                UiStrings.action_save_to_downloads to mainVm.scope.launchUiAction(Dispatchers.IO) {
                    if (!META.isGkdChannel) {
                        if (!mainVm.dialogRequests.confirm(
                            title = UiStrings.save_notice,
                            text = exportPlayTipText,
                            confirmText = UiStrings.action_continue,
                        )) return@launchUiAction
                    }
                    context.saveFileToDownloads(getShareApkFile())
                },
                UiStrings.google_play_label to {
                    mainVm.openUrl(PLAY_STORE_URL)
                },
            )
        )
    }
}
