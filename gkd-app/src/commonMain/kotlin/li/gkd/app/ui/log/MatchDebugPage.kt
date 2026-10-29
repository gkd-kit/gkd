package li.gkd.app.ui.log

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.resources.*
import li.gkd.app.state.Loadable
import li.gkd.app.storage.createLogArchive
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.*
import li.gkd.app.ui.platform.GkBackHandler
import li.gkd.app.ui.platform.UiHost
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.share.shareArchive
import org.jetbrains.compose.resources.stringResource

@Composable
fun MatchDebugPage(host: UiHost) {
    val main = MainViewModel.requireCurrent()
    val vm = viewModel { MatchDebugViewModel() }
    val scope = rememberCoroutineScope()
    val diagnostics = MatchingRuntime.coordinator.diagnostics
    val control =
        remember(diagnostics) {
            diagnostics.state.map { it?.id to (it?.active == true) }.distinctUntilChanged()
        }
    val current by
        control.collectAsStateWithLifecycle(
            initialValue =
                diagnostics.state.value.let {
                    it?.id to (it?.active == true)
                },
        )
    val active = current.second
    val records by vm.records.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val formatter = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }
    val selection by vm.selection.state.collectAsStateWithLifecycle()
    val allRecords = records.value.orEmpty()
    val keys = allRecords.mapTo(mutableSetOf()) { it.file.name }
    val selectedKeys = selection.selectedKeys intersect keys
    val targets = allRecords.filter { it.file.name in selectedKeys }
    GkBackHandler(selection.active) { vm.selection.clear() }
    val exportTitle = stringResource(Res.string.logs_share_file)
    GkScaffold(
        topBar = {
            GkMultiSelectionTopAppBar(
                selectedMode = selection.active,
                selectedCount = selectedKeys.size,
                onExitSelection = vm.selection::clear,
                title = { Text(stringResource(Res.string.match_debug_records)) },
                onNavigateBack = { main.navigator.pop() },
                actions = { selecting ->
                    if (selecting)
                        GkMultiSelectionActions(
                            selected = selectedKeys,
                            keys = keys,
                            onSelectAll = vm.selection::selectAll,
                            onInvert = vm.selection::invert,
                        ) { dismiss ->
                            GkBatchActionMenuItem(
                                stringResource(Res.string.action_share),
                                dismiss,
                                onClick = {
                                    scope.launchUi {
                                        host.shareArchive(exportTitle) { createLogArchive(targets) }
                                    }
                                },
                            )
                            GkBatchActionMenuItem(
                                stringResource(Res.string.action_delete),
                                dismiss,
                                onClick = { vm.delete(targets) },
                            )
                        }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            when (val state = records) {
                Loadable.Loading -> item { Text(stringResource(Res.string.loading_progress)) }
                is Loadable.Failure ->
                    item {
                        Text(
                            stringResource(
                                Res.string.match_debug_read_failed,
                                state.cause.message.orEmpty(),
                            ),
                        )
                    }
                is Loadable.Ready -> {
                    if (state.value.isEmpty())
                        item {
                            Text(
                                stringResource(
                                    if (active) Res.string.match_debug_collecting_empty
                                    else Res.string.match_debug_empty,
                                ),
                                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    items(state.value, key = { it.file.name }) { record ->
                        DiagnosticRecordCard(
                            time = formatter.format(Instant.ofEpochMilli(record.startedAt)),
                            id = record.id,
                            sizeBytes = record.sizeBytes,
                            selectedMode = selection.active,
                            selected = record.file.name in selectedKeys,
                            onClick = {
                                if (selection.active) vm.selection.toggle(record.file.name)
                                else vm.show(record)
                            },
                            onLongClick = { vm.selection.select(record.file.name) },
                        )
                    }
                }
            }
            gkPageBottomSpace()
        }
    }
    GkRetainedSheet(
        request = selected,
        snapshot = allRecords.find { it.file == selected?.key?.file },
        missing =
            selected != null &&
                records is Loadable.Ready &&
                allRecords.none { it.file == selected?.key?.file },
        onDismissRequest = vm::dismiss,
    ) { _, snapshot, sheetState, dismiss ->
        val record = snapshot
        GkModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState) {
            Column(
                Modifier.align(Alignment.CenterHorizontally)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(
                    Modifier.padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        stringResource(Res.string.match_debug_records),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "ID · ${record.id} · ${formatDiagnosticSize(record.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GkFixedTimeText(
                            formatter.format(Instant.ofEpochMilli(record.startedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DiagnosticActionRow(GkIcons.Share, stringResource(Res.string.action_share)) {
                    dismiss()
                    scope.launchUi {
                        host.shareArchive(exportTitle) { createLogArchive(listOf(record)) }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                DiagnosticActionRow(GkIcons.Delete, stringResource(Res.string.action_delete)) {
                    dismiss()
                    vm.delete(listOf(record))
                }
            }
        }
    }
}

@Composable
private fun DiagnosticActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GkIcon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DiagnosticRecordCard(
    time: String,
    id: String,
    sizeBytes: Long,
    selectedMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val containerColor by
        animateColorAsState(
            if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
            label = "Diagnostic card background",
        )
    Card(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onClickLabel =
                            stringResource(
                                if (selectedMode) Res.string.selection_toggle
                                else Res.string.details_view,
                            ),
                        onLongClickLabel = stringResource(Res.string.selection_mode_enter),
                    )
                    .semantics {
                        if (selectedMode) {
                            role = Role.Checkbox
                            this.selected = selected
                        }
                    }
                    .heightIn(min = 64.dp)
                    .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (selectedMode) GkCheckbox(checked = selected, onCheckedChange = null)
                else
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            Column(Modifier.weight(1f)) {
                Text(time, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "ID · $id · ${formatDiagnosticSize(sizeBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatDiagnosticSize(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
    }
