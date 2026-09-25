package li.gkd.app.feature.snapshot

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import li.gkd.app.MainActivity
import li.gkd.app.MainViewModel
import li.gkd.app.core.state.Loadable
import li.gkd.app.data.date
import li.gkd.app.data.screenshotFile
import li.gkd.app.data.snapshot.SnapshotRepository
import li.gkd.app.store.AppStore
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.component.GkBatchActionMenuItem
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkFixedTimeText
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMultiSelectionActions
import li.gkd.app.ui.component.GkMultiSelectionTopAppBar
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkRetainedSheet
import li.gkd.app.ui.component.SheetRequest
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.SnapshotDisplayModeOption
import li.gkd.app.util.IMPORT_SHORT_URL
import li.gkd.app.util.findOption
import li.gkd.app.util.format
import li.gkd.app.util.getShowActivityId
import li.gkd.app.util.ToastUtils.toast
import li.gkd.db.Snapshot

@Serializable
data object SnapshotPageRoute : NavKey

@Composable
fun SnapshotPage() {
    val context = LocalActivity.current as MainActivity
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel<SnapshotVm>()
    val loadableState by vm.uiState.collectAsStateWithLifecycle()
    val settings by AppStore.storeFlow.collectAsStateWithLifecycle()
    val displayMode = SnapshotDisplayModeOption.objects.findOption(settings.snapshotDisplayMode)
    val actionScope = vm.scope
    val state = loadableState.value
    val firstLoading = loadableState is Loadable.Loading
    val loadError = (loadableState as? Loadable.Failure)?.cause
    val snapshots = state?.snapshots.orEmpty()
    val appNames = state?.appNames.orEmpty()
    val groups = remember(snapshots, appNames, displayMode) {
        buildSnapshotGroups(snapshots, appNames, displayMode)
    }
    val displayedSnapshots = remember(groups) { groups.flatMap { it.snapshots } }
    val previewSnapshotIds = remember(displayedSnapshots) { displayedSnapshots.map { it.id } }
    fun openPreview(snapshotId: Long) {
        mainVm.navigatePage(SnapshotPreviewRoute(snapshotId, previewSnapshotIds))
    }
    var actionRequest by remember { mutableStateOf<SheetRequest<Long>?>(null) }
    val actionSnapshot = actionRequest?.let { request -> snapshots.find { it.id == request.key } }
    val thumbnailVersions = remember { mutableStateMapOf<Long, Int>() }
    val selectionState = rememberMultiSelectionState<Long>()
    val allIds = remember(snapshots) { snapshots.mapTo(mutableSetOf()) { it.id } }
    val selectedIds = selectionState.selectedKeys intersect allIds
    val selectedSnapshots = displayedSnapshots.filter { it.id in selectedIds }
    val hasUnlinkedSnapshots = remember(snapshots) { snapshots.any { it.githubAssetId == null } }
    val pendingUploads = selectedSnapshots.filter { it.githubAssetId == null }
    fun createUploadItems(targets: List<Snapshot>) = targets.map { snapshot ->
        createSnapshotUploadItem(
            snapshot = snapshot,
            label = (appNames[snapshot.appId] ?: snapshot.appId) + " · " + snapshot.date,
            vm = vm,
        )
    }
    fun showLinks(targets: List<Snapshot>) {
        val links = targets.mapNotNull { snapshot ->
            snapshot.githubAssetId?.let { IMPORT_SHORT_URL + it }
        }
        if (links.isNotEmpty()) mainVm.textDialog.showText(links.joinToString("\n"))
    }
    fun showCurrentLinks(targetIds: List<Long>) {
        mainVm.scope.launchUi {
            val byId = SnapshotRepository.snapshots().first().associateBy { it.id }
            showLinks(targetIds.mapNotNull(byId::get))
        }
    }
    fun saveSelected(targets: List<Snapshot>, toAlbum: Boolean) {
        actionScope.launchUi {
            val title = if (toAlbum) UiStrings.action_save_to_album else UiStrings.action_save_to_downloads
            val confirmation = if (toAlbum) {
                UiStrings.snapshot_save_selected_to_album_confirmation(targets.size)
            } else {
                UiStrings.snapshot_save_selected_to_downloads_confirmation(targets.size)
            }
            if (!mainVm.dialogRequests.confirm(
                    title = title,
                    text = confirmation,
                    confirmText = title,
                )) return@launchUi
            val actions = SnapshotActionHandler(mainVm = mainVm, vm = vm, activity = context)
            val savedCount = if (toAlbum) {
                actions.saveSelectedToAlbum(targets)
            } else {
                actions.saveSelectedToDownloads(targets)
            } ?: return@launchUi
            toast(if (savedCount == targets.size) {
                UiStrings.snapshot_save_selected_success(savedCount)
            } else {
                UiStrings.snapshot_save_selected_partial(savedCount, targets.size - savedCount)
            })
        }
    }
    val gridState = rememberLazyGridState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val uiScope = rememberCoroutineScope()
    val resetScroll = {
        uiScope.launch {
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
            gridState.scrollToItem(0)
        }
        Unit
    }
    var wasEmpty by remember { mutableStateOf(groups.isEmpty()) }
    SideEffect {
        if (wasEmpty && groups.isNotEmpty()) {
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
            gridState.requestScrollToItem(0)
        }
        wasEmpty = groups.isEmpty()
    }

    LaunchedEffect(allIds, loadableState) {
        if (loadableState is Loadable.Ready) selectionState.retain(allIds)
    }
    BackHandler(selectionState.active) { selectionState.clear() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkMultiSelectionTopAppBar(
                selectedMode = selectionState.active,
                selectedCount = selectedIds.size,
                onExitSelection = selectionState::clear,
                onNavigateBack = mainVm::popPage,
                onTitleClick = resetScroll,
                scrollBehavior = scrollBehavior,
                title = { Text(UiStrings.snapshot_records) },
                actions = { selectedMode ->
                    if (selectedMode) {
                        GkMultiSelectionActions(
                            selectionState = selectionState,
                            keys = allIds,
                            enabled = loadableState is Loadable.Ready,
                        ) { dismiss ->
                            GkBatchActionMenuItem(
                                text = UiStrings.action_save_to_downloads,
                                enabled = selectedSnapshots.isNotEmpty(),
                                onDismiss = dismiss,
                                onClick = { saveSelected(selectedSnapshots, toAlbum = false) },
                            )
                            GkBatchActionMenuItem(
                                text = UiStrings.action_save_to_album,
                                enabled = selectedSnapshots.isNotEmpty(),
                                onDismiss = dismiss,
                                onClick = { saveSelected(selectedSnapshots, toAlbum = true) },
                            )
                            if (hasUnlinkedSnapshots) {
                                GkBatchActionMenuItem(
                                    text = UiStrings.snapshot_generate_link,
                                    enabled = pendingUploads.isNotEmpty(),
                                    onDismiss = dismiss,
                                    onClick = {
                                        val targets = pendingUploads
                                        actionScope.launchUi {
                                            if (!mainVm.dialogRequests.confirm(
                                                    title = UiStrings.snapshot_generate_link,
                                                    text = UiStrings.snapshot_generate_links_confirmation(targets.size) +
                                                        "\n\n" + UiStrings.snapshot_upload_privacy_warning,
                                                    confirmText = UiStrings.snapshot_generate_link,
                                                )) return@launchUi
                                            if (mainVm.githubUpload.startBatchTask(createUploadItems(targets))) {
                                                selectionState.clear()
                                            } else {
                                                toast(UiStrings.upload_busy)
                                            }
                                        }
                                    },
                                )
                            }
                            GkBatchActionMenuItem(
                                text = UiStrings.link_copy,
                                enabled = selectedSnapshots.isNotEmpty(),
                                onDismiss = dismiss,
                                onClick = {
                                    val targets = selectedSnapshots
                                    val targetIds = targets.map { it.id }
                                    val pending = targets.filter { it.githubAssetId == null }
                                    if (pending.isEmpty()) {
                                        showLinks(targets)
                                    } else {
                                        actionScope.launchUi {
                                            val upload = mainVm.dialogRequests.confirm(
                                                title = UiStrings.link_copy,
                                                text = (if (pending.size == targets.size) {
                                                    UiStrings.snapshot_copy_links_upload_all_confirmation(targets.size)
                                                } else {
                                                    UiStrings.snapshot_copy_links_upload_some_confirmation(pending.size)
                                                }) + "\n\n" + UiStrings.snapshot_upload_privacy_warning,
                                                confirmText = UiStrings.snapshot_generate_link,
                                            )
                                            if (upload) {
                                                if (mainVm.githubUpload.startBatchTask(
                                                        createUploadItems(pending),
                                                        onFinished = { showCurrentLinks(targetIds) },
                                                    )) {
                                                    selectionState.clear()
                                                } else {
                                                    toast(UiStrings.upload_busy)
                                                }
                                            } else if (pending.size < targets.size) {
                                                showLinks(targets)
                                            }
                                        }
                                    }
                                },
                            )
                            GkBatchActionMenuItem(
                                text = UiStrings.action_delete,
                                enabled = selectedSnapshots.isNotEmpty(),
                                onDismiss = dismiss,
                                onClick = {
                                    val targets = selectedSnapshots
                                    actionScope.launchUi {
                                        if (!mainVm.dialogRequests.confirm(
                                            title = UiStrings.snapshot_delete,
                                            text = UiStrings.snapshot_delete_selected_confirmation(targets.size),
                                            error = true,
                                        )) return@launchUi
                                        val result = vm.deleteSnapshots(targets)
                                        selectionState.removeDeleted(result.deletedIds)
                                        toast(UiStrings.snapshot_delete_selected_result(
                                            result.deletedIds.size,
                                            result.failedCount,
                                        ))
                                    }
                                },
                            )
                        }
                    } else {
                        val nextMode = when (displayMode) {
                            SnapshotDisplayModeOption.ByTime -> SnapshotDisplayModeOption.ByApp
                            SnapshotDisplayModeOption.ByApp -> SnapshotDisplayModeOption.ByTime
                        }
                        GkIconButton(
                            imageVector = when (displayMode) {
                                SnapshotDisplayModeOption.ByTime -> GkIcons.CalendarMonth
                                SnapshotDisplayModeOption.ByApp -> GkIcons.Apps
                            },
                            modifier = Modifier.semantics { stateDescription = displayMode.label },
                            contentDescription = when (nextMode) {
                                SnapshotDisplayModeOption.ByTime -> UiStrings.snapshot_view_switch_to_time
                                SnapshotDisplayModeOption.ByApp -> UiStrings.snapshot_view_switch_to_app
                            },
                            onClick = {
                                AppStore.updateSettings {
                                    it.copy(snapshotDisplayMode = nextMode.value)
                                }
                                resetScroll()
                            },
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize().scaffoldPadding(contentPadding),
            state = gridState,
            contentPadding = PaddingValues(start = 12.dp, top = 0.dp, end = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEach { group ->
                stickyHeader(key = group.key) {
                    val groupIds = remember(group) {
                        group.snapshots.mapTo(mutableSetOf()) { it.id }
                    }
                    val selectedCount = groupIds.count { it in selectedIds }
                    val groupSelectionState = when (selectedCount) {
                        0 -> ToggleableState.Off
                        groupIds.size -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    }
                    SnapshotGroupHeader(
                        title = group.title,
                        appId = group.appId,
                        selectedMode = selectionState.active,
                        groupSelectionState = groupSelectionState,
                        onToggleSelection = {
                            if (groupSelectionState == ToggleableState.On) {
                                selectionState.deselectMany(groupIds)
                            } else {
                                selectionState.selectMany(groupIds)
                            }
                        },
                    )
                }
                items(group.snapshots, key = { it.id }) { snapshot ->
                    SnapshotCard(
                        snapshot = snapshot,
                        appName = appNames[snapshot.appId] ?: snapshot.appId,
                        showAppName = displayMode != SnapshotDisplayModeOption.ByApp,
                        thumbnailVersion = thumbnailVersions[snapshot.id] ?: 0,
                        selectedMode = selectionState.active,
                        selected = snapshot.id in selectedIds,
                        onClick = {
                            if (selectionState.active) {
                                selectionState.toggle(snapshot.id)
                            } else {
                                openPreview(snapshot.id)
                            }
                        },
                        onLongClick = { selectionState.select(snapshot.id) },
                        onMore = { actionRequest = SheetRequest(snapshot.id) },
                    )
                }
            }
            if (!firstLoading) {
                item(key = ListPlaceholder.KEY, span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        if (groups.isEmpty()) {
                            GkEmptyState(
                                text = when {
                                    loadError != null -> loadError.message ?: UiStrings.data_load_failed
                                    else -> UiStrings.data_empty
                                },
                            )
                        }
                        GkPageBottomSpace()
                    }
                }
            }
        }
    }

    GkRetainedSheet(
        request = actionRequest,
        snapshot = actionSnapshot,
        missing = actionRequest != null && actionSnapshot == null && loadableState is Loadable.Ready,
        onDismissRequest = { request ->
            if (actionRequest === request) actionRequest = null
        },
    ) { _, snapshot, sheetState, dismiss ->
        val actions = SnapshotActionHandler(
            mainVm = mainVm,
            vm = vm,
            activity = context,
            onReplaced = { id ->
                thumbnailVersions[id] = (thumbnailVersions[id] ?: 0) + 1
            },
        )
        GkSnapshotActionsSheet(
            snapshot = snapshot,
            appName = appNames[snapshot.appId] ?: snapshot.appId,
            sheetState = sheetState,
            onDismissRequest = dismiss,
            onShare = { actions.share(snapshot) },
            onSaveToDownloads = { actions.saveToDownloads(snapshot) },
            onUpload = { actions.upload(snapshot) },
            onSaveToAlbum = { actions.saveToAlbum(snapshot) },
            onReplace = { actions.replace(snapshot) },
            onDelete = { actions.delete(snapshot) },
        )
    }
}

@Composable
private fun SnapshotGroupHeader(
    title: String,
    appId: String?,
    selectedMode: Boolean,
    groupSelectionState: ToggleableState,
    onToggleSelection: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (appId != null) {
            GkAppNameText(
                appId = appId,
                fallbackName = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
        } else {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(modifier = Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            if (selectedMode) {
                TriStateCheckbox(
                    state = groupSelectionState,
                    onClick = onToggleSelection,
                    modifier = Modifier.semantics {
                        contentDescription = title
                    },
                )
            }
        }
    }
}

@Composable
private fun SnapshotCard(
    snapshot: Snapshot,
    appName: String,
    showAppName: Boolean,
    thumbnailVersion: Int,
    selectedMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(12.dp)
    val screenshotFile = snapshot.screenshotFile
    val modifiedAt = screenshotFile.lastModified()
    val cacheKey = remember(snapshot.id, modifiedAt, thumbnailVersion) {
        "snapshot-" + snapshot.id + "-" + modifiedAt + "-" + thumbnailVersion
    }
    val request = remember(context, screenshotFile, cacheKey) {
        ImageRequest.Builder(context)
            .data(screenshotFile)
            .memoryCacheKey(cacheKey)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    var imageFailed by remember(cacheKey) { mutableStateOf(!screenshotFile.isFile) }
    Card(
        modifier = Modifier.fillMaxWidth().clip(cardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = if (selectedMode) UiStrings.selection_toggle else UiStrings.action_view,
                onLongClickLabel = UiStrings.selection_mode_enter,
            )
            .semantics {
                if (selectedMode) {
                    role = Role.Checkbox
                    this.selected = selected
                }
            },
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) colorScheme.primaryContainer
                else colorScheme.surfaceContainer,
        ),
        border = if (selected) BorderStroke(2.dp, colorScheme.primary) else null,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { imageFailed = false },
                onError = { imageFailed = true },
            )
            Box(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(56.dp)
                    .background(Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent),
                    )),
            )
            if (imageFailed) {
                Text(
                    text = UiStrings.snapshot_screenshot_unavailable,
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(56.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.width(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selectedMode) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = null,
                            modifier = Modifier.clearAndSetSemantics {},
                            colors = CheckboxDefaults.colors(uncheckedColor = Color.White),
                        )
                    } else {
                        GkIconButton(
                            imageVector = GkIcons.MoreVert,
                            contentDescription = UiStrings.more_label,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                            onClick = onMore,
                        )
                    }
                }
            }
        }
        val fullTime = remember(snapshot.id) { snapshot.id.format("yyyy-MM-dd HH:mm:ss") }
        val firstLineHeight = with(LocalDensity.current) {
            MaterialTheme.typography.bodyMedium.lineHeight.toDp()
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp)
                .heightIn(min = firstLineHeight),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showAppName) {
                GkAppNameText(
                    appId = snapshot.appId,
                    fallbackName = appName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val timeTooltipState = rememberTooltipState()
                val timeTooltipScope = rememberCoroutineScope()
                TooltipBox(
                    tooltip = { PlainTooltip { Text(fullTime) } },
                    state = timeTooltipState,
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        TooltipAnchorPosition.Above,
                    ),
                    enableUserInput = false,
                ) {
                    GkFixedTimeText(
                        text = snapshot.id.format("HH:mm"),
                        modifier = (if (selectedMode) Modifier else Modifier.clickable {
                            timeTooltipScope.launch { timeTooltipState.show() }
                        }).semantics { contentDescription = fullTime },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            } else {
                GkFixedTimeText(
                    text = snapshot.date,
                    modifier = Modifier.semantics { contentDescription = fullTime },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        val activityLabel = getShowActivityId(snapshot.appId, snapshot.activityId)
            ?.takeIf(String::isNotBlank)
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp)
                .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = activityLabel ?: UiStrings.value_null,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                style = MaterialTheme.typography.labelSmall,
                color = if (activityLabel == null) colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else colorScheme.onSurface,
            )
        }
    }
}
