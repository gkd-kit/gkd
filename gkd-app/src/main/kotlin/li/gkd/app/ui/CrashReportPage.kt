package li.gkd.app.ui

import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.share.launchUi
import li.gkd.app.MainViewModel

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.data.CrashData
import li.gkd.app.core.state.Loadable
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.itemVerticalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.ui.style.surfaceCardColors
import li.gkd.app.util.ISSUES_URL
import li.gkd.app.util.format
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.ui.component.GkCopyTextCard
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkExpandableSection
import li.gkd.app.ui.component.GkFixedTimeText
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.rememberListScrollState


@Serializable
data object CrashReportRoute : NavKey

@Composable
fun CrashReportPage() {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { CrashReportVm(mainVm.takeCrashDataList()) }
    val actionScope = vm.scope
    val crashDataState by vm.crashDataState.collectAsStateWithLifecycle()
    val crashDataList = crashDataState.value.orEmpty()
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnChange(crashDataList.isNotEmpty())
    val expandedCrashId = vm.expandedCrashId
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = mainVm::popPage,
                    )
                },
                title = {
                    Text(
                        text = UiStrings.crash_reports,
                        modifier = Modifier.noRippleClickable(onClick = throttle(pageScrollState::resetScroll))
                    )
                },
                actions = {
                    if (crashDataList.isNotEmpty()) {
                        GkIconButton(
                            imageVector = GkIcons.Delete,
                            contentDescription = UiStrings.crash_reports_clear,
                            onClick = throttle {
                                actionScope.launchUi {
                                    if (!mainVm.dialogRequests.confirm(
                                        title = UiStrings.crash_reports_clear,
                                        text = UiStrings.crash_reports_clear_confirmation,
                                        error = true,
                                    )) return@launchUi
                                    vm.deleteAllCrashes()
                                    toast(UiStrings.delete_success)
                                }
                            },
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
                        onClick = throttle { mainVm.openUrl(ISSUES_URL) },
                    ) {
                        Text(text = UiStrings.feedback_report)
                    }
                    Spacer(modifier = Modifier.width(itemHorizontalPadding))
                    TextButton(
                        onClick = { mainVm.shareLog.show() },
                    ) {
                        Text(text = UiStrings.logs_export)
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
                    expanded = expanded,
                    onToggle = {
                        vm.toggleCrash(crashData.id)
                    },
                    onDelete = throttle {
                        mainVm.confirmDelete(
                            title = UiStrings.crash_report_delete,
                            text = UiStrings.crash_report_delete_confirmation,
                        ) {
                            vm.deleteCrash(crashData)
                            toast(UiStrings.delete_success)
                        }
                    },
                )
            }
            item(key = "crash-report-footer") {
                if (crashDataList.isEmpty() && crashDataState !is Loadable.Loading) {
                    GkEmptyState(
                        text = (crashDataState as? Loadable.Failure)?.cause?.message
                            ?: UiStrings.crash_reports_empty,
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
) {
    val exceptionName = crashData.name.substringAfterLast('.').ifBlank { UiStrings.exception_unknown }
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
            expandLabel = UiStrings.crash_details_expand,
            collapseLabel = UiStrings.crash_details_collapse,
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
                        text = message ?: UiStrings.exception_message_empty,
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
                            text = UiStrings.crash_version_thread(crashData.versionName, crashData.versionCode, crashData.thread),
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
                            text = UiStrings.crash_device_description(crashData.device),
                            style = MaterialTheme.typography.bodySmall,
                            color = supportingColor,
                        )
                        Text(
                            text = UiStrings.android_version_description(crashData.androidVersionName, crashData.androidVersionCode),
                            modifier = Modifier.padding(end = 56.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = supportingColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = UiStrings.crash_app_description(crashData.versionName, crashData.versionCode),
                            modifier = Modifier.padding(end = 56.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = supportingColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = UiStrings.crash_thread_description(crashData.thread),
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
                            contentDescription = UiStrings.crash_report_delete_current,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = UiStrings.stack_trace,
                    style = MaterialTheme.typography.titleSmall,
                )
                GkCopyTextCard(
                    text = crashData.stackTrace,
                    modifier = Modifier.heightIn(max = 320.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
