package li.gkd.app.feature.snapshot

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.gkd.app.MainActivity
import li.gkd.app.data.date
import li.gkd.app.data.screenshotFile
import li.gkd.app.permission.PermissionStates
import li.gkd.app.data.snapshot.SnapshotRepository
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.ImagePreviewRoute
import li.gkd.app.ui.component.FixedTimeText
import li.gkd.app.ui.component.AppDialog
import li.gkd.app.ui.component.BatchActionMenuItem
import li.gkd.app.ui.component.MultiSelectionActions
import li.gkd.app.ui.component.MultiSelectionTopAppBar
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.core.state.Loadable
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.itemVerticalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.IMPORT_SHORT_URL
import li.gkd.app.util.UriUtils
import li.gkd.app.util.ToastUtils.copyText
import li.gkd.app.ui.share.launchUi
import li.gkd.app.util.throttle
import li.gkd.app.util.ToastUtils.toast
import li.gkd.db.Snapshot

@Serializable
data object SnapshotPageRoute : NavKey

@Composable
fun SnapshotPage() {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val colorScheme = MaterialTheme.colorScheme
    val vm = viewModel<SnapshotVm>()
    val loadableState by vm.uiState.collectAsStateWithLifecycle()
    val batchBusy by vm.batchBusyFlow.collectAsStateWithLifecycle()
    val actionScope = vm.scope

    val state = loadableState.value
    val firstLoading = loadableState is Loadable.Loading
    val loadError = (loadableState as? Loadable.Failure)?.cause
    val snapshots = state?.snapshots.orEmpty()
    val appNames = state?.appNames.orEmpty()
    var selectedSnapshot by remember { mutableStateOf<Snapshot?>(null) }

    val allSnapshotIds = remember(snapshots) { snapshots.mapTo(mutableSetOf()) { it.id } }
    val selectionState = rememberMultiSelectionState<Long>()
    val selectedSnapshotIds = selectionState.selectedKeys intersect allSnapshotIds
    val isSelectedMode = selectionState.active

    LaunchedEffect(allSnapshotIds, loadableState) {
        if (loadableState is Loadable.Ready) selectionState.retain(allSnapshotIds)
    }
    BackHandler(isSelectedMode) {
        selectionState.clear()
    }

    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnChange(
        snapshots.isEmpty(),
        firstLoading,
    )
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        MultiSelectionTopAppBar(
            selectedMode = isSelectedMode,
            selectedCount = selectedSnapshotIds.size,
            onExitSelection = selectionState::clear,
            onNavigateBack = mainVm::popPage,
            onTitleClick = pageScrollState::resetScroll,
            scrollBehavior = scrollBehavior,
            title = {
                Text(text = "快照记录")
            },
            actions = { selectedMode ->
                if (selectedMode) {
                    MultiSelectionActions(
                        selectionState = selectionState,
                        keys = allSnapshotIds,
                        enabled = isSelectedMode && !batchBusy && loadableState is Loadable.Ready,
                    ) { dismiss ->
                        BatchActionMenuItem(
                            text = "保存到下载",
                            enabled = selectedSnapshotIds.isNotEmpty(),
                            onDismiss = dismiss,
                            onClick = {
                                val targets = snapshots.filter { it.id in selectedSnapshotIds }
                                actionScope.launchUi {
                                    vm.runBatchAction {
                                        toast("正在保存...")
                                        var count = 0
                                        for (target in targets) {
                                            val archive = vm.buildShareArchive(target)
                                            try {
                                                context.saveFileToDownloads(archive)
                                                count++
                                            } finally {
                                                SnapshotRepository.deleteArchive(archive)
                                            }
                                        }
                                        selectionState.clear()
                                        if (count > 0) {
                                            toast("已保存 $count 个快照到下载")
                                        }
                                    }
                                }
                            },
                        )
                        BatchActionMenuItem(
                            text = "删除",
                            enabled = selectedSnapshotIds.isNotEmpty(),
                            destructive = true,
                            onDismiss = dismiss,
                            onClick = {
                                val targets = snapshots.filter { it.id in selectedSnapshotIds }
                                actionScope.launchUi {
                                    vm.runBatchAction {
                                        if (!mainVm.dialogRequests.confirm(
                                                title = "删除快照",
                                                text = "确定删除所选 ${targets.size} 个快照吗?",
                                                error = true,
                                            )
                                        ) return@runBatchAction
                                        vm.deleteSnapshots(targets)
                                        selectionState.removeDeleted(targets.mapTo(mutableSetOf()) { it.id })
                                        toast("已删除 ${targets.size} 个快照")
                                    }
                                }
                            },
                        )
                    }
                } else {
                    if (snapshots.isNotEmpty()) {
                        PerfIconButton(
                            imageVector = PerfIcon.Delete,
                            onClick = throttle {
                                actionScope.launchUi {
                                    if (!mainVm.dialogRequests.confirm(
                                            title = "删除快照",
                                            text = "确定删除所有快照记录?",
                                            error = true,
                                        )
                                    ) return@launchUi
                                    vm.deleteAllSnapshots()
                                }
                            },
                        )
                    }
                }
            },
        )
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
                    isSelectedMode = isSelectedMode,
                    isSelected = snapshot.id in selectedSnapshotIds,
                    onSelect = {
                        selectionState.toggle(snapshot.id)
                    },
                    onClick = {
                        selectedSnapshot = snapshot
                    },
                )
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (snapshots.isEmpty() && !firstLoading) {
                    EmptyText(
                        text = loadError?.let { it.message ?: "数据加载失败" } ?: "暂无数据",
                    )
                }
            }
        }
    })

    selectedSnapshot?.let { snapshotVal ->
        AppDialog(onDismissRequest = { selectedSnapshot = null }) {
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
                    text = "查看", modifier = Modifier
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
                    text = "分享到其他应用",
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            actionScope.launchUi {
                                selectedSnapshot = null
                                context.shareFile(
                                    vm.buildShareArchive(snapshotVal),
                                    "分享快照文件",
                                )
                            }
                        })
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = "保存到下载",
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            actionScope.launchUi {
                                selectedSnapshot = null
                                toast("正在保存...")
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
                        text = "复制链接", modifier = Modifier
                            .clickable(onClick = throttle {
                                selectedSnapshot = null
                                copyText(IMPORT_SHORT_URL + snapshotVal.githubAssetId)
                            })
                            .then(modifier)
                    )
                } else {
                    Text(
                        text = "生成链接(需科学上网)", modifier = Modifier
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
                    text = "保存截图到相册",
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            actionScope.launchUi {
                                toast("正在保存...")
                                selectedSnapshot = null
                                if (!mainVm.permissionRequests.ensurePermissions(
                                        PermissionStates.writeExternalStorage,
                                    )
                                ) {
                                    return@launchUi
                                }
                                vm.saveScreenshotToAlbum(snapshotVal)
                                toast("保存成功")
                            }
                        })
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = "替换截图(去除隐私)",
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            actionScope.launchUi {
                                val uri = mainVm.activityResults.pickImage() ?: return@launchUi
                                selectedSnapshot = null
                                val newBytes = withContext(Dispatchers.IO) {
                                    UriUtils.uri2Bytes(uri)
                                }
                                if (vm.replaceScreenshot(snapshotVal, newBytes)) {
                                    toast("替换成功")
                                } else {
                                    toast("截图尺寸不一致, 无法替换")
                                }
                            }
                        })
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = "删除", modifier = Modifier
                        .clickable(onClick = throttle {
                            actionScope.launchUi {
                                if (!mainVm.dialogRequests.confirm(
                                    title = "删除快照",
                                    text = "确定删除当前快照吗?",
                                    error = true,
                                )) return@launchUi
                                vm.deleteSnapshot(snapshotVal)
                                selectedSnapshot = null
                                toast("删除成功")
                            }
                        })
                        .then(modifier), color = colorScheme.error
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
    isSelectedMode: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClick: () -> Unit,
) {
    val indicatorColor = MaterialTheme.colorScheme.primaryContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectedMode) {
                        onSelect()
                    } else {
                        onClick()
                    }
                },
                onLongClick = onSelect,
            )
            .semantics {
                if (isSelectedMode) {
                    selected = isSelected
                    role = Role.Checkbox
                    stateDescription = if (isSelected) "已选中" else "未选中"
                }
                this.onClick(
                    label = if (isSelectedMode) {
                        if (isSelected) "取消选中" else "选中"
                    } else "查看快照详情",
                    action = null,
                )
                this.onLongClick(
                    label = if (isSelectedMode) "选中" else "进入多选模式",
                ) {
                    onSelect()
                    true
                }
            }
            .padding(horizontal = itemHorizontalPadding, vertical = itemVerticalPadding / 2)
            .drawBehind {
                drawRect(
                    color = indicatorColor,
                    size = Size(2.dp.toPx(), size.height),
                )
            }
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
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
                FixedTimeText(
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
                    text = "null",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.typography.bodyMedium.color.copy(alpha = 0.5f)
                )
            }
        }
        if (isSelectedMode) {
            Spacer(modifier = Modifier.width(8.dp))
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier.minimumInteractiveComponentSize(),
            )
        }
    }
}
