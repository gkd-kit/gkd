package li.songe.gkd.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.songe.gkd.MainActivity
import li.songe.gkd.data.Snapshot
import li.songe.gkd.db.DbSet
import li.songe.gkd.permission.canWriteExternalStorage
import li.songe.gkd.permission.requiredPermission
import li.songe.gkd.ui.component.AppIcon
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.noRippleClickable
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

@Serializable
data object SnapshotPageRoute : NavKey

private val SnapshotSidebarWidth = 72.dp
private val SnapshotSidebarPadding = 8.dp
private val SnapshotGridSpacing = 12.dp
private val SnapshotGridPadding = 16.dp
private val SnapshotGridMinCardWidth = 172.dp // 增加最小宽度，确保横屏快照在格子里有足够高度

@Composable
fun SnapshotPage() {
    val mainVm = LocalMainViewModel.current
    val colorScheme = MaterialTheme.colorScheme
    val vm = viewModel<SnapshotVm>()

    val firstLoading by vm.firstLoadingFlow.collectAsState()
    val appGroups by vm.appGroupsState.collectAsState()
    val selectedSnapshotState = remember { mutableStateOf<Snapshot?>(null) }
    val previewSnapshotState = remember { mutableStateOf<Snapshot?>(null) }
    val selectedAppIdState = rememberSaveable { mutableStateOf<String?>(null) }

    val selectedGroup = remember(appGroups, selectedAppIdState.value) {
        appGroups.firstOrNull { it.appId == selectedAppIdState.value } ?: appGroups.firstOrNull()
    }

    Scaffold(topBar = {
        PerfTopAppBar(
            navigationIcon = {
                PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = { mainVm.popPage() })
            },
            title = {
                Column {
                    Text(text = "快照记录")
                    selectedGroup?.let { group ->
                        Text(
                            text = group.appName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            actions = {
                if (appGroups.isNotEmpty()) {
                    PerfIconButton(
                        imageVector = PerfIcon.Delete,
                        onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                            mainVm.dialogFlow.waitResult(
                                title = "删除快照",
                                text = "确定删除所有快照记录?",
                                error = true,
                            )
                            appGroups.flatMap { it.snapshots }.forEach { snapshot ->
                                SnapshotExt.removeSnapshot(snapshot.id)
                            }
                            DbSet.snapshotDao.deleteAll()
                        })
                    )
                }
            }
        )
    }) { contentPadding ->
        when {
            appGroups.isEmpty() && !firstLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyText(text = "暂无数据")
                }
            }

            selectedGroup != null -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                ) {
                    SnapshotSidebar(
                        groups = appGroups,
                        selectedAppId = selectedGroup.appId,
                        onSelectApp = { appId -> selectedAppIdState.value = appId },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(colorScheme.outlineVariant.copy(alpha = 0.35f))
                    )
                    SnapshotGrid(
                        modifier = Modifier.weight(1f),
                        group = selectedGroup,
                        onClickSnapshot = { snapshot -> selectedSnapshotState.value = snapshot },
                        onPreviewSnapshot = { snapshot -> previewSnapshotState.value = snapshot },
                    )
                }
            }
        }
    }

    selectedSnapshotState.value?.let { snapshot ->
        SnapshotActionDialog(
            snapshot = snapshot,
            onDismissRequest = { selectedSnapshotState.value = null },
        )
    }

    previewSnapshotState.value?.let { snapshot ->
        SnapshotPeekDialog(
            snapshot = snapshot,
            onDismissRequest = { previewSnapshotState.value = null },
        )
    }
}

@Composable
private fun SnapshotSidebar(
    groups: List<SnapshotAppGroup>,
    selectedAppId: String,
    onSelectApp: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .width(SnapshotSidebarWidth)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentPadding = PaddingValues(vertical = SnapshotSidebarPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        lazyItems(groups, key = { it.appId }) { group ->
            val selected = group.appId == selectedAppId
            Box(
                modifier = Modifier
                    .padding(horizontal = SnapshotSidebarPadding)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        }
                    )
                    .noRippleClickable { onSelectApp(group.appId) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    appId = group.appId,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun SnapshotGrid(
    modifier: Modifier = Modifier,
    group: SnapshotAppGroup,
    onClickSnapshot: (Snapshot) -> Unit,
    onPreviewSnapshot: (Snapshot) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val availableWidth = maxWidth - SnapshotGridPadding * 2
        // 动态计算列数并限制最大列数，确保即便在横屏下，卡片宽度也足够显示横屏截图。
        val columnCount = (availableWidth / SnapshotGridMinCardWidth).toInt().coerceIn(2, 5)
        val cardWidth =
            (availableWidth - SnapshotGridSpacing * (columnCount - 1)) / columnCount

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columnCount),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SnapshotGridPadding,
                top = SnapshotGridPadding,
                end = SnapshotGridPadding,
                bottom = SnapshotGridPadding * 2,
            ),
            horizontalArrangement = Arrangement.spacedBy(SnapshotGridSpacing),
            verticalItemSpacing = SnapshotGridSpacing,
        ) {
            staggeredItems(group.snapshots, key = { it.id }) { snapshot ->
                SnapshotGridCard(
                    snapshot = snapshot,
                    thumbnailWidth = cardWidth,
                    onClick = { onClickSnapshot(snapshot) },
                    onPreview = { onPreviewSnapshot(snapshot) },
                )
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                Spacer(modifier = Modifier.size(1.dp))
            }
        }
    }
}

@Composable
private fun SnapshotGridCard(
    snapshot: Snapshot,
    thumbnailWidth: Dp,
    onClick: () -> Unit,
    onPreview: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme
    val thumbnailWidthPx = remember(thumbnailWidth, density) {
        with(density) { thumbnailWidth.roundToPx().coerceAtLeast(1) }
    }
    val aspectRatio = remember(snapshot.screenWidth, snapshot.screenHeight) {
        if (snapshot.screenWidth > 0 && snapshot.screenHeight > 0) {
            snapshot.screenWidth.toFloat() / snapshot.screenHeight.toFloat()
        } else {
            3f / 4f
        }
    }
    val thumbnailHeightPx = remember(thumbnailWidthPx, aspectRatio) {
        (thumbnailWidthPx / aspectRatio).toInt().coerceAtLeast(1)
    }

    val thumbnailRequest = remember(snapshot.id, thumbnailWidthPx, thumbnailHeightPx) {
        ImageRequest.Builder(context)
            .data(snapshot.screenshotFile)
            .size(thumbnailWidthPx, thumbnailHeightPx)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colorScheme.surfaceContainer)
            .noRippleClickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.88f))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onPreview,
                    onClickLabel = "打开快照操作菜单",
                    onLongClickLabel = "预览快照大图",
                )
        ) {
            AsyncImage(
                model = thumbnailRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
            )
            Text(
                text = snapshot.date,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.68f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
        Text(
            text = snapshot.shortActivityLabel(),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
    }
}

@Composable
private fun SnapshotPeekDialog(
    snapshot: Snapshot,
    onDismissRequest: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val aspectRatio = remember(snapshot.screenWidth, snapshot.screenHeight) {
        if (snapshot.screenWidth > 0 && snapshot.screenHeight > 0) {
            snapshot.screenWidth.toFloat() / snapshot.screenHeight.toFloat()
        } else {
            1f
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .noRippleClickable(onClick = onDismissRequest),
            contentAlignment = Alignment.Center,
        ) {
            val parentWidth = maxWidth
            val parentHeight = maxHeight
            val parentAspectRatio = if (parentHeight > 0.dp) parentWidth / parentHeight else 1f

            val containerModifier = if (aspectRatio > parentAspectRatio) {
                Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(aspectRatio)
            } else {
                Modifier
                    .fillMaxHeight(0.85f)
                    .aspectRatio(aspectRatio)
            }

            Box(
                modifier = containerModifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(colorScheme.surfaceContainerHighest)
                    .noRippleClickable(onClick = {}),
            ) {
                AsyncImage(
                    model = snapshot.screenshotFile,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                )
                PerfIconButton(
                    imageVector = PerfIcon.Close,
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun SnapshotActionDialog(
    snapshot: Snapshot,
    onDismissRequest: () -> Unit,
) {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<SnapshotVm>()
    val colorScheme = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            val itemModifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)

            Text(
                text = "查看",
                modifier = itemModifier.noRippleClickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                    onDismissRequest()
                    mainVm.navigatePage(
                        ImagePreviewRoute(
                            title = appInfoMapFlow.value[snapshot.appId]?.name ?: snapshot.appId,
                            uri = snapshot.screenshotFile.absolutePath,
                        )
                    )
                }))
            )
            HorizontalDivider()
            Text(
                text = "分享到其他应用",
                modifier = itemModifier.noRippleClickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                    onDismissRequest()
                    val zipFile = SnapshotExt.snapshotZipFile(
                        snapshot.id,
                        snapshot.appId,
                        snapshot.activityId,
                    )
                    context.shareFile(zipFile, "分享快照文件")
                }))
            )
            HorizontalDivider()
            Text(
                text = "保存到下载",
                modifier = itemModifier.noRippleClickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                    onDismissRequest()
                    toast("正在保存...")
                    val zipFile = SnapshotExt.snapshotZipFile(
                        snapshot.id,
                        snapshot.appId,
                        snapshot.activityId,
                    )
                    context.saveFileToDownloads(zipFile)
                }))
            )
            HorizontalDivider()
            if (snapshot.githubAssetId != null) {
                Text(
                    text = "复制链接",
                    modifier = itemModifier.noRippleClickable(onClick = throttle {
                        onDismissRequest()
                        copyText(IMPORT_SHORT_URL + snapshot.githubAssetId)
                    })
                )
            } else {
                Text(
                    text = "生成链接(需科学上网)",
                    modifier = itemModifier.noRippleClickable(onClick = throttle {
                        onDismissRequest()
                        mainVm.uploadOptions.startTask(
                            getFile = { SnapshotExt.snapshotZipFile(snapshot.id) },
                            showHref = { IMPORT_SHORT_URL + it.id },
                            onSuccessResult = {
                                DbSet.snapshotDao.update(snapshot.copy(githubAssetId = it.id))
                            }
                        )
                    })
                )
            }
            HorizontalDivider()
            Text(
                text = "保存截图到相册",
                modifier = itemModifier.noRippleClickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                    toast("正在保存...")
                    onDismissRequest()
                    requiredPermission(context, canWriteExternalStorage)
                    ImageUtils.save2Album(BitmapFactory.decodeFile(snapshot.screenshotFile.absolutePath))
                    toast("保存成功")
                }))
            )
            HorizontalDivider()
            Text(
                text = "替换截图(去除隐私)",
                modifier = itemModifier.noRippleClickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                    val uri = context.pickContentLauncher.launchForImageResult()
                    val oldBitmap = BitmapFactory.decodeFile(snapshot.screenshotFile.absolutePath)
                    val newBytes = UriUtils.uri2Bytes(uri)
                    val newBitmap = BitmapFactory.decodeByteArray(newBytes, 0, newBytes.size)
                    if (oldBitmap.width == newBitmap.width && oldBitmap.height == newBitmap.height) {
                        snapshot.screenshotFile.writeBytes(newBytes)
                        if (snapshot.githubAssetId != null) {
                            DbSet.snapshotDao.deleteGithubAssetId(snapshot.id)
                        }
                        toast("替换成功")
                        onDismissRequest()
                    } else {
                        toast("截图尺寸不一致, 无法替换")
                    }
                }))
            )
            HorizontalDivider()
            Text(
                text = "删除",
                modifier = itemModifier.noRippleClickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                    onDismissRequest()
                    mainVm.dialogFlow.waitResult(
                        title = "删除快照",
                        text = "确定删除当前快照吗?",
                        error = true,
                    )
                    DbSet.snapshotDao.delete(snapshot)
                    withContext(Dispatchers.IO) {
                        SnapshotExt.removeSnapshot(snapshot.id)
                    }
                    toast("删除成功")
                })),
                color = colorScheme.error,
            )
        }
    }
}

private fun Snapshot.shortActivityLabel(): String {
    val actualActivityId = activityId ?: return "未记录页面"
    return if (actualActivityId.startsWith(appId)) {
        actualActivityId.substring(appId.length).ifBlank { actualActivityId }
    } else {
        actualActivityId
    }
}
