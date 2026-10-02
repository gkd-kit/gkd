package li.gkd.app.ui.component

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
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_close
import li.gkd.app.resources.action_open
import li.gkd.app.resources.link_view
import li.gkd.app.resources.text_view
import org.jetbrains.compose.resources.stringResource

private data class TextDialogRequest(
    val text: String,
    val openable: Boolean,
)

class TextDialogState(private val openUri: (String) -> Unit) {
    private val requestFlow = MutableStateFlow<TextDialogRequest?>(null)

    fun showText(text: String) {
        requestFlow.value = TextDialogRequest(
            text = text,
            openable = false,
        )
    }

    fun showUrl(url: String) {
        if ((url.startsWith("http://", true) || url.startsWith("https://", true))) {
            requestFlow.value = TextDialogRequest(
                text = url,
                openable = true,
            )
        } else {
            openUri(url)
        }
    }

    private fun dismiss() {
        requestFlow.value = null
    }

    private fun open(request: TextDialogRequest) {
        dismiss()
        openUri(request.text)
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
                    Text(
                        text = if (currentRequest.openable) stringResource(Res.string.link_view) else stringResource(
                            Res.string.text_view
                        )
                    )
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
                        TextButton(onClick = ::dismiss) {
                            Text(text = stringResource(Res.string.action_close))
                        }
                        if (currentRequest.openable) {
                            TextButton(onClick = { open(currentRequest) }) {
                                Text(text = stringResource(Res.string.action_open))
                            }
                        }
                    }
                },
            )
        }
    }
}
