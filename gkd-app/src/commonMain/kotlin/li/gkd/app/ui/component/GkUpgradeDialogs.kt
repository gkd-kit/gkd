package li.gkd.app.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_cancel
import li.gkd.app.resources.action_close
import li.gkd.app.resources.action_ignore
import li.gkd.app.resources.action_install
import li.gkd.app.resources.download_abort
import li.gkd.app.resources.download_complete
import li.gkd.app.resources.download_failed
import li.gkd.app.resources.image_downloading
import li.gkd.app.resources.update_download
import li.gkd.app.resources.update_new_version
import li.gkd.app.resources.update_ready_to_install
import org.jetbrains.compose.resources.stringResource

sealed interface UpdateDownloadState {
    data class Loading(val progress: Float) : UpdateDownloadState
    data class Failure(val exception: Exception) : UpdateDownloadState
    data object Success : UpdateDownloadState
}

@Composable
fun GkUpgradeDialogs(
    text: String?, allowIgnore: Boolean, downloadStatus: UpdateDownloadState?,
    onDownload: () -> Unit, onDismissVersion: () -> Unit, onIgnore: () -> Unit,
    onCancelDownload: () -> Unit, onDismissDownload: () -> Unit, onInstall: () -> Unit,
) {
    if (text != null) {
        val scrollState = rememberScrollState()
        GkAlertDialog(
            title = {
                Text(text = stringResource(Res.string.update_new_version))
            },
            text = {
                Text(
                    text = text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(scrollState)
                )
            },
            onDismissRequest = { },
            confirmButton = {
                TextButton(onClick = {
                    onDownload()
                }) {
                    Text(text = stringResource(Res.string.update_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissVersion() }) {
                    Text(text = stringResource(Res.string.action_cancel))
                }
                if (allowIgnore) {
                    TextButton(onClick = {
                        onIgnore()
                    }) {
                        Text(text = stringResource(Res.string.action_ignore))
                    }
                }
            },
        )
    }
    downloadStatus?.let { downloadStatusVal ->
        when (downloadStatusVal) {
            is UpdateDownloadState.Loading -> {
                GkAlertDialog(
                    title = { Text(text = stringResource(Res.string.image_downloading)) },
                    text = {
                        LinearProgressIndicator(
                            progress = { downloadStatusVal.progress },
                        )
                    },
                    onDismissRequest = {},
                    confirmButton = {
                        TextButton(onClick = {
                            onCancelDownload()
                        }) {
                            Text(text = stringResource(Res.string.download_abort))
                        }
                    },
                )
            }

            is UpdateDownloadState.Failure -> {
                GkAlertDialog(
                    title = { Text(text = stringResource(Res.string.download_failed)) },
                    text = {
                        Text(text = downloadStatusVal.exception.let {
                            it.message ?: it.toString()
                        })
                    },
                    onDismissRequest = { onDismissDownload() },
                    confirmButton = {
                        TextButton(onClick = {
                            onDismissDownload()
                        }) {
                            Text(text = stringResource(Res.string.action_close))
                        }
                    },
                )
            }

            UpdateDownloadState.Success -> {
                GkAlertDialog(
                    title = { Text(text = stringResource(Res.string.download_complete)) },
                    text = {
                        Text(text = stringResource(Res.string.update_ready_to_install))
                    },
                    onDismissRequest = {},
                    dismissButton = {
                        TextButton(onClick = {
                            onDismissDownload()
                        }) {
                            Text(text = stringResource(Res.string.action_close))
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = onInstall) {
                            Text(text = stringResource(Res.string.action_install))
                        }
                    })
            }
        }
    }
}
