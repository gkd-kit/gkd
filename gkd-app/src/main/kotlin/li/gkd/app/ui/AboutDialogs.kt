package li.gkd.app.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import li.gkd.app.META
import li.gkd.app.MainActivity
import li.gkd.app.ui.component.AppAlertDialog
import li.gkd.app.ui.component.TextListDialog
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.util.PLAY_STORE_URL
import li.gkd.app.util.ShortUrlSet
import li.gkd.app.util.format
import li.gkd.app.util.getShareApkFile
import li.gkd.app.util.launchAsFn
import li.gkd.app.util.openUri

@Composable
fun AboutDialogs() {
    VersionInfoDialog()
    ShareAppDialog()
}

@Composable
private fun VersionInfoDialog() {
    val vm = viewModel<AboutVm>()
    val visible by vm.showInfoDlgFlow.collectAsStateWithLifecycle()
    if (visible) {
        AppAlertDialog(
            onDismissRequest = { vm.setInfoDialogVisible(false) },
            title = { Text(text = "版本信息") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text(text = "构建渠道")
                        Text(text = META.channel)
                    }
                    Column {
                        Text(text = "版本代码")
                        Text(text = META.versionCode.toString())
                    }
                    Column {
                        Text(text = "版本名称")
                        Text(text = META.versionName)
                    }
                    Column {
                        Text(text = "代码记录")
                        Text(
                            modifier = Modifier.clickable { openUri(META.commitUrl) },
                            text = META.tagName ?: META.commitId.substring(0, 16),
                            color = MaterialTheme.colorScheme.primary,
                            style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                        )
                    }
                    Column {
                        Text(text = "提交时间")
                        Text(text = META.commitTime.format("yyyy-MM-dd HH:mm:ss ZZ"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setInfoDialogVisible(false) }) {
                    Text(text = "关闭")
                }
            },
        )
    }
}

@Composable
private fun ShareAppDialog() {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AboutVm>()
    val visible by vm.showShareAppDlgFlow.collectAsStateWithLifecycle()
    if (visible) {
        val exportPlayTipText = buildAnnotatedString {
            append("当前导出的 APK 文件只能在已安装 Google 框架的设备上才能使用，否则安装打开后会提示报错，")
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
                append("建议点此从官网下载")
            }
            append("，或点击下方继续操作")
        }
        TextListDialog(
            onDismiss = { vm.setShareAppDialogVisible(false) },
            textList = listOf(
                "分享到其他应用" to vm.scope.launchAsFn(Dispatchers.IO) {
                    if (!META.isGkdChannel) {
                        if (!mainVm.dialogRequests.confirm(
                            title = "分享提示",
                            text = exportPlayTipText,
                            confirmText = "继续",
                        )) return@launchAsFn
                    }
                    context.shareFile(getShareApkFile(), "分享安装文件")
                },
                "保存到下载" to vm.scope.launchAsFn(Dispatchers.IO) {
                    if (!META.isGkdChannel) {
                        if (!mainVm.dialogRequests.confirm(
                            title = "保存提示",
                            text = exportPlayTipText,
                            confirmText = "继续",
                        )) return@launchAsFn
                    }
                    context.saveFileToDownloads(getShareApkFile())
                },
                "Google Play" to {
                    mainVm.openUrl(PLAY_STORE_URL)
                },
            )
        )
    }
}
