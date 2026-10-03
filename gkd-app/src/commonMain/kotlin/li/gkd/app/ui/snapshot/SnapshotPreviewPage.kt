package li.gkd.app.ui.snapshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.navigation3.runtime.NavKey
import coil3.ImageLoader
import li.gkd.app.app.ActivityNames.getShowActivityId
import li.gkd.app.resources.Res
import li.gkd.app.resources.more_label
import li.gkd.app.resources.snapshot_missing_or_deleted
import li.gkd.app.resources.snapshot_records
import li.gkd.app.state.Loadable
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkRetainedSheet
import li.gkd.app.ui.component.SheetRequest
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.ImagePreviewItem
import li.gkd.app.ui.navigation.ImagePreviewRoute
import li.gkd.app.ui.navigation.SnapshotActionFactory
import li.gkd.app.ui.navigation.SnapshotPageRoute
import li.gkd.app.ui.navigation.SnapshotPreviewRoute
import li.gkd.app.ui.page.GkImagePreviewContent
import org.jetbrains.compose.resources.stringResource

@Composable
fun SnapshotPreviewPage(
    route: SnapshotPreviewRoute,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    replaceRoute: (AppRoute) -> Unit,
    topRoute: () -> NavKey,
    copyText: (String) -> Unit,
    imageLoader: ImageLoader,
    systemBars: @Composable (Boolean) -> Unit,
    snapshotActions: SnapshotActionFactory,
) {
    val vm = viewModel { SnapshotViewModel() }

    val loadableState by vm.uiState.collectAsStateWithLifecycle()
    val state = loadableState.value
    val catalog by li.gkd.app.app.AppInfoRepository.state.collectAsStateWithLifecycle()
    key(route) {
        var actionRequest by remember { mutableStateOf<SheetRequest<Long>?>(null) }
        var imageVersion by remember { mutableIntStateOf(0) }
        val snapshotsById = state?.associateBy { it.id }.orEmpty()
        val previewSnapshots = route.snapshotIds.mapNotNull(snapshotsById::get)

        if (previewSnapshots.isNotEmpty()) {
            val initialPage = previewSnapshots.indexOfFirst { it.id == route.snapshotId }
                .coerceAtLeast(0)
            val pagerState = rememberPagerState(initialPage = initialPage) {
                previewSnapshots.size
            }
            GkImagePreviewContent(
                imageLoader = imageLoader, systemBars = { systemBars(it) },
                route = ImagePreviewRoute(items = previewSnapshots.map {
                    ImagePreviewItem(uri = it.image().path)
                }),
                pagerState = pagerState,
                imageVersion = imageVersion,
                onBack = onBack,
                titleContent = { index ->
                    previewSnapshots.getOrNull(index)?.let { item ->
                        val appName = catalog.snapshot?.apps?.get(item.appId)?.name ?: item.appId
                        val activityId = getShowActivityId(item.appId, item.activityId)
                            ?.takeIf(String::isNotBlank)
                        Column {
                            GkAppNameText(
                                appId = item.appId,
                                fallbackName = appName,
                                style = MaterialTheme.typography.titleLarge.copy(
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
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = Color.White.copy(alpha = 0.8f),
                                    ),
                                )
                            }
                        }
                    }
                },
                actionContent = { _, index ->
                    val item = previewSnapshots.getOrNull(index)
                    if (item != null) {
                        GkIconButton(
                            imageVector = GkIcons.MoreVert,
                            contentDescription = stringResource(Res.string.more_label),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                            onClick = { actionRequest = SheetRequest(item.id) },
                        )
                    }
                },
            )

            val actionSnapshot = actionRequest?.let { request ->
                previewSnapshots.find { it.id == request.key }
            }
            GkRetainedSheet(
                request = actionRequest,
                snapshot = actionSnapshot,
                missing = actionRequest != null && actionSnapshot == null,
                onDismissRequest = { request ->
                    if (actionRequest === request) actionRequest = null
                },
            ) { _, snapshot, sheetState, dismiss ->
                val routeAfterDelete = if (previewSnapshots.size == 1) {
                    null
                } else {
                    val currentIndex = previewSnapshots.indexOfFirst { it.id == snapshot.id }
                    val targetIndex = if (currentIndex < previewSnapshots.lastIndex) {
                        currentIndex + 1
                    } else {
                        currentIndex - 1
                    }
                    route.copy(
                        snapshotId = previewSnapshots[targetIndex].id,
                        snapshotIds = route.snapshotIds.filterNot { it == snapshot.id },
                    )
                }
                val actions = snapshotActions(
                    vm,
                    { imageVersion++ },
                    {
                        if (routeAfterDelete == null) {
                            onBack()
                        } else {
                            replaceRoute(routeAfterDelete)
                        }
                    },
                    {
                        if (routeAfterDelete == null) {
                            if (topRoute() == SnapshotPageRoute) onNavigate(route)
                        } else if (topRoute() == routeAfterDelete) {
                            replaceRoute(route)
                        }
                    },
                )
                GkSnapshotActionsSheet(
                    copyText, imageLoader, snapshot = snapshot,
                    appName = catalog.snapshot?.apps?.get(snapshot.appId)?.name ?: snapshot.appId,
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
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                GkImagePreviewContent(
                    imageLoader = imageLoader,
                    systemBars = { systemBars(it) },
                    route = ImagePreviewRoute(title = stringResource(Res.string.snapshot_records)),
                    onBack = onBack,
                )
                if (loadableState is Loadable.Loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    Text(
                        text = (loadableState as? Loadable.Failure)?.cause?.message
                            ?: stringResource(Res.string.snapshot_missing_or_deleted),
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                    )
                }
            }
        }
    }
}
