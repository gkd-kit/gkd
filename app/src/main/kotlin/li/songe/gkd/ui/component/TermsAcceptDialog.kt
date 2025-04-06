package li.songe.gkd.ui.component

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import li.songe.gkd.MainActivity
import li.songe.gkd.util.termsAcceptedFlow
import li.songe.gkd.util.throttle


@Composable
fun TermsAcceptDialog() {
    // https://support.google.com/googleplay/android-developer/answer/10144311
    // https://support.google.com/googleplay/android-developer/answer/10964491
    val context = LocalActivity.current as MainActivity
    val stepDataList = remember {
        arrayOf(
            "使用声明" to "GKD 不收集或分享任何用户个人数据",
            "关于无障碍" to "GKD 请求使用系统「无障碍 API」获取屏幕信息, 以此基于用户自定义订阅规则执行自动化操作"
        )
    }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val modifier = Modifier.fillMaxWidth()

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(text = stepDataList[step].first)
        },
        text = {
            Text(
                modifier = modifier,
                text = stepDataList[step].second,
            )
        },
        confirmButton = {
            TextButton(onClick = throttle {
                if (step < stepDataList.size - 1) {
                    step++
                } else {
                    termsAcceptedFlow.value = true
                }
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