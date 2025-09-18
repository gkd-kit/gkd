package li.songe.gkd.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ImagePreviewPageDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.songe.gkd.MainActivity
import li.songe.gkd.data.Snapshot
import li.songe.gkd.db.DbSet
import li.songe.gkd.permission.canWriteExternalStorage
import li.songe.gkd.permission.requiredPermission
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.FixedTimeText
import li.songe.gkd.ui.component.LocalNumberCharWidth
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.animateListItem
import li.songe.gkd.ui.component.measureNumberTextWidth
import li.songe.gkd.ui.component.useListScrollState
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.noRippleClickable
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.ProfileTransitions
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.ui.style.itemVerticalPadding
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.IMPORT_SHORT_URL
import li.songe.gkd.util.ImageUtils
import li.songe.gkd.util.SnapshotExt
import li.songe.gkd.util.UriUtils
import li.songe.gkd.util.appInfoMapFlow
import li.songe.gkd.util.copyText
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.saveFileToDownloads
import li.songe.gkd.util.shareFile
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun SnapshotPage() {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val colorScheme = MaterialTheme.colorScheme
    val vm = viewModel<SnapshotVm>()

    val firstLoading by vm.firstLoadingFlow.collectAsState()
    val snapshots by vm.snapshotsState.collectAsState()
    var selectedSnapshot by remember { mutableStateOf<Snapshot?>(null) }
    val resetKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, listState) = useListScrollState(
        resetKey,
        snapshots.isEmpty(),
        firstLoading,
    )
    val timeTextWidth = measureNumberTextWidth(MaterialTheme.typography.bodySmall)

    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        PerfTopAppBar(
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = {
                    mainVm.popBackStack()
                })
            },
            title = {
                Text(
                    text = "快照记录",
                    modifier = Modifier.noRippleClickable { resetKey.intValue++ },
                )
            },
            actions = {
                if (snapshots.isNotEmpty()) {
                    PerfIconButton(
                        imageVector = PerfIcon.Delete,
                        onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            mainVm.dialogFlow.waitResult(
                                title = "删除快照",
                                text = "确定删除所有快照记录?",
                                error = true,
                            )
                            snapshots.forEach { s ->
                                SnapshotExt.removeSnapshot(s.id)
                            }
                            DbSet.snapshotDao.deleteAll()
                        })
                    )
                }
            })
    }, content = { contentPadding ->
        CompositionLocalProvider(
            LocalNumberCharWidth provides timeTextWidth
        ) {
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
            ) {
                items(snapshots, { it.id }) { snapshot ->
                    SnapshotCard(
                        modifier = Modifier.animateListItem(this),
                        snapshot = snapshot,
                        onClick = {
                            selectedSnapshot = snapshot
                        }
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    if (snapshots.isEmpty() && !firstLoading) {
                        EmptyText(text = "暂无记录")
                    }
                }
            }
        }
    })

    selectedSnapshot?.let { snapshotVal ->
        Dialog(onDismissRequest = { selectedSnapshot = null }) {
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
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            selectedSnapshot = null
                            mainVm.navigatePage(
                                ImagePreviewPageDestination(
                                    title = appInfoMapFlow.value[snapshotVal.appId]?.name
                                        ?: snapshotVal.appId,
                                    uri = snapshotVal.screenshotFile.absolutePath,
                                )
                            )
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = "分享到其他应用",
                    modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            selectedSnapshot = null
                            val zipFile = SnapshotExt.snapshotZipFile(
                                snapshotVal.id,
                                snapshotVal.appId,
                                snapshotVal.activityId
                            )
                            context.shareFile(zipFile, "分享快照文件")
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = "保存到下载",
                    modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            selectedSnapshot = null
                            toast("正在保存...")
                            val zipFile = SnapshotExt.snapshotZipFile(
                                snapshotVal.id,
                                snapshotVal.appId,
                                snapshotVal.activityId
                            )
                            context.saveFileToDownloads(zipFile)
                        }))
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
                                mainVm.uploadOptions.startTask(
                                    getFile = { SnapshotExt.snapshotZipFile(snapshotVal.id) },
                                    showHref = { IMPORT_SHORT_URL + it.id },
                                    onSuccessResult = {
                                        DbSet.snapshotDao.update(snapshotVal.copy(githubAssetId = it.id))
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
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            toast("正在保存...")
                            selectedSnapshot = null
                            requiredPermission(context, canWriteExternalStorage)
                            ImageUtils.save2Album(BitmapFactory.decodeFile(snapshotVal.screenshotFile.absolutePath))
                            toast("保存成功")
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = "替换截图(去除隐私)",
                    modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            val uri = context.pickContentLauncher.launchForImageResult()
                            val oldBitmap =
                                BitmapFactory.decodeFile(snapshotVal.screenshotFile.absolutePath)
                            val newBytes = UriUtils.uri2Bytes(uri)
                            val newBitmap =
                                BitmapFactory.decodeByteArray(newBytes, 0, newBytes.size)
                            if (oldBitmap.width == newBitmap.width && oldBitmap.height == newBitmap.height) {
                                snapshotVal.screenshotFile.writeBytes(newBytes)
                                if (snapshotVal.githubAssetId != null) {
                                    // 当本地快照变更时, 移除快照链接
                                    DbSet.snapshotDao.deleteGithubAssetId(snapshotVal.id)
                                }
                                toast("替换成功")
                                selectedSnapshot = null
                            } else {
                                toast("截图尺寸不一致, 无法替换")
                            }
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = "删除", modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            selectedSnapshot = null
                            mainVm.dialogFlow.waitResult(
                                title = "删除快照",
                                text = "确定删除当前快照吗?",
                                error = true,
                            )
                            DbSet.snapshotDao.delete(snapshotVal)
                            withContext(Dispatchers.IO) {
                                SnapshotExt.removeSnapshot(snapshotVal.id)
                            }
                            toast("删除成功")
                        }))
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
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = itemHorizontalPadding, vertical = itemVerticalPadding / 2)
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val appInfo = appInfoMapFlow.collectAsState().value[snapshot.appId]
                val showAppName = appInfo?.name ?: snapshot.appId
                Text(
                    text = showAppName,
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
