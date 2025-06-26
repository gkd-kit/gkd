package li.songe.gkd.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.generated.destinations.WebViewPageDestination
import li.songe.gkd.ui.local.LocalMainViewModel
import li.songe.gkd.util.ShortUrlSet
import li.songe.gkd.util.copyText
import li.songe.gkd.util.throttle

@Composable
fun ManualAuthDialog(
    commandText: String,
    show: Boolean,
    onUpdateShow: (Boolean) -> Unit,
) {
    if (show) {
        val mainVm = LocalMainViewModel.current
        val adbCommandText = remember(commandText) {
            "adb shell \"$commandText\""
        }
        AlertDialog(
            onDismissRequest = { onUpdateShow(false) },
            title = { Text(text = "手动授权") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "1. 有一台安装了 adb 的电脑\n\n2.手机开启调试模式后连接电脑授权调试\n\n3. 在电脑 cmd/pwsh 中运行如下命令")
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = adbCommandText,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clickable(onClick = throttle {
                                    copyText(adbCommandText)
                                })
                                .padding(4.dp)
                                .size(20.dp),
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        modifier = Modifier
                            .clickable(onClick = throttle {
                                onUpdateShow(false)
                                mainVm.navigatePage(WebViewPageDestination(initUrl = ShortUrlSet.URL3))
                            }),
                        text = "运行后授权失败?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateShow(false)
                }) {
                    Text(text = "关闭")
                }
            },
        )
    }
}