package li.gkd.app.ui.log

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import li.gkd.app.META
import li.gkd.app.app.AppCatalogState
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.app.launcherAppIdFlow
import li.gkd.app.error.messageResource
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.resources.*
import li.gkd.app.state.Loadable
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.*
import li.gkd.app.ui.image.GkAppIcon
import li.gkd.app.ui.platform.GkBackHandler
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.Constants
import li.gkd.db.MatchDiagnosticRecord
import org.jetbrains.compose.resources.stringResource

@Composable
fun MatchDebugPage() {
    val main = MainViewModel.requireCurrent()
    val vm = viewModel { MatchDebugViewModel() }
    val catalog by AppInfoRepository.state.collectAsStateWithLifecycle()
    val launcher by launcherAppIdFlow.collectAsStateWithLifecycle()
    val diagnostics = MatchingRuntime.coordinator.diagnostics
    val control =
        remember(diagnostics) {
            diagnostics.state
                .map { Triple(it?.id, it?.active == true, it?.startedAt) }
                .distinctUntilChanged()
        }
    val current by
        control.collectAsStateWithLifecycle(
            initialValue =
                diagnostics.state.value.let {
                    Triple(it?.id, it?.active == true, it?.startedAt)
                },
        )
    val active = current.second
    val records by vm.records.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val selection by vm.selection.state.collectAsStateWithLifecycle()
    val allRecords = records.value.orEmpty()
    val keys = allRecords.mapTo(mutableSetOf()) { it.id }
    val selectedKeys = selection.selectedKeys intersect keys
    val targets = allRecords.filter { it.id in selectedKeys }
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    pageScrollState.ResetOnChange(allRecords.isEmpty())
    GkBackHandler(selection.active) { vm.selection.clear() }
    GkScaffold(
        modifier = Modifier.gkTopAppBarScroll(scrollBehavior),
        topBar = {
            GkMultiSelectionTopAppBar(
                selectedMode = selection.active,
                selectedCount = selectedKeys.size,
                onExitSelection = vm.selection::clear,
                scrollBehavior = scrollBehavior,
                onTitleClick = pageScrollState::resetScroll,
                title = { Text(stringResource(Res.string.advanced_logs_diagnostics)) },
                onNavigateBack = { main.navigator.pop() },
                actions = { selecting ->
                    if (!selecting) {
                        GkIconButton(
                            imageVector = GkIcons.Info,
                            contentDescription = stringResource(Res.string.match_debug_help_title),
                            onClick = vm::showDiagnosticHelp,
                        )
                    }
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
                                    main.shareLog.show(targets)
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
            Modifier.fillMaxSize().scaffoldPadding(padding),
            state = pageScrollState.listState,
        ) {
            item(key = "capture") {
                DiagnosticCaptureCard(
                    active = active,
                    startedAt = current.third,
                    enabled = !selection.active,
                    onClick = {
                        if (active)
                            current.first?.let { MatchingRuntime.coordinator.stopDiagnostics(it) }
                        else vm.requestDiagnosticCapture()
                    },
                )
            }
            when (val state = records) {
                Loadable.Loading -> item { GkLoadingState() }
                is Loadable.Failure ->
                    item {
                        Text(
                            stringResource(
                                Res.string.match_debug_read_failed,
                                state.cause.messageResource(),
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
                    items(state.value, key = { it.id }) { record ->
                        DiagnosticRecordCard(
                            modifier = Modifier.animateListItem(),
                            record = record,
                            catalog = catalog,
                            launcher = launcher,
                            selectedMode = selection.active,
                            selected = record.id in selectedKeys,
                            onClick = {
                                if (selection.active) vm.selection.toggle(record.id)
                                else vm.show(record)
                            },
                            onLongClick = { vm.selection.select(record.id) },
                        )
                    }
                }
            }
            gkPageBottomSpace()
        }
    }
    GkRetainedSheet(
        request = selected,
        snapshot = allRecords.find { it.id == selected?.key?.id },
        missing =
            selected != null &&
                records is Loadable.Ready &&
                allRecords.none { it.id == selected?.key?.id },
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
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        stringResource(Res.string.match_debug_records),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "ID · ${record.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        GkFixedTimeText(
                            formatDiagnosticSize(record.sizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DiagnosticTimeRange(record)
                }
                DiagnosticAppIcons(
                    record.appIds,
                    catalog = catalog,
                    launcher = launcher,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                DiagnosticActionRow(GkIcons.Share, stringResource(Res.string.action_share)) {
                    dismiss()
                    main.shareLog.show(listOf(record))
                }
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
    modifier: Modifier,
    record: MatchDiagnosticRecord,
    catalog: AppCatalogState,
    launcher: String,
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
        modifier = modifier.padding(horizontal = 8.dp, vertical = 3.dp).fillMaxWidth(),
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
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val started = Instant.ofEpochMilli(record.startedAt).atZone(ZoneId.systemDefault())
                val pattern =
                    if (started.year == LocalDate.now().year) "MM-dd HH:mm:ss"
                    else "yyyy-MM-dd HH:mm:ss"
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GkFixedTimeText(
                        started.format(DateTimeFormatter.ofPattern(pattern)),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    GkFixedTimeText(
                        diagnosticDuration(record.durationMillis),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DiagnosticAppIcons(
                        record.appIds,
                        catalog = catalog,
                        launcher = launcher,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    GkFixedTimeText(
                        formatDiagnosticSize(record.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(
                visible = selectedMode,
                enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
            ) {
                GkCheckbox(
                    checked = selected,
                    modifier = Modifier.padding(start = 12.dp),
                    onCheckedChange = null,
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

@Composable
private fun DiagnosticTimeRange(record: MatchDiagnosticRecord) {
    val locale = Locale.getDefault()
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(record.startedAt).atZone(zone)
    val end = Instant.ofEpochMilli(record.stoppedAt).atZone(zone)
    val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val accessibleDateFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withDecimalStyle(DecimalStyle.of(locale))
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
    val startDate = start.format(dateFormatter)
    val endDate = end.format(dateFormatter)
    val startTime = start.format(timeFormatter)
    val endTime = end.format(timeFormatter)
    val duration = diagnosticDuration(record.durationMillis)
    val description =
        stringResource(
            Res.string.match_debug_time_range_description,
            "${start.format(accessibleDateFormatter)} $startTime",
            "${end.format(accessibleDateFormatter)} $endTime",
            duration,
        )
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (start.toLocalDate() == end.toLocalDate()) {
            GkFixedTimeText(
                startDate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GkFixedTimeText(startTime, style = MaterialTheme.typography.bodyMedium)
                GkFixedTimeText(
                    stringResource(Res.string.match_debug_range_end, endTime),
                    style = MaterialTheme.typography.bodyMedium,
                )
                GkFixedTimeText(
                    duration,
                    modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GkFixedTimeText(startDate, style = MaterialTheme.typography.bodyMedium)
                GkFixedTimeText(startTime, style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GkFixedTimeText(
                    stringResource(Res.string.match_debug_range_end, endDate),
                    style = MaterialTheme.typography.bodyMedium,
                )
                GkFixedTimeText(endTime, style = MaterialTheme.typography.bodyMedium)
                GkFixedTimeText(
                    duration,
                    modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private data class DiagnosticAppIcon(
    val appId: String?,
    val description: String,
    val count: Int = 1,
)

@Composable
private fun DiagnosticAppIcons(
    appIds: List<String>,
    catalog: AppCatalogState,
    launcher: String,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    val snapshot = catalog.snapshot
    val auxiliary = setOf(Constants.systemUiAppId, launcher, META.appId)
    val missing =
        if (
            snapshot != null &&
                !catalog.refreshing &&
                catalog.failure == null &&
                snapshot.canQueryPackages &&
                !snapshot.queryPackagesAbnormal
        ) {
            appIds.filter { it !in snapshot.apps && it !in auxiliary }
        } else emptyList()
    val installed =
        appIds
            .filterNot { it in missing }
            .map { appId ->
                val name = snapshot?.apps?.get(appId)?.name
                DiagnosticAppIcon(
                    appId,
                    if (name == null || name == appId) appId else "$name\n$appId",
                )
            }
    val icons =
        installed.filter { it.appId !in auxiliary } +
            (if (missing.isEmpty()) emptyList()
            else
                listOf(
                    DiagnosticAppIcon(
                        null,
                        stringResource(
                            Res.string.match_debug_uninstalled_apps,
                            missing.size.toString(),
                        ) + "\n" + missing.joinToString("\n"),
                        missing.size,
                    ),
                )) +
            installed.filter { it.appId in auxiliary }
    if (icons.isEmpty()) {
        Text(
            stringResource(Res.string.match_debug_no_apps),
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    val iconSize = if (singleLine) 24.dp else 32.dp
    if (singleLine) {
        BoxWithConstraints(modifier) {
            val slots = ((maxWidth + 8.dp) / (iconSize + 8.dp)).toInt().coerceAtLeast(1)
            val shown = if (icons.size > slots) slots - 1 else icons.size
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icons.take(shown).forEach { DiagnosticAppIconView(it, iconSize) }
                if (shown < icons.size) {
                    val hidden = icons.drop(shown)
                    val description = hidden.joinToString("\n\n") { it.description }
                    GkTooltipIconButtonBox(description) {
                        Box(
                            Modifier.size(iconSize).semantics { contentDescription = description },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("+${hidden.size}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    } else {
        FlowRow(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icons.forEach { DiagnosticAppIconView(it, iconSize, showOnClick = true) }
        }
    }
}

@Composable
private fun DiagnosticAppIconView(icon: DiagnosticAppIcon, size: Dp, showOnClick: Boolean = false) {
    GkTooltipIconButtonBox(
        contentDescription = icon.description,
        showOnClick = showOnClick,
    ) {
        Box(Modifier.size(size).semantics { contentDescription = icon.description }) {
            if (icon.appId != null) GkAppIcon(icon.appId, size)
            else li.gkd.app.ui.component.GkAppIcon(painter = null, size = size)
            if (icon.count > 1) {
                Badge(Modifier.align(Alignment.TopEnd)) { Text(icon.count.toString()) }
            }
        }
    }
}

@Composable
private fun diagnosticDuration(millis: Long): String {
    val seconds = millis / 1000
    val numbers =
        NumberFormat.getIntegerInstance(Locale.getDefault()).apply { isGroupingUsed = false }
    return when {
        seconds < 60 -> stringResource(Res.string.match_debug_seconds, numbers.format(seconds))
        seconds < 3600 ->
            stringResource(
                Res.string.match_debug_minutes,
                numbers.format(seconds / 60),
                numbers.format(seconds % 60),
            )
        else ->
            stringResource(
                Res.string.match_debug_hours,
                numbers.format(seconds / 3600),
                numbers.format(seconds / 60 % 60),
                numbers.format(seconds % 60),
            )
    }
}

@Composable
private fun DiagnosticCaptureCard(
    active: Boolean,
    startedAt: Long?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val action =
        stringResource(if (active) Res.string.match_debug_stop else Res.string.match_debug_start)
    Card(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(Res.string.match_debug_collect),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (active && startedAt != null) {
                    DiagnosticCaptureDuration(startedAt)
                } else {
                    Text(
                        stringResource(Res.string.match_debug_idle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            GkTooltipIconButtonBox(action) {
                FilledTonalIconButton(onClick = onClick, enabled = enabled) {
                    GkIcon(
                        imageVector = if (active) GkIcons.Stop else GkIcons.PlayArrow,
                        contentDescription = action,
                        animateMorph = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticCaptureDuration(startedAt: Long) {
    val elapsedFlow =
        remember(startedAt) {
            flow {
                while (true) {
                    val elapsed = System.currentTimeMillis() - startedAt
                    emit(elapsed)
                    delay(1000 - elapsed % 1000)
                }
            }
        }
    val elapsed by
        elapsedFlow.collectAsStateWithLifecycle(
            initialValue = System.currentTimeMillis() - startedAt,
        )
    val seconds = elapsed / 1000
    val text =
        String.format(
            Locale.ROOT,
            "%02d:%02d:%02d",
            seconds / 3600,
            seconds / 60 % 60,
            seconds % 60,
        )
    val description =
        stringResource(Res.string.match_debug_elapsed_description, diagnosticDuration(elapsed))
    GkFixedTimeText(
        text = text,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
