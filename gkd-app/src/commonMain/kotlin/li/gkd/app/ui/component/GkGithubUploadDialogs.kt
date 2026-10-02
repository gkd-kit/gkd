package li.gkd.app.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_cancel
import li.gkd.app.resources.action_close
import li.gkd.app.resources.action_confirm
import li.gkd.app.resources.cookie_change
import li.gkd.app.resources.github_cookie_dialog_title
import li.gkd.app.resources.github_cookie_input_hint
import li.gkd.app.resources.upload_abort
import li.gkd.app.resources.upload_batch_progress
import li.gkd.app.resources.upload_batch_result
import li.gkd.app.resources.upload_batch_summary
import li.gkd.app.resources.upload_cancelled
import li.gkd.app.resources.upload_complete
import li.gkd.app.resources.upload_failed
import li.gkd.app.resources.upload_file_progress
import org.jetbrains.compose.resources.stringResource

sealed interface GithubUploadStatus {
    data class Loading(
        val batch: Boolean,
        val index: Int,
        val total: Int,
        val label: String,
        val progress: Float,
    ) : GithubUploadStatus

    data class Finished(
        val batch: Boolean,
        val links: List<String>,
        val failures: List<Pair<String, String>>,
        val remainingCount: Int,
        val cancelled: Boolean,
        val cookieExpired: Boolean,
    ) : GithubUploadStatus
}

@Composable
fun GkGithubUploadDialogs(
    cookieEditorVisible: Boolean, cookieDraft: String, status: GithubUploadStatus?,
    canSaveCookie: Boolean, onDismissCookie: () -> Unit, onCookieHelp: () -> Unit,
    onCookieChange: (String) -> Unit, onSaveCookie: () -> Unit, onStop: () -> Unit,
    onClose: () -> Unit, onEditCookie: () -> Unit,
) {
    if (cookieEditorVisible) {
        GkAlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            onDismissRequest = onDismissCookie,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(Res.string.github_cookie_dialog_title))
                    GkIconButton(
                        imageVector = GkIcons.HelpOutline,
                        onClick = onCookieHelp,
                    )
                }
            },
            text = {
                OutlinedTextField(
                    value = cookieDraft,
                    onValueChange = onCookieChange,
                    placeholder = { Text(text = stringResource(Res.string.github_cookie_input_hint)) },
                    modifier = Modifier.fillMaxWidth().autoFocus(),
                    maxLines = 10,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = canSaveCookie,
                    onClick = onSaveCookie,
                ) { Text(text = stringResource(Res.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissCookie) {
                    Text(text = stringResource(Res.string.action_cancel))
                }
            },
        )
    }
    when (val visibleStatus = if (cookieEditorVisible) null else status) {
        null -> Unit
        is GithubUploadStatus.Loading -> {
            GkAlertDialog(
                title = { Text(stringResource(Res.string.upload_file_progress)) },
                text = {
                    if (visibleStatus.batch) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                stringResource(
                                    Res.string.upload_batch_progress,
                                    visibleStatus.index,
                                    visibleStatus.total,
                                )
                            )
                            Text(visibleStatus.label)
                            UploadProgress(visibleStatus.progress)
                        }
                    } else {
                        UploadProgress(visibleStatus.progress)
                    }
                },
                onDismissRequest = {},
                confirmButton = {
                    TextButton(onClick = onStop) { Text(stringResource(Res.string.upload_abort)) }
                },
            )
        }

        is GithubUploadStatus.Finished -> {
            GkAlertDialog(
                properties = DialogProperties(
                    dismissOnClickOutside = visibleStatus.links.isEmpty(),
                ),
                title = {
                    Text(
                        when {
                            visibleStatus.cancelled -> stringResource(Res.string.upload_cancelled)
                            visibleStatus.batch -> stringResource(Res.string.upload_batch_result)
                            visibleStatus.remainingCount == 0 -> stringResource(Res.string.upload_complete)
                            else -> stringResource(Res.string.upload_failed)
                        }
                    )
                },
                text = {
                    if (visibleStatus.batch) {
                        Column(
                            modifier = Modifier.heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(
                                    Res.string.upload_batch_summary,
                                    visibleStatus.links.size,
                                    visibleStatus.remainingCount,
                                )
                            )
                            if (visibleStatus.links.isNotEmpty()) {
                                GkCopyTextCard(text = visibleStatus.links.joinToString("\n"))
                            }
                            visibleStatus.failures.take(5).forEach { (label, message) ->
                                Text("$label: $message", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else if (visibleStatus.remainingCount == 0) {
                        GkCopyTextCard(text = visibleStatus.links.single())
                    } else {
                        Text(visibleStatus.failures.first().second)
                    }
                },
                onDismissRequest = onClose,
                dismissButton = if (visibleStatus.cookieExpired) {
                    {
                        TextButton(onClick = onEditCookie) {
                            Text(stringResource(Res.string.cookie_change))
                        }
                    }
                } else null,
                confirmButton = {
                    TextButton(onClick = onClose) { Text(stringResource(Res.string.action_close)) }
                },
            )
        }
    }
}

@Composable
private fun UploadProgress(progress: Float) {
    val showExactProgress = progress > 0f && progress < 1f
    AnimatedContent(showExactProgress) { showExact ->
        if (showExact) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
