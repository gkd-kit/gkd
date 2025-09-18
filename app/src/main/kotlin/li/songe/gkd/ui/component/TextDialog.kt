package li.songe.gkd.ui.component

import android.webkit.URLUtil
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.util.openUri
import li.songe.gkd.util.throttle

@Composable
fun TextDialog(
    textFlow: MutableStateFlow<String?>
) {
    val text = textFlow.collectAsState().value
    if (text != null) {
        val isUri = remember(text) { URLUtil.isNetworkUrl(text) }
        val onDismissRequest = {
            textFlow.value = null
        }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = if (isUri) "查看链接" else "查看文本")
            },
            text = {
                CopyTextCard(text = text)
            },
            confirmButton = {
                if (isUri) {
                    TextButton(onClick = throttle {
                        onDismissRequest()
                        openUri(text)
                    }) {
                        Text(text = "打开")
                    }
                } else {
                    TextButton(onClick = onDismissRequest) {
                        Text(text = "关闭")
                    }
                }
            },
        )
    }
}
