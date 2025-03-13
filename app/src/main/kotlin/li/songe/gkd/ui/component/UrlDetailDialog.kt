package li.songe.gkd.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.util.openUri
import li.songe.gkd.util.throttle

@Composable
fun UrlDetailDialog(
    urlFlow: MutableStateFlow<String?>
) {
    val url = urlFlow.collectAsState().value
    if (url != null) {
        val onDismissRequest = {
            urlFlow.value = null
        }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = "链接详情")
            },
            text = {
                UrlCopyText(text = url)
            },
            confirmButton = {
                TextButton(onClick = throttle {
                    onDismissRequest()
                    openUri(url)
                }) {
                    Text(text = "打开")
                }
            },
        )
    }
}
