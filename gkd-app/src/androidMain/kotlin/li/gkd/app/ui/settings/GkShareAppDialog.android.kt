package li.gkd.app.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import kotlinx.coroutines.Dispatchers
import li.gkd.app.META
import li.gkd.app.MainActivity
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_continue
import li.gkd.app.resources.apk_continue_suffix
import li.gkd.app.resources.apk_download_official_link
import li.gkd.app.resources.apk_google_services_required
import li.gkd.app.resources.apk_share
import li.gkd.app.resources.save_notice
import li.gkd.app.resources.share_notice
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.navigation.AppWindow
import li.gkd.app.ui.page.GkShareAppDialog
import li.gkd.app.ui.share.launchUiAction
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.ApkUtils
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun AppWindow.GkShareAppDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    val context = LocalActivity.current as MainActivity
    val mainVm = MainViewModel.requireCurrent()
    if (visible) {
        val exportPlayTipText = buildAnnotatedString {
            append(stringResource(Res.string.apk_google_services_required))
            withLink(
                LinkAnnotation.Url(
                    AppLinks.GoogleServicesHelp,
                    TextLinkStyles(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    )
                )
            ) {
                append(stringResource(Res.string.apk_download_official_link))
            }
            append(stringResource(Res.string.apk_continue_suffix))
        }
        GkShareAppDialog(
            onDismiss = onDismissRequest,
            onShare = mainVm.scope.launchUiAction(Dispatchers.IO) {
                if (!META.isGkdChannel) {
                    if (!mainVm.dialogRequests.confirm(
                            title = Res.string.share_notice.getSync(),
                            text = exportPlayTipText,
                            confirmText = Res.string.action_continue.getSync(),
                        )
                    ) return@launchUiAction
                }
                context.shareFile(ApkUtils.createShareFile(), Res.string.apk_share.getSync())
            },
            onSave = mainVm.scope.launchUiAction(Dispatchers.IO) {
                if (!META.isGkdChannel) {
                    if (!mainVm.dialogRequests.confirm(
                            title = Res.string.save_notice.getSync(),
                            text = exportPlayTipText,
                            confirmText = Res.string.action_continue.getSync(),
                        )
                    ) return@launchUiAction
                }
                context.saveFileToDownloads(ApkUtils.createShareFile())
            },
            onPlay = {
                mainVm.openUrl(AppLinks.PlayStore)
            },
        )
    }
}
