package li.gkd.app.ui.component

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.MainActivity
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.util.ShortUrlSet
import li.gkd.app.util.throttle


@Composable
fun TermsAcceptDialog() {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val modifier = Modifier.fillMaxWidth()
    val stepDataList = remember {
        arrayOf(
            "使用声明" to @Composable {
                val linkStyles = TextLinkStyles(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                )
                Text(
                    modifier = modifier,
                    text = buildAnnotatedString {
                        append("感谢使用 GKD！您需要阅读并同意「")
                        withLink(
                            LinkAnnotation.Url(
                                ShortUrlSet.URL12,
                                linkStyles
                            )
                        ) {
                            append("用户协议")
                        }
                        append("」和「")
                        withLink(
                            LinkAnnotation.Url(
                                ShortUrlSet.URL11,
                                linkStyles
                            )
                        ) {
                            append("隐私政策")
                        }
                        append("」才能继续使用, 请仔细阅读相关内容")
                    },
                )
            },
            "关于无障碍" to @Composable {
                Text(
                    modifier = modifier,
                    text = "GKD 请求使用系统「无障碍 API」获取屏幕信息, 以此基于用户自定义订阅规则执行自动化操作",
                )
            }
        )
    }
    val step by mainVm.termsStepFlow.collectAsStateWithLifecycle()

    AppAlertDialog(
        onDismissRequest = {},
        title = {
            Text(text = stepDataList[step].first)
        },
        text = stepDataList[step].second,
        confirmButton = {
            TextButton(onClick = throttle {
                mainVm.acceptTermsStep(stepDataList.lastIndex)
            }) {
                Text(text = "同意")
            }
        },
        dismissButton = {
            TextButton(onClick = throttle {
                context.finish()
            }) {
                Text(text = "不同意")
            }
        }
    )
}
