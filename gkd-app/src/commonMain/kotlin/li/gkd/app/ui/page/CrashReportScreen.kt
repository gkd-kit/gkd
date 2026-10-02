package li.gkd.app.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.crash.CrashData
import li.gkd.app.resources.Res
import li.gkd.app.resources.android_version_description
import li.gkd.app.resources.crash_app_description
import li.gkd.app.resources.crash_details_collapse
import li.gkd.app.resources.crash_details_expand
import li.gkd.app.resources.crash_device_description
import li.gkd.app.resources.crash_report_delete_current
import li.gkd.app.resources.crash_reports
import li.gkd.app.resources.crash_reports_clear
import li.gkd.app.resources.crash_reports_empty
import li.gkd.app.resources.crash_thread_description
import li.gkd.app.resources.crash_version_thread
import li.gkd.app.resources.exception_message_empty
import li.gkd.app.resources.exception_unknown
import li.gkd.app.resources.feedback_report
import li.gkd.app.resources.logs_export
import li.gkd.app.resources.stack_trace
import li.gkd.app.state.Loadable
import li.gkd.app.time.format
import li.gkd.app.ui.component.GkCopyTextCard
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkExpandableSection
import li.gkd.app.ui.component.GkFixedTimeText
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.itemVerticalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.ui.style.surfaceCardColors
import org.jetbrains.compose.resources.stringResource

@Composable
fun CrashReportScreen(
    crashDataState: Loadable<List<CrashData>>,
    expandedCrashId: Long?,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onDelete: (CrashData) -> Unit,
    onToggle: (Long) -> Unit,
    onReport: () -> Unit,
    onExport: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val crashDataList = crashDataState.value.orEmpty()
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnChange(crashDataList.isNotEmpty())
    GkScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = onBack,
                    )
                },
                title = {
                    Text(
                        text = stringResource(Res.string.crash_reports),
                        modifier = Modifier.noRippleClickable(onClick = pageScrollState::resetScroll)
                    )
                },
                actions = {
                    if (crashDataList.isNotEmpty()) {
                        GkIconButton(
                            imageVector = GkIcons.Delete,
                            contentDescription = stringResource(Res.string.crash_reports_clear),
                            onClick = onClear,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (crashDataList.isNotEmpty()) {
                BottomAppBar {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = onReport,
                    ) {
                        Text(text = stringResource(Res.string.feedback_report))
                    }
                    Spacer(modifier = Modifier.width(itemHorizontalPadding))
                    TextButton(
                        onClick = onExport,
                    ) {
                        Text(text = stringResource(Res.string.logs_export))
                    }
                    Spacer(modifier = Modifier.width(itemHorizontalPadding))
                }
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .scaffoldPadding(contentPadding)
                .fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(itemVerticalPadding),
        ) {
            items(
                items = crashDataList,
                key = { it.id },
            ) { crashData ->
                val expanded = expandedCrashId == crashData.id
                CrashReportCard(
                    crashData = crashData,
                    onCopy = onCopy,
                    expanded = expanded,
                    onToggle = {
                        onToggle(crashData.id)
                    },
                    onDelete = { onDelete(crashData) },
                )
            }
            item(key = "crash-report-footer") {
                if (crashDataList.isEmpty() && crashDataState !is Loadable.Loading) {
                    GkEmptyState(
                        text = (crashDataState as? Loadable.Failure)?.cause?.message
                            ?: stringResource(Res.string.crash_reports_empty),
                    )
                }
                GkPageBottomSpace()
            }
        }
    }
}

@Composable
private fun CrashReportCard(
    crashData: CrashData,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val exceptionName = crashData.name.substringAfterLast('.')
        .ifBlank { stringResource(Res.string.exception_unknown) }
    val message = crashData.message?.takeIf { it.isNotBlank() }
    val timeText = remember(crashData.mtime) {
        crashData.mtime.format("yyyy-MM-dd HH:mm:ss")
    }
    val supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier
            .padding(horizontal = itemHorizontalPadding / 2)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraSmall,
        colors = surfaceCardColors,
    ) {
        GkExpandableSection(
            expanded = expanded,
            onToggle = onToggle,
            expandLabel = stringResource(Res.string.crash_details_expand),
            collapseLabel = stringResource(Res.string.crash_details_collapse),
            header = { indicator ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
                        indicator()
                    }
                    Text(
                        text = message ?: stringResource(Res.string.exception_message_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                Res.string.crash_version_thread,
                                crashData.versionName,
                                crashData.versionCode,
                                crashData.thread
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
            },
        ) {
            HorizontalDivider()
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(
                                Res.string.crash_device_description,
                                crashData.device
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = supportingColor,
                        )
                        Text(
                            text = stringResource(
                                Res.string.android_version_description,
                                crashData.androidVersionName,
                                crashData.androidVersionCode
                            ),
                            modifier = Modifier.padding(end = 56.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = supportingColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                Res.string.crash_app_description,
                                crashData.versionName,
                                crashData.versionCode
                            ),
                            modifier = Modifier.padding(end = 56.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = supportingColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                Res.string.crash_thread_description,
                                crashData.thread
                            ),
                            modifier = Modifier.padding(end = 56.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = supportingColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                        GkIconButton(
                            imageVector = GkIcons.Delete,
                            onClick = onDelete,
                            contentDescription = stringResource(Res.string.crash_report_delete_current),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.stack_trace),
                    style = MaterialTheme.typography.titleSmall,
                )
                GkCopyTextCard(
                    text = crashData.stackTrace,
                    onCopy = onCopy,
                    modifier = Modifier.heightIn(max = 320.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
