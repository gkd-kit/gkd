package li.songe.gkd.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.songe.gkd.MainActivity
import li.songe.gkd.data.Snapshot
import li.songe.gkd.db.DbSet
import li.songe.gkd.permission.canWriteExternalStorage
import li.songe.gkd.permission.requiredPermission
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.FixedTimeText
import li.songe.gkd.ui.component.LocalNumberCharWidth
import li.songe.gkd.ui.component.MenuGroupCard
import li.songe.gkd.ui.component.MenuItemCheckbox
import li.songe.gkd.ui.component.MenuItemRadioButton
import li.songe.gkd.ui.component.PerfCheckbox
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.measureNumberTextWidth
import li.songe.gkd.ui.component.useListScrollState
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.noRippleClickable
import li.songe.gkd.ui.style.EmptyHeight
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
import li.songe.gkd.util.shareFiles
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Serializable
data object SnapshotPageRoute : NavKey

/**
 * 这里把页面排序方式收敛成一个小枚举，避免后面菜单和分组逻辑散落字符串判断。
 */
private enum class SnapshotSortMode {
    LatestFirst,
    AppName,
}

/**
 * Activity 子分组只保留展示真正需要的信息，这样列表层可以专心渲染，不再重复做归类和排序。
 */
private data class SnapshotActivitySection(
    val key: String,
    val title: String,
    val latestSnapshotId: Long,
    val snapshots: List<Snapshot>,
)

/**
 * App 组既承载默认的一级分组，也承载可选的 Activity 二级分组，统一后页面渲染会简单很多。
 */
private data class SnapshotAppSection(
    val appId: String,
    val appName: String,
    val latestSnapshotId: Long,
    val snapshots: List<Snapshot>,
    val activitySections: List<SnapshotActivitySection>,
)

@Composable
fun SnapshotPage() {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val colorScheme = MaterialTheme.colorScheme
    val vm = viewModel<SnapshotVm>()

    val firstLoading by vm.firstLoadingFlow.collectAsState()
    val snapshots by vm.snapshotsState.collectAsState()
    val appInfoMap by appInfoMapFlow.collectAsState()
    var selectedSnapshot by remember { mutableStateOf<Snapshot?>(null) }
    val resetKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, listState) = useListScrollState(
        resetKey,
        snapshots.isEmpty(),
        firstLoading,
    )
    val timeTextWidth = measureNumberTextWidth(MaterialTheme.typography.bodySmall)

    /**
     * 页面状态拆成普通模式和多选模式两套，是为了不影响现有单击弹窗路径，同时给 V1 的批量管理留出清晰边界。
     */
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSnapshotIds by remember { mutableStateOf(emptySet<Long>()) }
    var sortMode by rememberSaveable { mutableStateOf(SnapshotSortMode.LatestFirst) }
    var groupByActivityId by rememberSaveable { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var selectionMenuExpanded by remember { mutableStateOf(false) }

    /**
     * 分组整理会对整份快照列表做 group/sort，数据量大时如果直接在 Compose 主线程里算，
     * 会明显拖慢页面首帧。这里改成异步生成展示模型，列表层只消费已经整理好的结果。
     */
    var appSections by remember { mutableStateOf<List<SnapshotAppSection>>(emptyList()) }
    LaunchedEffect(snapshots, appInfoMap, sortMode, groupByActivityId) {
        appSections = withContext(Dispatchers.Default) {
            buildSnapshotSections(
                snapshots = snapshots,
                appNameMap = snapshots.asSequence()
                    .map { snapshot -> snapshot.appId }
                    .distinct()
                    .associateWith { appId -> appInfoMap[appId]?.name ?: appId },
                sortMode = sortMode,
                groupByActivityId = groupByActivityId,
            )
        }
    }
    val selectedSnapshots = remember(snapshots, selectedSnapshotIds) {
        snapshots.filter { snapshot -> selectedSnapshotIds.contains(snapshot.id) }
    }

    /**
     * 选择模式下返回键优先退出选择，这样不会把“批量选择”误当成页面跳转。
     */
    BackHandler(isSelectionMode) {
        isSelectionMode = false
    }

    /**
     * 选择模式切换时顺手收掉弹窗和菜单，避免普通模式遗留的 UI 状态混进多选流转里。
     */
    LaunchedEffect(isSelectionMode) {
        sortMenuExpanded = false
        selectionMenuExpanded = false
        if (isSelectionMode) {
            selectedSnapshot = null
        } else if (selectedSnapshotIds.isNotEmpty()) {
            selectedSnapshotIds = emptySet()
        }
    }

    /**
     * 数据源变化时及时清理失效选中项，保证删除或刷新后不会出现“选中不存在的快照”。
     */
    LaunchedEffect(snapshots) {
        val snapshotIds = snapshots.map { snapshot -> snapshot.id }.toSet()
        val actualSelectedIds = selectedSnapshotIds.intersect(snapshotIds)
        if (actualSelectedIds != selectedSnapshotIds) {
            selectedSnapshotIds = actualSelectedIds
        }
        if (selectedSnapshot != null && !snapshotIds.contains(selectedSnapshot!!.id)) {
            selectedSnapshot = null
        }
    }

    /**
     * 多选模式如果已经没有任何选中项，就自动退回普通模式，避免用户落在一个“空选择态”里。
     */
    LaunchedEffect(isSelectionMode, selectedSnapshotIds) {
        if (isSelectionMode && selectedSnapshotIds.isEmpty()) {
            isSelectionMode = false
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (isSelectionMode) {
                        PerfIconButton(
                            imageVector = PerfIcon.Close,
                            contentDescription = "退出多选模式",
                            onClick = { isSelectionMode = false },
                        )
                    } else {
                        PerfIconButton(
                            imageVector = PerfIcon.ArrowBack,
                            contentDescription = "返回",
                            onClick = { mainVm.popPage() },
                        )
                    }
                },
                title = {
                    if (isSelectionMode) {
                        Text(text = "已选 ${selectedSnapshotIds.size}")
                    } else {
                        Text(
                            text = "快照记录",
                            modifier = Modifier.noRippleClickable { resetKey.intValue++ },
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        /**
                         * 多选模式只保留批量相关操作，避免普通模式和批量模式的动作混在一起。
                         */
                        PerfIconButton(
                            imageVector = PerfIcon.Share,
                            contentDescription = "分享所选快照",
                            onClick = throttle(
                                fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                                    toast("正在准备分享...")
                                    val zipFiles = buildSnapshotZipFiles(selectedSnapshots)
                                    withContext(Dispatchers.Main) {
                                        context.shareFiles(zipFiles, "分享快照文件")
                                    }
                                }
                            ),
                        )
                        PerfIconButton(
                            imageVector = PerfIcon.Save,
                            contentDescription = "保存所选快照到下载",
                            onClick = throttle(
                                fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                                    toast("正在保存...")
                                    saveSelectedSnapshots(context, selectedSnapshots)
                                }
                            ),
                        )
                        PerfIconButton(
                            imageVector = PerfIcon.Delete,
                            contentDescription = "删除所选快照",
                            onClick = throttle(
                                fn = vm.viewModelScope.launchAsFn {
                                    mainVm.dialogFlow.waitResult(
                                        title = "删除快照",
                                        text = "确定删除所选 ${selectedSnapshots.size} 个快照吗?",
                                        error = true,
                                    )
                                    deleteSelectedSnapshots(selectedSnapshots)
                                    toast("删除成功")
                                }
                            ),
                        )
                        Box(
                            modifier = Modifier.wrapContentSize(Alignment.TopStart)
                        ) {
                            PerfIconButton(
                                imageVector = PerfIcon.MoreVert,
                                contentDescription = "更多多选操作",
                                onClick = { selectionMenuExpanded = true },
                            )
                            DropdownMenu(
                                expanded = selectionMenuExpanded,
                                onDismissRequest = { selectionMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(text = "全选") },
                                    onClick = {
                                        selectionMenuExpanded = false
                                        selectedSnapshotIds = snapshots.map { it.id }.toSet()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(text = "清空") },
                                    onClick = {
                                        selectionMenuExpanded = false
                                        isSelectionMode = false
                                    },
                                )
                            }
                        }
                    } else {
                        /**
                         * 普通模式继续保留原来的“删除全部”入口，同时把分组/排序收进单独菜单，避免顶栏动作过载。
                         */
                        if (snapshots.isNotEmpty()) {
                            PerfIconButton(
                                imageVector = PerfIcon.Delete,
                                contentDescription = "删除所有快照",
                                onClick = throttle(
                                    fn = vm.viewModelScope.launchAsFn {
                                        mainVm.dialogFlow.waitResult(
                                            title = "删除快照",
                                            text = "确定删除所有快照记录?",
                                            error = true,
                                        )
                                        withContext(Dispatchers.IO) {
                                            snapshots.forEach { snapshot ->
                                                SnapshotExt.removeSnapshot(snapshot.id)
                                            }
                                            DbSet.snapshotDao.deleteAll()
                                        }
                                    }
                                ),
                            )
                            Box(
                                modifier = Modifier.wrapContentSize(Alignment.TopStart)
                            ) {
                                PerfIconButton(
                                    imageVector = PerfIcon.Sort,
                                    contentDescription = "排序和分组选项",
                                    onClick = { sortMenuExpanded = true },
                                )
                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false },
                                ) {
                                    MenuGroupCard(inTop = true, title = "排序") {
                                        MenuItemRadioButton(
                                            text = "按快照时间（新到旧）",
                                            selected = sortMode == SnapshotSortMode.LatestFirst,
                                            onClick = {
                                                sortMode = SnapshotSortMode.LatestFirst
                                                sortMenuExpanded = false
                                            },
                                        )
                                        MenuItemRadioButton(
                                            text = "按应用名称",
                                            selected = sortMode == SnapshotSortMode.AppName,
                                            onClick = {
                                                sortMode = SnapshotSortMode.AppName
                                                sortMenuExpanded = false
                                            },
                                        )
                                    }
                                    MenuGroupCard(title = "分组") {
                                        MenuItemCheckbox(
                                            text = "按 ActivityId 分组",
                                            checked = groupByActivityId,
                                            onClick = {
                                                groupByActivityId = !groupByActivityId
                                                sortMenuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
            )
        },
        content = { contentPadding ->
            CompositionLocalProvider(
                LocalNumberCharWidth provides timeTextWidth
            ) {
                LazyColumn(
                    modifier = Modifier.scaffoldPadding(contentPadding),
                    state = listState,
                ) {
                    /**
                     * 这里不再给首屏列表项加 animateListItem。
                     * 快照页更偏数据浏览页，进入速度比首屏动画更重要，尤其是在大量历史快照时。
                     */
                    appSections.forEach { section ->
                        item("app-${section.appId}") {
                            SnapshotAppHeader(section = section)
                        }
                        if (groupByActivityId) {
                            section.activitySections.forEach { activitySection ->
                                item("activity-${section.appId}-${activitySection.key}") {
                                    SnapshotActivityHeader(activitySection = activitySection)
                                }
                                items(
                                    items = activitySection.snapshots,
                                    key = { snapshot -> snapshot.id },
                                ) { snapshot ->
                                    SnapshotCard(
                                        modifier = Modifier,
                                        snapshot = snapshot,
                                        appName = section.appName,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = selectedSnapshotIds.contains(snapshot.id),
                                        onClick = {
                                            selectedSnapshot = snapshot
                                        },
                                        onLongClick = {
                                            isSelectionMode = true
                                            selectedSnapshotIds = selectedSnapshotIds + snapshot.id
                                        },
                                        onSelectedChange = {
                                            selectedSnapshotIds = toggleSnapshotSelection(
                                                selectedSnapshotIds,
                                                snapshot.id,
                                            )
                                        },
                                    )
                                }
                            }
                        } else {
                            items(
                                items = section.snapshots,
                                key = { snapshot -> snapshot.id },
                            ) { snapshot ->
                                SnapshotCard(
                                    modifier = Modifier,
                                    snapshot = snapshot,
                                    appName = section.appName,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedSnapshotIds.contains(snapshot.id),
                                    onClick = {
                                        selectedSnapshot = snapshot
                                    },
                                    onLongClick = {
                                        isSelectionMode = true
                                        selectedSnapshotIds = selectedSnapshotIds + snapshot.id
                                    },
                                    onSelectedChange = {
                                        selectedSnapshotIds = toggleSnapshotSelection(
                                            selectedSnapshotIds,
                                            snapshot.id,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                        Spacer(modifier = Modifier.height(EmptyHeight))
                        if (snapshots.isEmpty() && !firstLoading) {
                            EmptyText(text = "暂无数据")
                        }
                    }
                }
            }
        },
    )

    if (!isSelectionMode) {
        selectedSnapshot?.let { snapshot ->
            SnapshotActionDialog(
                colorScheme = colorScheme,
                onDismissRequest = { selectedSnapshot = null },
                onView = throttle(
                    fn = vm.viewModelScope.launchAsFn {
                        selectedSnapshot = null
                        mainVm.navigatePage(
                            ImagePreviewRoute(
                                title = appInfoMap[snapshot.appId]?.name ?: snapshot.appId,
                                uri = snapshot.screenshotFile.absolutePath,
                            )
                        )
                    }
                ),
                onShare = throttle(
                    fn = vm.viewModelScope.launchAsFn {
                        selectedSnapshot = null
                        val zipFile = SnapshotExt.snapshotZipFile(
                            snapshot.id,
                            snapshot.appId,
                            snapshot.activityId,
                        )
                        context.shareFile(zipFile, "分享快照文件")
                    }
                ),
                onSave = throttle(
                    fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                        selectedSnapshot = null
                        toast("正在保存...")
                        val zipFile = SnapshotExt.snapshotZipFile(
                            snapshot.id,
                            snapshot.appId,
                            snapshot.activityId,
                        )
                        context.saveFileToDownloads(zipFile)
                    }
                ),
                onCopyOrUpload = if (snapshot.githubAssetId != null) {
                    throttle {
                        selectedSnapshot = null
                        copyText(IMPORT_SHORT_URL + snapshot.githubAssetId)
                    }
                } else {
                    throttle {
                        selectedSnapshot = null
                        mainVm.uploadOptions.startTask(
                            getFile = { SnapshotExt.snapshotZipFile(snapshot.id) },
                            showHref = { IMPORT_SHORT_URL + it.id },
                            onSuccessResult = {
                                DbSet.snapshotDao.update(snapshot.copy(githubAssetId = it.id))
                            },
                        )
                    }
                },
                uploadLabel = if (snapshot.githubAssetId != null) "复制链接" else "生成链接(需科学上网)",
                onSaveScreenshot = throttle(
                    fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                        toast("正在保存...")
                        selectedSnapshot = null
                        requiredPermission(context, canWriteExternalStorage)
                        ImageUtils.save2Album(
                            BitmapFactory.decodeFile(snapshot.screenshotFile.absolutePath)
                        )
                        toast("保存成功")
                    }
                ),
                onReplaceScreenshot = throttle(
                    fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                        val uri = context.pickContentLauncher.launchForImageResult()
                        val oldBitmap =
                            BitmapFactory.decodeFile(snapshot.screenshotFile.absolutePath)
                        val newBytes = UriUtils.uri2Bytes(uri)
                        val newBitmap =
                            BitmapFactory.decodeByteArray(newBytes, 0, newBytes.size)
                        if (oldBitmap.width == newBitmap.width && oldBitmap.height == newBitmap.height) {
                            snapshot.screenshotFile.writeBytes(newBytes)
                            if (snapshot.githubAssetId != null) {
                                DbSet.snapshotDao.deleteGithubAssetId(snapshot.id)
                            }
                            toast("替换成功")
                            selectedSnapshot = null
                        } else {
                            toast("截图尺寸不一致, 无法替换")
                        }
                    }
                ),
                onDelete = throttle(
                    fn = vm.viewModelScope.launchAsFn {
                        selectedSnapshot = null
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
                    }
                ),
            )
        }
    }
}

/**
 * App 头部只负责表达“这一组是什么”和“这组有多少条”，避免卡片重复承担分组语义。
 */
@Composable
private fun SnapshotAppHeader(
    section: SnapshotAppSection,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = itemHorizontalPadding, vertical = itemVerticalPadding)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = section.appName,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${section.snapshots.size} 条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Activity 子头部只在开启二级分组时出现，用更轻的视觉层级提示子界面归属，避免压过真正的卡片内容
 */
@Composable
private fun SnapshotActivityHeader(
    activitySection: SnapshotActivitySection,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = itemHorizontalPadding + 12.dp,
                end = itemHorizontalPadding,
                top = 4.dp,
                bottom = 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = activitySection.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
        Text(
            text = "${activitySection.snapshots.size} 条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * 卡片在普通模式保持原有的“点一下弹菜单”语义，多选模式下改成切换选中，长按负责进入多选。
 */
@Composable
private fun SnapshotCard(
    modifier: Modifier = Modifier,
    snapshot: Snapshot,
    appName: String,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelectedChange: () -> Unit,
) {
    val actualOnClick = if (isSelectionMode) onSelectedChange else onClick
    val activityLabel = remember(snapshot.appId, snapshot.activityId) {
        snapshot.toDisplayActivityTitle()
    }
    /**
     * 这个列表页的数据量可能很大，首屏更重要的是尽快出内容。
     * 这里不用带动画的 Card 容器，改成更轻的背景块，优先降低进入页面时的组合和绘制成本。
     */
    Row(
        modifier = modifier
            .padding(horizontal = itemHorizontalPadding, vertical = itemVerticalPadding / 2)
            .background(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                shape = MaterialTheme.shapes.small,
            )
            .combinedClickable(
                onClick = actualOnClick,
                onLongClick = if (isSelectionMode) ({}) else onLongClick,
                onClickLabel = if (isSelectionMode) "切换快照选中状态" else "打开快照操作弹窗",
                onLongClickLabel = "进入多选模式",
            )
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
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
            Text(
                text = activityLabel,
                style = MaterialTheme.typography.bodyMedium,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                color = if (snapshot.activityId == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        if (isSelectionMode) {
            Spacer(modifier = Modifier.width(8.dp))
            PerfCheckbox(
                checked = isSelected,
                onCheckedChange = { onSelectedChange() },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * 单条快照的管理动作继续保留在弹窗里，避免这次 V1 把“列表分组/多选”和“浏览路径”两个重构混在一起。
 */
@Composable
private fun SnapshotActionDialog(
    colorScheme: androidx.compose.material3.ColorScheme,
    onDismissRequest: () -> Unit,
    onView: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onCopyOrUpload: () -> Unit,
    uploadLabel: String,
    onSaveScreenshot: () -> Unit,
    onReplaceScreenshot: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            val itemModifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
            Text(
                text = "查看",
                modifier = Modifier
                    .combinedClickable(onClick = onView, onLongClick = {})
                    .then(itemModifier),
            )
            HorizontalDivider()
            Text(
                text = "分享到其他应用",
                modifier = Modifier
                    .combinedClickable(onClick = onShare, onLongClick = {})
                    .then(itemModifier),
            )
            HorizontalDivider()
            Text(
                text = "保存到下载",
                modifier = Modifier
                    .combinedClickable(onClick = onSave, onLongClick = {})
                    .then(itemModifier),
            )
            HorizontalDivider()
            Text(
                text = uploadLabel,
                modifier = Modifier
                    .combinedClickable(onClick = onCopyOrUpload, onLongClick = {})
                    .then(itemModifier),
            )
            HorizontalDivider()
            Text(
                text = "保存截图到相册",
                modifier = Modifier
                    .combinedClickable(onClick = onSaveScreenshot, onLongClick = {})
                    .then(itemModifier),
            )
            HorizontalDivider()
            Text(
                text = "替换截图(去除隐私)",
                modifier = Modifier
                    .combinedClickable(onClick = onReplaceScreenshot, onLongClick = {})
                    .then(itemModifier),
            )
            HorizontalDivider()
            Text(
                text = "删除",
                modifier = Modifier
                    .combinedClickable(onClick = onDelete, onLongClick = {})
                    .then(itemModifier),
                color = colorScheme.error,
            )
        }
    }
}

/**
 * 选中集合的增删逻辑收进一个小函数里，避免页面中出现大量重复的 Set 拷贝代码。
 */
private fun toggleSnapshotSelection(
    selectedSnapshotIds: Set<Long>,
    snapshotId: Long,
): Set<Long> {
    return if (selectedSnapshotIds.contains(snapshotId)) {
        selectedSnapshotIds - snapshotId
    } else {
        selectedSnapshotIds + snapshotId
    }
}

/**
 * 先把原始快照整理成分组展示模型，后面的 LazyColumn 只负责渲染，不再关心排序和归类细节。
 */
private fun buildSnapshotSections(
    snapshots: List<Snapshot>,
    appNameMap: Map<String, String>,
    sortMode: SnapshotSortMode,
    groupByActivityId: Boolean,
): List<SnapshotAppSection> {
    val sections = snapshots.groupBy { snapshot -> snapshot.appId }
        .map { (appId, appSnapshots) ->
            val sortedSnapshots = appSnapshots.sortedByDescending { snapshot -> snapshot.id }
            val activitySections = if (groupByActivityId) {
                sortedSnapshots.groupBy { snapshot -> snapshot.activityId }
                    .map { (activityId, activitySnapshots) ->
                        SnapshotActivitySection(
                            key = activityId ?: "null",
                            title = activitySnapshots.first().toDisplayActivityTitle(),
                            latestSnapshotId = activitySnapshots.maxOf { snapshot -> snapshot.id },
                            snapshots = activitySnapshots.sortedByDescending { snapshot -> snapshot.id },
                        )
                    }
                    .sortedByDescending { activitySection -> activitySection.latestSnapshotId }
            } else {
                emptyList()
            }
            SnapshotAppSection(
                appId = appId,
                appName = appNameMap[appId] ?: appId,
                latestSnapshotId = sortedSnapshots.firstOrNull()?.id ?: 0L,
                snapshots = sortedSnapshots,
                activitySections = activitySections,
            )
        }
    return when (sortMode) {
        SnapshotSortMode.LatestFirst -> sections.sortedByDescending { section ->
            section.latestSnapshotId
        }

        SnapshotSortMode.AppName -> sections.sortedWith(
            compareBy<SnapshotAppSection> { section -> section.appName.lowercase() }
                .thenByDescending { section -> section.latestSnapshotId }
        )
    }
}

/**
 * ActivityId 在页面里主要是辅助识别，所以展示时尽量去掉和包名重复的前缀，保持列表可扫读性。
 */
private fun Snapshot.toDisplayActivityTitle(): String {
    val actualActivityId = activityId ?: return "未记录页面"
    return if (actualActivityId.startsWith(appId)) {
        actualActivityId.substring(appId.length).ifBlank { actualActivityId }
    } else {
        actualActivityId
    }
}

/**
 * 批量分享和批量保存都继续复用“每条快照一个 zip”的输出约定，这样不会突然引入新的打包格式。
 */
private suspend fun buildSnapshotZipFiles(
    snapshots: List<Snapshot>,
): List<java.io.File> {
    return snapshots.sortedByDescending { snapshot -> snapshot.id }
        .map { snapshot ->
            SnapshotExt.snapshotZipFile(
                snapshotId = snapshot.id,
                appId = snapshot.appId,
                activityId = snapshot.activityId,
            )
        }
}

/**
 * 批量保存复用单文件保存能力，但把逐个 toast 关掉，改成最后统一给一次结果反馈，避免通知噪音。
 */
private suspend fun saveSelectedSnapshots(
    context: MainActivity,
    snapshots: List<Snapshot>,
) {
    val zipFiles = buildSnapshotZipFiles(snapshots)
    zipFiles.forEach { zipFile ->
        context.saveFileToDownloads(zipFile, showToast = false)
    }
    toast("已保存 ${zipFiles.size} 个快照到下载")
}

/**
 * 批量删除先删数据库再删文件目录，保持和现有单条删除一致，同时确保列表刷新和磁盘清理都能完成。
 */
private suspend fun deleteSelectedSnapshots(
    snapshots: List<Snapshot>,
) {
    if (snapshots.isEmpty()) return
    DbSet.snapshotDao.delete(*snapshots.toTypedArray())
    withContext(Dispatchers.IO) {
        snapshots.forEach { snapshot ->
            SnapshotExt.removeSnapshot(snapshot.id)
        }
    }
}
