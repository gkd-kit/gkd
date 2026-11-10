package li.gkd.app.ui.snapshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.app.ActivityNames.getShowActivityId
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.resources.*
import li.gkd.app.state.Loadable
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkRetainedSheet
import li.gkd.app.ui.component.LocalEditorWindowInsets
import li.gkd.app.ui.image.appImageLoader
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.ImagePreviewItem
import li.gkd.app.ui.navigation.ImagePreviewRoute
import li.gkd.app.ui.navigation.SnapshotPageRoute
import li.gkd.app.ui.navigation.SnapshotPreviewRoute
import li.gkd.app.ui.page.GkImagePreviewContent
import li.gkd.app.ui.platform.GkBackHandler
import li.gkd.app.ui.platform.GkSystemBars
import li.gkd.app.ui.platform.LocalUiHost
import org.jetbrains.compose.resources.stringResource

@Composable
fun SnapshotPreviewPage(
    route: SnapshotPreviewRoute,
) {
    val host = LocalUiHost.current
    val mainVm = MainViewModel.requireCurrent()
    val imageLoader = appImageLoader()
    val replaceRoute: (AppRoute) -> Unit = { mainVm.navigator.navigate(it, true) }
    val vm = viewModel { SnapshotViewModel() }
    val inspection = viewModel { SnapshotPreviewViewModel() }
    val nodeState by inspection.tree.collectAsStateWithLifecycle()
    val selection by inspection.selection.collectAsStateWithLifecycle()
    val (selectedNode, candidates) = selection
    val shareProgress by vm.shareProgress.collectAsStateWithLifecycle()

    val loadableState by vm.uiState.collectAsStateWithLifecycle()
    val state = loadableState.value
    val catalog by AppInfoRepository.state.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize()) {
        key(route) {
            val actionRequest by vm.actionRequest.collectAsStateWithLifecycle()
            var imageVersion by remember { mutableIntStateOf(0) }
            val snapshotsById = state?.associateBy { it.id }.orEmpty()
            val previewSnapshots = route.snapshotIds.mapNotNull(snapshotsById::get)

            if (previewSnapshots.isNotEmpty()) {
                val initialPage =
                    previewSnapshots.indexOfFirst { it.id == route.snapshotId }.coerceAtLeast(0)
                val pagerState =
                    rememberPagerState(initialPage = initialPage) {
                        previewSnapshots.size
                    }
                val currentSnapshot = previewSnapshots[pagerState.currentPage]
                val currentImageUri = currentSnapshot.image().path
                var inspecting by remember { mutableStateOf(false) }
                var inspectionSession by remember { mutableIntStateOf(0) }
                var treeVisible by remember { mutableStateOf(true) }
                var locateRequest by remember {
                    mutableStateOf<SnapshotNodeLocateRequest?>(null)
                }
                var propertiesVisible by
                    remember(currentSnapshot.id, imageVersion) {
                        mutableStateOf(false)
                    }
                val currentTree = nodeState.value?.takeIf { it.snapshot.id == currentSnapshot.id }
                val selectedForSnapshot = selectedNode?.let { currentTree?.nodes?.get(it) }
                LaunchedEffect(currentSnapshot.id, imageVersion) {
                    locateRequest = null
                    inspection.load(currentSnapshot.id)
                }
                LaunchedEffect(inspecting, currentSnapshot.id, currentTree) {
                    if (inspecting) inspection.selectFirstNodeIfNone(currentSnapshot.id)
                }
                fun exitInspection() {
                    inspecting = false
                    propertiesVisible = false
                    locateRequest = null
                    inspection.clearSelection()
                }

                fun showNodeProperties(id: Int) {
                    inspection.select(id)
                    propertiesVisible = true
                }

                fun revealSelectedNode(id: Int) {
                    treeVisible = true
                    locateRequest = SnapshotNodeLocateRequest(id)
                }

                val onBack: () -> Unit = {
                    if (inspecting) exitInspection() else mainVm.navigator.pop()
                }
                GkBackHandler(enabled = inspecting, onBack = onBack)
                GkImagePreviewContent(
                    imageLoader = imageLoader,
                    systemBars = { host.GkSystemBars(it, fullscreen = inspecting) },
                    route =
                        ImagePreviewRoute(
                            items =
                                previewSnapshots.map {
                                    ImagePreviewItem(uri = it.image().path)
                                },
                        ),
                    pagerState = pagerState,
                    imageVersion = imageVersion,
                    fullscreen = inspecting,
                    pagingEnabled = !inspecting,
                    onBack = onBack,
                    onImageTap =
                        if (inspecting) {
                            { uri, point ->
                                if (uri == currentImageUri) {
                                    inspection.hit(
                                        point.x,
                                        point.y,
                                        onSelected = ::revealSelectedNode,
                                    )
                                }
                            }
                        } else null,
                    imageOverlay = { uri, bounds ->
                        if (inspecting && currentTree != null && uri == currentImageUri) {
                            GkSnapshotNodeOutline(currentTree, selectedForSnapshot?.id, bounds)
                        }
                    },
                    titleContent = { index ->
                        previewSnapshots.getOrNull(index)?.let { item ->
                            val appName =
                                catalog.snapshot?.apps?.get(item.appId)?.name ?: item.appId
                            val activityId =
                                getShowActivityId(item.appId, item.activityId)
                                    ?.takeIf(String::isNotBlank)
                            Column {
                                GkAppNameText(
                                    appId = item.appId,
                                    fallbackName = appName,
                                    style =
                                        MaterialTheme.typography.titleLarge.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                        ),
                                )
                                if (activityId != null) {
                                    Text(
                                        text = activityId,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.MiddleEllipsis,
                                        style =
                                            MaterialTheme.typography.titleSmall.copy(
                                                color = Color.White.copy(alpha = 0.8f),
                                            ),
                                    )
                                }
                            }
                        }
                    },
                    actionContent = { _, index ->
                        previewSnapshots.getOrNull(index)?.let { item ->
                            val colors =
                                IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                            GkIconButton(
                                imageVector = GkIcons.FrameInspect,
                                contentDescription =
                                    stringResource(Res.string.snapshot_node_inspect),
                                colors = colors,
                                onClick = {
                                    vm.actionRequest.value?.let(vm::dismissActions)
                                    inspectionSession++
                                    inspecting = true
                                    treeVisible = true
                                },
                            )
                            GkIconButton(
                                imageVector = GkIcons.MoreVert,
                                contentDescription = stringResource(Res.string.more_label),
                                colors = colors,
                                onClick = { vm.showActions(item.id) },
                            )
                        }
                    },
                )

                if (inspectionSession > 0)
                    key(inspectionSession) {
                        GkSnapshotNodeTreeSheet(
                            visible = inspecting && treeVisible,
                            state = nodeState,
                            selectedId = selectedForSnapshot?.id,
                            onSelect = inspection::select,
                            onShowProperties = ::showNodeProperties,
                            locateRequest = locateRequest,
                            onHide = { treeVisible = false },
                            onRetry = { inspection.load(currentSnapshot.id) },
                        )
                    }
                if (inspecting) {
                    SnapshotInspectionTools(
                        treeVisible = treeVisible,
                        onTreeVisibilityChange = { treeVisible = it },
                        onExit = ::exitInspection,
                    )
                }
                if (inspecting && currentTree != null) {
                    if (propertiesVisible && selectedForSnapshot != null) {
                        GkSnapshotNodePropertiesDialog(
                            currentTree,
                            selectedForSnapshot,
                            onSelect = {
                                inspection.select(it)
                                if (treeVisible) locateRequest = SnapshotNodeLocateRequest(it)
                            },
                            onShowValue = mainVm.textDialog::showText,
                            onDismiss = { propertiesVisible = false },
                        )
                    }
                    if (candidates.isNotEmpty()) {
                        GkSnapshotOverlapDialog(
                            currentTree,
                            candidates,
                            selectedId = selectedForSnapshot?.id,
                            onSelect = {
                                inspection.select(it)
                                revealSelectedNode(it)
                            },
                            onDismiss = inspection::dismissCandidates,
                        )
                    }
                }
                if (!inspecting) {
                    val actionSnapshot = actionRequest?.let { request ->
                        previewSnapshots.find { it.id == request.key }
                    }
                    GkRetainedSheet(
                        request = actionRequest,
                        snapshot = actionSnapshot,
                        missing = actionRequest != null && actionSnapshot == null,
                        onDismissRequest = { request ->
                            vm.dismissActions(request)
                        },
                    ) { _, snapshot, sheetState, dismiss ->
                        val routeAfterDelete =
                            if (previewSnapshots.size == 1) {
                                null
                            } else {
                                val currentIndex = previewSnapshots.indexOfFirst {
                                    it.id == snapshot.id
                                }
                                val targetIndex =
                                    if (currentIndex < previewSnapshots.lastIndex) {
                                        currentIndex + 1
                                    } else {
                                        currentIndex - 1
                                    }
                                route.copy(
                                    snapshotId = previewSnapshots[targetIndex].id,
                                    snapshotIds = route.snapshotIds.filterNot { it == snapshot.id },
                                )
                            }
                        val actions =
                            host.snapshotActions(
                                mainVm,
                                vm,
                                { imageVersion++ },
                                {
                                    if (routeAfterDelete == null) {
                                        mainVm.navigator.pop()
                                    } else {
                                        replaceRoute(routeAfterDelete)
                                    }
                                },
                                {
                                    if (routeAfterDelete == null) {
                                        if (mainVm.navigator.topRoute == SnapshotPageRoute)
                                            mainVm.navigator.navigate(route)
                                    } else if (mainVm.navigator.topRoute == routeAfterDelete) {
                                        replaceRoute(route)
                                    }
                                },
                            )
                        GkSnapshotActionsSheet(
                            imageLoader,
                            snapshot = snapshot,
                            appName =
                                catalog.snapshot?.apps?.get(snapshot.appId)?.name ?: snapshot.appId,
                            sheetState = sheetState,
                            onDismissRequest = dismiss,
                            onShare = {
                                vm.shareSnapshots(listOf(snapshot), host.snapshotPlatformActions())
                            },
                            shareEnabled = shareProgress == null,
                            onSaveToDownloads = { actions.saveToDownloads(snapshot) },
                            onUpload = { actions.upload(snapshot) },
                            onSaveToAlbum = { actions.saveToAlbum(snapshot) },
                            onReplace = { actions.replace(snapshot) },
                            onDelete = { actions.delete(snapshot) },
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    GkImagePreviewContent(
                        imageLoader = imageLoader,
                        systemBars = { host.GkSystemBars(it) },
                        route =
                            ImagePreviewRoute(title = stringResource(Res.string.snapshot_records)),
                        onBack = mainVm.navigator::pop,
                    )
                    if (loadableState is Loadable.Loading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        Text(
                            text =
                                (loadableState as? Loadable.Failure)?.cause?.message
                                    ?: stringResource(Res.string.snapshot_missing_or_deleted),
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.White,
                        )
                    }
                }
            }
        }
        shareProgress?.let {
            GkSnapshotShareProgress(
                progress = it,
                onCancel = vm::cancelShare,
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .windowInsetsPadding(
                            LocalEditorWindowInsets.current ?: WindowInsets.safeDrawing,
                        )
                        .then(host.snapshotShareCornerPadding()),
            )
        }
    }
}
