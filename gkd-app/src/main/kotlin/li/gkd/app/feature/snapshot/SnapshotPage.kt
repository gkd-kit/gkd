package li.gkd.app.feature.snapshot

import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.MainActivity
import li.gkd.app.data.date
import li.gkd.app.data.screenshotFile
import li.gkd.app.permission.PermissionStates
import li.gkd.app.data.snapshot.SnapshotRepository
import li.gkd.app.ui.ImagePreviewRoute
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.core.state.Loadable
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.itemVerticalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.IMPORT_SHORT_URL
import li.gkd.app.util.UriUtils
import li.gkd.app.util.ToastUtils.copyText
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.util.ToastUtils.toast
import li.gkd.db.Snapshot
import li.gkd.app.ui.component.GkDialog
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkFixedTimeText
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.component.rememberListScrollState

@Serializable
data object SnapshotPageRoute : NavKey

@Composable
fun SnapshotPage() {
    val context = LocalActivity.current as MainActivity
    val mainVm = MainViewModel.requireCurrent()
    val colorScheme = MaterialTheme.colorScheme
    val vm = viewModel<SnapshotVm>()
    val loadableState by vm.uiState.collectAsStateWithLifecycle()
    val actionScope = vm.scope

    val state = loadableState.value
    val firstLoading = loadableState is Loadable.Loading
    val loadError = (loadableState as? Loadable.Failure)?.cause
    val snapshots = state?.snapshots.orEmpty()
    val appNames = state?.appNames.orEmpty()
    var selectedSnapshot by remember { mutableStateOf<Snapshot?>(null) }
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnChange(
        snapshots.isEmpty(),
        firstLoading,
    )
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        GkTopAppBar(
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                GkIconButton(imageVector = GkIcons.ArrowBack, onClick = {
                    mainVm.popPage()
                })
            },
            title = {
                Text(
                    text = UiStrings.snapshot_records,
                    modifier = Modifier.noRippleClickable(onClick = pageScrollState::resetScroll),
                )
            },
            actions = {
                if (snapshots.isNotEmpty()) {
                    GkIconButton(
                        imageVector = GkIcons.Delete,
                        onClick = throttle {
                            actionScope.launchUi {
                                if (!mainVm.dialogRequests.confirm(
                                    title = UiStrings.snapshot_delete,
                                    text = UiStrings.snapshot_delete_all_confirmation,
                                    error = true,
                                )) return@launchUi
                                vm.deleteAllSnapshots()
                            }
                        },
                    )
                }
            })
    }, content = { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState,
        ) {
            items(snapshots, { it.id }) { snapshot ->
                SnapshotCard(
                    modifier = Modifier.animateListItem(),
                    snapshot = snapshot,
                    appName = appNames[snapshot.appId] ?: snapshot.appId,
                    onClick = {
                        selectedSnapshot = snapshot
                    }
                )
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                if (snapshots.isEmpty() && !firstLoading) {
                    GkEmptyState(
                        text = loadError?.let { it.message ?: UiStrings.data_load_failed } ?: UiStrings.data_empty,
                    )
                } else {
                    GkPageBottomSpace()
                }
            }
        }
    })

    selectedSnapshot?.let { snapshotVal ->
        GkDialog(onDismissRequest = { selectedSnapshot = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                val modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                Text(
                    text = UiStrings.action_view, modifier = Modifier
                        .clickable(onClick = throttle {
                            selectedSnapshot = null
                            mainVm.navigatePage(
                                ImagePreviewRoute(
                                    title = appNames[snapshotVal.appId] ?: snapshotVal.appId,
                                    uri = snapshotVal.screenshotFile.absolutePath,
                                )
                            )
                        })
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = UiStrings.action_share_to_apps,
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            actionScope.launchUi {
                                selectedSnapshot = null
                                context.shareFile(
                                    vm.buildShareArchive(snapshotVal),
                                    UiStrings.snapshot_share,
                                )
                            }
                        })
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = UiStrings.action_save_to_downloads,
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            actionScope.launchUi {
                                selectedSnapshot = null
                                toast(UiStrings.saving_progress)
                                val archive = vm.buildShareArchive(snapshotVal)
                                try {
                                    context.saveFileToDownloads(archive)
                                } finally {
                                    SnapshotRepository.deleteArchive(archive)
                                }
                            }
                        })
                        .then(modifier)
                )
                HorizontalDivider()
                if (snapshotVal.githubAssetId != null) {
                    Text(
                        text = UiStrings.link_copy, modifier = Modifier
                            .clickable(onClick = throttle {
                                selectedSnapshot = null
                                copyText(IMPORT_SHORT_URL + snapshotVal.githubAssetId)
                            })
                            .then(modifier)
                    )
                } else {
                    Text(
                        text = UiStrings.upload_generate_link, modifier = Modifier
                            .clickable(onClick = throttle {
                                selectedSnapshot = null
                                mainVm.githubUpload.startTask(
                                    getFile = { vm.buildUploadArchive(snapshotVal) },
                                    showHref = { IMPORT_SHORT_URL + it.id },
                                    onSuccessResult = {
                                        vm.markUploaded(snapshotVal, it.id)
                                    }
                                )
                            })
                            .then(modifier)
                    )
                }
                HorizontalDivider()

                Text(
                    text = UiStrings.screenshot_save_to_album,
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            actionScope.launchUi {
                                toast(UiStrings.saving_progress)
                                selectedSnapshot = null
                                if (!mainVm.permissionRequests.ensurePermissions(
                                        PermissionStates.writeExternalStorage,
                                    )
                                ) {
                                    return@launchUi
                                }
                                vm.saveScreenshotToAlbum(snapshotVal)
                                toast(UiStrings.save_success)
                            }
                        })
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = UiStrings.screenshot_replace,
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            actionScope.launchUi {
                                val uri = mainVm.activityResults.pickImage() ?: return@launchUi
                                selectedSnapshot = null
                                val newBytes = withContext(Dispatchers.IO) {
                                    UriUtils.uri2Bytes(uri)
                                }
                                if (vm.replaceScreenshot(snapshotVal, newBytes)) {
                                    toast(UiStrings.screenshot_replace_success)
                                } else {
                                    toast(UiStrings.screenshot_size_mismatch)
                                }
                            }
                        })
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = UiStrings.action_delete, modifier = Modifier
                        .clickable(onClick = throttle {
                            mainVm.confirmDelete(
                                title = UiStrings.snapshot_delete,
                                text = UiStrings.snapshot_delete_confirmation,
                                dismiss = { selectedSnapshot = null },
                            ) {
                                vm.deleteSnapshot(snapshotVal)
                                toast(UiStrings.delete_success)
                            }
                        })
                        .then(modifier)
                )
            }
        }
    }
}

@Composable
private fun SnapshotCard(
    modifier: Modifier = Modifier,
    snapshot: Snapshot,
    appName: String,
    onClick: () -> Unit,
) {
    val indicatorColor = MaterialTheme.colorScheme.primaryContainer
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = itemHorizontalPadding, vertical = itemVerticalPadding / 2)
            .drawBehind {
                drawRect(
                    color = indicatorColor,
                    size = Size(2.dp.toPx(), size.height),
                )
            }
            .padding(start = 10.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = appName,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    softWrap = false,
                )
                GkFixedTimeText(
                    text = snapshot.date,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val activityId = snapshot.activityId
            val showActivityId = if (activityId != null) {
                if (activityId.startsWith(snapshot.appId)) {
                    activityId.substring(snapshot.appId.length)
                } else {
                    activityId
                }
            } else {
                null
            }
            if (showActivityId != null) {
                Text(
                    modifier = Modifier.height(MaterialTheme.typography.bodyMedium.lineHeight.value.dp),
                    text = showActivityId,
                    style = MaterialTheme.typography.bodyMedium,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            } else {
                Text(
                    text = UiStrings.value_null,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.typography.bodyMedium.color.copy(alpha = 0.5f)
                )
            }
        }
    }
}
