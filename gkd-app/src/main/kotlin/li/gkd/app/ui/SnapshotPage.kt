package li.gkd.app.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import li.gkd.app.MainActivity
import li.gkd.app.data.Snapshot
import li.gkd.app.permission.PermissionStates
import li.gkd.app.snapshot.SnapshotStore
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.component.FixedTimeText
import li.gkd.app.ui.component.AppDialog
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.Loadable
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.itemVerticalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.IMPORT_SHORT_URL
import li.gkd.app.util.UriUtils
import li.gkd.app.util.copyText
import li.gkd.app.util.launchTry
import li.gkd.app.util.throttle
import li.gkd.app.util.toast

@Serializable
data object SnapshotPageRoute : NavKey

@Composable
fun SnapshotPage() {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
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
        PerfTopAppBar(
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = {
                    mainVm.popPage()
                })
            },
            title = {
                Text(
                    text = "快照记录",
                    modifier = Modifier.noRippleClickable(onClick = pageScrollState::resetScroll),
                )
            },
            actions = {
                if (snapshots.isNotEmpty()) {
                    PerfIconButton(
                        imageVector = PerfIcon.Delete,
                        onClick = throttle {
                            actionScope.launchTry {
                                if (!mainVm.dialogRequests.confirm(
                                    title = "删除快照",
                                    text = "确定删除所有快照记录?",
                                    error = true,
                                )) return@launchTry
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
                            actionScope.launchTry {
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
                            actionScope.launchTry {
                                selectedSnapshot = null
                                toast("正在保存...")
                                val archive = vm.buildShareArchive(snapshotVal)
                                try {
                                    context.saveFileToDownloads(archive)
                                } finally {
                                    SnapshotStore.deleteArchive(archive)
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
                            actionScope.launchTry {
                                toast("正在保存...")
                                selectedSnapshot = null
                                if (!mainVm.permissionRequests.ensurePermissions(
                                        PermissionStates.writeExternalStorage,
                                    )
                                ) {
                                    return@launchTry
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
                            actionScope.launchTry {
                                val uri = mainVm.activityResults.pickImage() ?: return@launchTry
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
                            actionScope.launchTry {
                                if (!mainVm.dialogRequests.confirm(
                                    title = "删除快照",
                                    text = "确定删除当前快照吗?",
                                    error = true,
                                )) return@launchTry
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
                FixedTimeText(
                    text = snapshot.date,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val showActivityId = if (snapshot.activityId != null) {
                if (snapshot.activityId.startsWith(snapshot.appId)) {
                    snapshot.activityId.substring(snapshot.appId.length)
                } else {
                    snapshot.activityId
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
    }
}
