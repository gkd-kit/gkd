package li.gkd.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import li.gkd.app.error.displayMessage
import li.gkd.app.platform.PackageInstallCapability
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.stringResource

sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState

    data object Active : UpdateInstallState

    data object Success : UpdateInstallState

    data class Failure(val exception: Exception) : UpdateInstallState
}

sealed interface UpdateDownloadState {
    data class Loading(val progress: Float) : UpdateDownloadState

    data class Failure(val exception: Exception) : UpdateDownloadState

    data object Success : UpdateDownloadState
}

@Composable
fun GkUpgradeDialogs(
    text: String?,
    allowIgnore: Boolean,
    downloadStatus: UpdateDownloadState?,
    onDownload: () -> Unit,
    onDismissVersion: () -> Unit,
    onIgnore: () -> Unit,
    onBackup: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismissDownload: () -> Unit,
    onInstall: () -> Unit,
    installCapability: PackageInstallCapability,
    installState: UpdateInstallState,
    downloadedVersion: String?,
    onInternalInstall: () -> Unit,
) {
    if (text != null) {
        val scrollState = rememberScrollState()
        GkAlertDialog(
            title = {
                UpdateDialogTitle(
                    title = stringResource(Res.string.update_new_version),
                    onClose = onDismissVersion,
                )
            },
            text = {
                Text(
                    text = text,
                    modifier =
                        Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(scrollState),
                )
            },
            onDismissRequest = {},
            buttons = {
                UpdateVersionButtons(
                    allowIgnore = allowIgnore,
                    onBackup = onBackup,
                    onIgnore = onIgnore,
                    onDownload = onDownload,
                )
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
                        TextButton(
                            onClick = {
                                onCancelDownload()
                            },
                        ) {
                            Text(text = stringResource(Res.string.download_abort))
                        }
                    },
                )
            }

            is UpdateDownloadState.Failure -> {
                GkAlertDialog(
                    title = { Text(text = stringResource(Res.string.download_failed)) },
                    text = {
                        Text(
                            text =
                                downloadStatusVal.exception.let {
                                    it.message ?: it.toString()
                                },
                        )
                    },
                    onDismissRequest = { onDismissDownload() },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onDismissDownload()
                            },
                        ) {
                            Text(text = stringResource(Res.string.action_close))
                        }
                    },
                )
            }

            UpdateDownloadState.Success -> {
                val installing = installState is UpdateInstallState.Active
                val installed = installState == UpdateInstallState.Success
                GkAlertDialog(
                    title = {
                        UpdateDialogTitle(
                            title =
                                stringResource(
                                    when (installState) {
                                        UpdateInstallState.Success -> Res.string.update_installed
                                        is UpdateInstallState.Failure ->
                                            Res.string.update_install_failed
                                        UpdateInstallState.Idle,
                                        UpdateInstallState.Active -> Res.string.download_complete
                                    },
                                ),
                            onClose = onDismissDownload,
                            closeEnabled = !installing,
                        )
                    },
                    text = {
                        Column(
                            modifier =
                                Modifier.heightIn(max = 400.dp)
                                    .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text =
                                    buildString {
                                        downloadedVersion?.let { append("v$it\n\n") }
                                        append(
                                            when (installState) {
                                                UpdateInstallState.Success ->
                                                    stringResource(
                                                        Res.string.update_installed_description,
                                                    )
                                                is UpdateInstallState.Failure ->
                                                    installState.exception.displayMessage()
                                                UpdateInstallState.Idle,
                                                UpdateInstallState.Active ->
                                                    stringResource(
                                                        Res.string.update_ready_to_install,
                                                    )
                                            },
                                        )
                                    },
                            )
                        }
                    },
                    onDismissRequest = { if (!installing) onDismissDownload() },
                    textToButtonsSpacing = if (installed) 0.dp else null,
                    confirmButton = {
                        if (!installed) {
                            TextButton(onClick = onInstall, enabled = !installing) {
                                Text(
                                    text =
                                        stringResource(
                                            if (
                                                installCapability ==
                                                    PackageInstallCapability.Available
                                            )
                                                Res.string.update_system_install
                                            else Res.string.action_install,
                                        ),
                                )
                            }
                            if (installCapability == PackageInstallCapability.Available) {
                                TextButton(onClick = onInternalInstall, enabled = !installing) {
                                    Text(
                                        text =
                                            stringResource(
                                                if (installing) Res.string.update_installing
                                                else Res.string.action_install,
                                            ),
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun UpdateDialogTitle(
    title: String,
    onClose: () -> Unit,
    closeEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        GkIconButton(
            imageVector = GkIcons.Close,
            onClick = onClose,
            modifier = Modifier.size(48.dp),
            enabled = closeEnabled,
            contentDescription = stringResource(Res.string.action_close),
        )
    }
}

@Composable
private fun UpdateVersionButtons(
    allowIgnore: Boolean,
    onBackup: () -> Unit,
    onIgnore: () -> Unit,
    onDownload: () -> Unit,
) {
    val backup = stringResource(Res.string.backup_action)
    val ignore = stringResource(Res.string.action_ignore)
    val download = stringResource(Res.string.update_download)
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelLarge
    val layoutDirection = LocalLayoutDirection.current
    val buttonWidths =
        with(LocalDensity.current) {
            listOf(
                    backup to ButtonDefaults.TextButtonContentPadding,
                    ignore to ButtonDefaults.TextButtonContentPadding,
                    download to ButtonDefaults.ContentPadding,
                )
                .map { (label, padding) ->
                    maxOf(
                        ButtonDefaults.MinWidth,
                        textMeasurer.measure(label, textStyle).size.width.toDp() +
                            padding.calculateLeftPadding(layoutDirection) +
                            padding.calculateRightPadding(layoutDirection),
                    )
                }
        }
    val visibleWidths = if (allowIgnore) buttonWidths else listOf(buttonWidths[0], buttonWidths[2])
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val singleRow =
            visibleWidths.fold(0.dp) { sum, width -> sum + width } +
                8.dp * (visibleWidths.size - 1) <= maxWidth
        val columns =
            when {
                singleRow -> visibleWidths.size
                visibleWidths.max() * 2 + 8.dp <= maxWidth -> 2
                else -> 1
            }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = columns,
        ) {
            val buttonModifiers = buttonWidths.map {
                if (singleRow) Modifier.width(it) else Modifier.weight(1f)
            }
            TextButton(
                onClick = onBackup,
                modifier = buttonModifiers[0],
            ) {
                Text(backup)
            }
            if (allowIgnore) {
                TextButton(
                    onClick = onIgnore,
                    modifier = buttonModifiers[1],
                ) {
                    Text(ignore)
                }
            }
            if (!singleRow && columns == 2) {
                Spacer(Modifier.weight(1f))
            }
            Button(
                onClick = onDownload,
                modifier = buttonModifiers[2],
            ) {
                Text(download)
            }
        }
    }
}
