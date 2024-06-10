package li.songe.gkd.ui.home

import android.content.Intent
import android.webkit.URLUtil
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pullrefresh.PullRefreshIndicator
import androidx.compose.material3.pullrefresh.pullRefresh
import androidx.compose.material3.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.UriUtils
import com.dylanc.activityresult.launcher.launchForResult
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import li.songe.gkd.data.TransferData
import li.songe.gkd.data.Value
import li.songe.gkd.data.deleteSubscription
import li.songe.gkd.data.importTransferData
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.SubsItemCard
import li.songe.gkd.ui.component.getDialogResult
import li.songe.gkd.ui.component.shareSubs
import li.songe.gkd.util.LOCAL_SUBS_ID
import li.songe.gkd.util.LocalLauncher
import li.songe.gkd.util.checkSubsUpdate
import li.songe.gkd.util.isSafeUrl
import li.songe.gkd.util.json
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.readFileZipByteArray
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.subsRefreshingFlow
import li.songe.gkd.util.toast
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

val subsNav = BottomNavItem(
    label = "订阅", icon = Icons.AutoMirrored.Filled.FormatListBulleted
)

@Composable
fun useSubsManagePage(): ScaffoldExt {
    val launcher = LocalLauncher.current
    val context = LocalContext.current

    val vm = hiltViewModel<HomeVm>()
    val subItems by subsItemsFlow.collectAsState()
    val subsIdToRaw by subsIdToRawFlow.collectAsState()

    var orderSubItems by remember {
        mutableStateOf(subItems)
    }
    LaunchedEffect(subItems) {
        orderSubItems = subItems
    }

    var showAddLinkDialog by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }

    val refreshing by subsRefreshingFlow.collectAsState()
    val pullRefreshState = rememberPullRefreshState(refreshing, { checkSubsUpdate(true) })
    var isSelectedMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val draggedFlag = remember { Value(false) }
    LaunchedEffect(key1 = isSelectedMode) {
        if (!isSelectedMode && selectedIds.isNotEmpty()) {
            selectedIds = emptySet()
        }
    }
    if (isSelectedMode) {
        BackHandler {
            isSelectedMode = false
        }
    }
    LaunchedEffect(key1 = subItems.size) {
        if (subItems.size <= 1) {
            isSelectedMode = false
        }
    }

    LaunchedEffect(showAddLinkDialog) {
        if (!showAddLinkDialog) {
            link = ""
        }
    }
    if (showAddLinkDialog) {
        AlertDialog(title = { Text(text = "添加订阅") }, text = {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it.trim() },
                maxLines = 8,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "请输入订阅链接",
                        style = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )
                },
                isError = link.isNotEmpty() && !URLUtil.isNetworkUrl(link),
            )
        }, onDismissRequest = { showAddLinkDialog = false }, confirmButton = {
            TextButton(enabled = link.isNotBlank(), onClick = {
                if (!URLUtil.isNetworkUrl(link)) {
                    toast("非法链接")
                    return@TextButton
                }
                if (subItems.any { s -> s.updateUrl == link }) {
                    toast("链接已存在")
                    return@TextButton
                }
                vm.viewModelScope.launchTry {
                    if (!isSafeUrl(link)) {
                        val result = getDialogResult(
                            "未知来源",
                            "你正在添加一个未验证的远程订阅\n\n这可能含有恶意的规则\n\n是否仍然确认添加?"
                        )
                        if (!result) return@launchTry
                    }
                    showAddLinkDialog = false
                    vm.addSubsFromUrl(url = link)
                }
            }) {
                Text(text = "添加")
            }
        })
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    return ScaffoldExt(
        navItem = subsNav,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                if (isSelectedMode) {
                    IconButton(onClick = { isSelectedMode = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                        )
                    }
                }
            }, title = {
                if (isSelectedMode) {
                    Text(
                        text = if (selectedIds.isNotEmpty()) selectedIds.size.toString() else "",
                    )
                } else {
                    Text(
                        text = subsNav.label,
                    )
                }
            }, actions = {
                var expanded by remember { mutableStateOf(false) }
                if (isSelectedMode) {
                    val canDeleteIds = if (selectedIds.contains(LOCAL_SUBS_ID)) {
                        selectedIds - LOCAL_SUBS_ID
                    } else {
                        selectedIds
                    }
                    if (canDeleteIds.isNotEmpty()) {
                        IconButton(onClick = vm.viewModelScope.launchAsFn {
                            val result = getDialogResult(
                                "删除订阅",
                                "是否删除所选 ${canDeleteIds.size} 个订阅?\n\n注: 不包含本地订阅",
                            )
                            if (!result) return@launchAsFn
                            deleteSubscription(*canDeleteIds.toLongArray())
                            selectedIds = selectedIds - canDeleteIds
                            if (selectedIds.size == canDeleteIds.size) {
                                isSelectedMode = false
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                            )
                        }
                    }
                    IconButton(onClick = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                        context.shareSubs(*selectedIds.toLongArray())
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = {
                        expanded = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                        )
                    }
                } else {
                    IconButton(onClick = {
                        if (subsRefreshingFlow.value) {
                            toast("正在刷新订阅,请稍后操作")
                        } else {
                            expanded = true
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                        )
                    }
                }
                Box(
                    modifier = Modifier.wrapContentSize(Alignment.TopStart)
                ) {
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        if (isSelectedMode) {
                            DropdownMenuItem(
                                text = {
                                    Text(text = "全选")
                                },
                                onClick = {
                                    expanded = false
                                    selectedIds = subItems.map { it.id }.toSet()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(text = "反选")
                                },
                                onClick = {
                                    expanded = false
                                    val newSelectedIds =
                                        subItems.map { it.id }.toSet() - selectedIds
                                    if (newSelectedIds.isEmpty()) {
                                        isSelectedMode = false
                                    }
                                    selectedIds = newSelectedIds
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                    )
                                },
                                text = {
                                    Text(text = "导入数据")
                                },
                                onClick = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                                    expanded = false
                                    val result =
                                        launcher.launchForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            type = "application/zip"
                                        })
                                    val uri = result.data?.data
                                    if (uri == null) {
                                        toast("未选择文件")
                                        return@launchAsFn
                                    }
                                    val string = readFileZipByteArray(
                                        UriUtils.uri2Bytes(uri), "${TransferData.TYPE}.json"
                                    )
                                    if (string != null) {
                                        val transferData =
                                            json.decodeFromString<TransferData>(string)
                                        importTransferData(transferData)
                                        toast("导入成功")
                                    } else {
                                        toast("导入文件无数据")
                                    }
                                },
                            )
                        }
                    }
                }
            })
        },
        floatingActionButton = {
            if (!isSelectedMode) {
                FloatingActionButton(onClick = {
                    if (subsRefreshingFlow.value) {
                        toast("正在刷新订阅,请稍后操作")
                        return@FloatingActionButton
                    }
                    showAddLinkDialog = true
                }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "info",
                    )
                }
            }
        },
    ) { padding ->
        val lazyListState = rememberLazyListState()
        val reorderableLazyColumnState =
            rememberReorderableLazyListState(lazyListState) { from, to ->
                orderSubItems = orderSubItems.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                    forEachIndexed { index, subsItem ->
                        if (subsItem.order != index) {
                            this[index] = subsItem.copy(order = index)
                        }
                    }
                }.toImmutableList()
                draggedFlag.value = true
            }
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .pullRefresh(pullRefreshState, subItems.isNotEmpty())
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(orderSubItems, { _, subItem -> subItem.id }) { index, subItem ->
                    val canDrag = !refreshing && orderSubItems.size > 1
                    ReorderableItem(
                        reorderableLazyColumnState,
                        key = subItem.id,
                        enabled = canDrag,
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }
                        SubsItemCard(
                            modifier = Modifier.longPressDraggableHandle(
                                enabled = canDrag,
                                interactionSource = interactionSource,
                                onDragStarted = {
                                    if (orderSubItems.size > 1 && !isSelectedMode) {
                                        isSelectedMode = true
                                        selectedIds = setOf(subItem.id)
                                    }
                                },
                                onDragStopped = {
                                    if (draggedFlag.value) {
                                        draggedFlag.value = false
                                        isSelectedMode = false
                                        selectedIds = emptySet()
                                    }
                                    val changeItems = orderSubItems.filter { newItem ->
                                        subItems.find { oldItem -> oldItem.id == newItem.id }?.order != newItem.order
                                    }
                                    if (changeItems.isNotEmpty()) {
                                        vm.viewModelScope.launchTry {
                                            DbSet.subsItemDao.batchUpdateOrder(changeItems)
                                        }
                                    }
                                },
                            ),
                            interactionSource = interactionSource,
                            subsItem = subItem,
                            subscription = subsIdToRaw[subItem.id],
                            index = index + 1,
                            vm = vm,
                            isSelectedMode = isSelectedMode,
                            isSelected = selectedIds.contains(subItem.id),
                            onCheckedChange = { checked ->
                                vm.viewModelScope.launch {
                                    DbSet.subsItemDao.updateEnable(subItem.id, checked)
                                }
                            },
                            onSelectedChange = {
                                val newSelectedIds = if (selectedIds.contains(subItem.id)) {
                                    selectedIds.toMutableSet().apply {
                                        remove(subItem.id)
                                    }
                                } else {
                                    selectedIds + subItem.id
                                }
                                selectedIds = newSelectedIds
                                if (newSelectedIds.isEmpty()) {
                                    isSelectedMode = false
                                }
                            },
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
            PullRefreshIndicator(
                refreshing = refreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}