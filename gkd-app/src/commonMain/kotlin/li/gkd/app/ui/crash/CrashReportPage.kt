package li.gkd.app.ui.crash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import li.gkd.app.crash.CrashData
import li.gkd.app.error.displayMessage
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.*
import li.gkd.app.state.Loadable
import li.gkd.app.time.format
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkCopyTextCard
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkFixedTimeText
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.gkTopAppBarScroll
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.platform.GkFullscreenDialog
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.itemVerticalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.ui.style.surfaceCardColors
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.copyText
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun CrashReportPage() {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { CrashReportViewModel(mainVm.takeCrashDataList()) }
    val records by vm.crashDataState.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    fun delete(record: CrashData?, beforeDelete: suspend () -> Unit = {}) {
        vm.scope.launch {
            try {
                if (
                    mainVm.dialogRequests.confirm(
                        title =
                            getString(
                                if (record == null) Res.string.crash_reports_clear
                                else Res.string.crash_report_delete,
                            ),
                        text =
                            getString(
                                if (record == null) Res.string.crash_reports_clear_confirmation
                                else Res.string.crash_report_delete_confirmation,
                            ),
                        error = true,
                    )
                ) {
                    beforeDelete()
                    if (record == null) vm.deleteAllCrashes() else vm.deleteCrash(record)
                    ToastUtils.show(getString(Res.string.delete_success))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ToastUtils.show(e.displayMessage())
            }
        }
    }

    val crashDataList = records.value.orEmpty()
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnChange(crashDataList.isNotEmpty())
    GkScaffold(
        modifier = Modifier.gkTopAppBarScroll(scrollBehavior),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = mainVm.navigator::pop,
                    )
                },
                title = {
                    Text(
                        text = stringResource(Res.string.crash_reports),
                        modifier =
                            Modifier.noRippleClickable(onClick = pageScrollState::resetScroll),
                    )
                },
                actions = {
                    if (crashDataList.isNotEmpty()) {
                        GkIconButton(
                            imageVector = GkIcons.Delete,
                            contentDescription = stringResource(Res.string.crash_reports_clear),
                            onClick = { delete(null) },
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (crashDataList.isNotEmpty()) {
                BottomAppBar {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { mainVm.textDialog.showUrl(AppLinks.Issues) }) {
                        Text(text = stringResource(Res.string.feedback_report))
                    }
                    Spacer(modifier = Modifier.width(itemHorizontalPadding))
                    TextButton(onClick = mainVm.shareLog::show) {
                        Text(text = stringResource(Res.string.logs_export))
                    }
                    Spacer(modifier = Modifier.width(itemHorizontalPadding))
                }
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding).fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(itemVerticalPadding),
        ) {
            items(
                items = crashDataList,
                key = { it.id },
            ) { crashData ->
                CrashReportCard(
                    crashData = crashData,
                    onClick = { vm.show(crashData) },
                )
            }
            item(key = "crash-report-footer") {
                if (crashDataList.isEmpty() && records !is Loadable.Loading) {
                    GkEmptyState(
                        text = stringResource(Res.string.crash_reports_empty),
                    )
                }
                GkPageBottomSpace()
            }
        }
    }
    crashDataList
        .find { it.id == selected }
        ?.let { record ->
            val detailsClosed = remember(record.id) { CompletableDeferred<Unit>() }
            DisposableEffect(detailsClosed) {
                onDispose { detailsClosed.complete(Unit) }
            }
            CrashReportDialog(
                crashData = record,
                onDismiss = vm::dismiss,
                onDelete = {
                    delete(record) {
                        vm.dismiss()
                        detailsClosed.await()
                        // Finish disposing the dialog subtree before touching its record.
                        yield()
                    }
                },
            )
        }
}

@Composable
private fun CrashReportCard(
    crashData: CrashData,
    onClick: () -> Unit,
) {
    val exceptionName =
        crashData.name.substringAfterLast('.').ifBlank {
            stringResource(Res.string.exception_unknown)
        }
    val message = crashData.message?.takeIf { it.isNotBlank() }
    val timeText = remember(crashData.mtime) { crashData.mtime.format("yyyy-MM-dd HH:mm:ss") }
    val supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier.padding(horizontal = itemHorizontalPadding / 2).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraSmall,
        colors = surfaceCardColors,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(
                        onClickLabel = stringResource(Res.string.crash_details_view),
                        onClick = onClick,
                    )
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = exceptionName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(4.dp))
                GkIcon(imageVector = GkIcons.KeyboardArrowRight, contentDescription = null)
            }
            Text(
                text = message ?: stringResource(Res.string.exception_message_empty),
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (message == null) supportingColor.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        stringResource(
                            Res.string.crash_version_thread,
                            crashData.versionName,
                            crashData.versionCode,
                            crashData.thread,
                        ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = supportingColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(8.dp))
                GkFixedTimeText(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = supportingColor,
                )
            }
        }
    }
}

@Composable
private fun CrashReportDialog(
    crashData: CrashData,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    GkFullscreenDialog(onDismiss) {
        GkScaffold(
            topBar = {
                GkTopAppBar(
                    title = { Text(stringResource(Res.string.crash_details)) },
                    actions = {
                        GkIconButton(
                            imageVector = GkIcons.Close,
                            contentDescription = stringResource(Res.string.action_close),
                            onClick = onDismiss,
                        )
                    },
                )
            },
            bottomBar = {
                BottomAppBar {
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onDelete) {
                        Text(text = stringResource(Res.string.action_delete))
                    }
                    Spacer(Modifier.width(itemHorizontalPadding))
                }
            },
        ) { contentPadding ->
            Column(
                modifier =
                    Modifier.scaffoldPadding(contentPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = itemHorizontalPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text =
                                crashData.name.ifBlank {
                                    stringResource(Res.string.exception_unknown)
                                },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        GkFixedTimeText(
                            text =
                                remember(crashData.mtime) {
                                    crashData.mtime.format("yyyy-MM-dd HH:mm:ss")
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text =
                                crashData.message?.takeIf { it.isNotBlank() }
                                    ?: stringResource(Res.string.exception_message_empty),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text =
                                    stringResource(
                                        Res.string.crash_device_description,
                                        crashData.device,
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text =
                                    if (crashData.androidVersionCode > 0) {
                                        stringResource(
                                            Res.string.crash_system_android_description,
                                            crashData.androidVersionName,
                                            crashData.androidVersionCode,
                                        )
                                    } else {
                                        stringResource(
                                            Res.string.crash_system_desktop_description,
                                            crashData.device.removePrefix("Desktop "),
                                            crashData.androidVersionName,
                                        )
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text =
                                    stringResource(
                                        Res.string.crash_app_description,
                                        crashData.versionName,
                                        crashData.versionCode,
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text =
                                    stringResource(
                                        Res.string.crash_thread_description,
                                        crashData.thread,
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                GkCopyTextCard(
                    text = crashData.stackTrace,
                    onCopy = ::copyText,
                    modifier = Modifier.heightIn(max = 320.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                GkPageBottomSpace()
            }
        }
    }
}
