package li.gkd.app.feature.snapshot

import androidx.activity.compose.LocalActivity
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
import kotlinx.serialization.Serializable
import li.gkd.app.MainActivity
import li.gkd.app.MainViewModel
import li.gkd.app.core.state.Loadable
import li.gkd.app.data.screenshotFile
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.GkImagePreviewContent
import li.gkd.app.ui.ImagePreviewItem
import li.gkd.app.ui.ImagePreviewRoute
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkRetainedSheet
import li.gkd.app.ui.component.SheetRequest
import li.gkd.app.util.getShowActivityId

@Serializable
data class SnapshotPreviewRoute(
    val snapshotId: Long,
    val snapshotIds: List<Long>,
) : NavKey

@Composable
fun SnapshotPreviewPage(route: SnapshotPreviewRoute) {
    val mainVm = MainViewModel.requireCurrent()
    val activity = LocalActivity.current as MainActivity
    val vm = viewModel<SnapshotVm>()
    val loadableState by vm.uiState.collectAsStateWithLifecycle()
    val state = loadableState.value
    key(route) {
        var actionRequest by remember { mutableStateOf<SheetRequest<Long>?>(null) }
        var imageVersion by remember { mutableIntStateOf(0) }
        val snapshotsById = state?.snapshots?.associateBy { it.id }.orEmpty()
        val previewSnapshots = route.snapshotIds.mapNotNull(snapshotsById::get)

        if (previewSnapshots.isNotEmpty()) {
            val initialPage = previewSnapshots.indexOfFirst { it.id == route.snapshotId }
                .coerceAtLeast(0)
            val pagerState = rememberPagerState(initialPage = initialPage) {
                previewSnapshots.size
            }
            GkImagePreviewContent(
                route = ImagePreviewRoute(items = previewSnapshots.map {
                    ImagePreviewItem(uri = it.screenshotFile.absolutePath)
                }),
                pagerState = pagerState,
                imageVersion = imageVersion,
                onBack = mainVm::popPage,
                titleContent = { index ->
                    previewSnapshots.getOrNull(index)?.let { item ->
                        val appName = state?.appNames?.get(item.appId) ?: item.appId
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
                            contentDescription = UiStrings.more_label,
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
                val actions = SnapshotActionHandler(
                    mainVm = mainVm,
                    vm = vm,
                    activity = activity,
                    onReplaced = { imageVersion++ },
                    onBeforeDelete = {
                        if (routeAfterDelete == null) {
                            mainVm.popPage()
                        } else {
                            mainVm.navigatePage(routeAfterDelete, replaced = true)
                        }
                    },
                    onDeleteFailed = {
                        if (routeAfterDelete == null) {
                            if (mainVm.topRoute == SnapshotPageRoute) mainVm.navigatePage(route)
                        } else if (mainVm.topRoute == routeAfterDelete) {
                            mainVm.navigatePage(route, replaced = true)
                        }
                    },
                )
                GkSnapshotActionsSheet(
                    snapshot = snapshot,
                    appName = state?.appNames?.get(snapshot.appId) ?: snapshot.appId,
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
                    route = ImagePreviewRoute(title = UiStrings.snapshot_records),
                    onBack = mainVm::popPage,
                )
                if (loadableState is Loadable.Loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    Text(
                        text = (loadableState as? Loadable.Failure)?.cause?.message
                            ?: UiStrings.snapshot_missing_or_deleted,
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                    )
                }
            }
        }
    }
}
