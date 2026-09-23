package li.gkd.app.ui.component

import android.webkit.URLUtil
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import li.gkd.app.text.UiStrings
import li.gkd.app.util.IntentUtils
import li.gkd.app.util.TimeUtils.throttle

private data class TextDialogRequest(
    val text: String,
    val openable: Boolean,
)

class TextDialogState {
    private val requestFlow = MutableStateFlow<TextDialogRequest?>(null)

    fun showText(text: String) {
        requestFlow.value = TextDialogRequest(
            text = text,
            openable = false,
        )
    }

    fun showUrl(url: String) {
        if (URLUtil.isNetworkUrl(url)) {
            requestFlow.value = TextDialogRequest(
                text = url,
                openable = true,
            )
        } else {
            IntentUtils.openUri(url)
        }
    }

    private fun dismiss() {
        requestFlow.value = null
    }

    private fun open(request: TextDialogRequest) {
        dismiss()
        IntentUtils.openUri(request.text)
    }

    @Composable
    fun Render() {
        val request by requestFlow.collectAsStateWithLifecycle()
        val currentRequest = request
        if (currentRequest != null) {
            val text = remember(currentRequest.text) { AnnotatedString(currentRequest.text) }
            GkAlertDialog(
                onDismissRequest = ::dismiss,
                title = {
                    Text(text = if (currentRequest.openable) UiStrings.link_view else UiStrings.text_view)
                },
                text = {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        GkCopyableText(
                            text = text,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = throttle(::dismiss)) {
                            Text(text = UiStrings.action_close)
                        }
                        if (currentRequest.openable) {
                            TextButton(onClick = throttle { open(currentRequest) }) {
                                Text(text = UiStrings.action_open)
                            }
                        }
                    }
                },
            )
        }
    }
}
