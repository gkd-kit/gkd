package li.songe.gkd.ui.home

import android.webkit.URLUtil
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ClipboardUtils
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import li.songe.gkd.data.SubsItem
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.SubsItemCard
import li.songe.gkd.ui.component.getDialogResult
import li.songe.gkd.ui.destinations.CategoryPageDestination
import li.songe.gkd.ui.destinations.GlobalRulePageDestination
import li.songe.gkd.ui.destinations.SubsPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.checkSubsUpdate
import li.songe.gkd.util.isSafeUrl
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.navigate
import li.songe.gkd.util.openUri
import li.songe.gkd.util.shareFile
import li.songe.gkd.util.subsFolder
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.subsRefreshingFlow
import li.songe.gkd.util.toast
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyColumnState

val subsNav = BottomNavItem(
    label = "订阅", icon = Icons.AutoMirrored.Filled.FormatListBulleted
)

@Composable
fun useSubsManagePage(): ScaffoldExt {
    val context = LocalContext.current
    val navController = LocalNavController.current

    val vm = hiltViewModel<HomeVm>()
    val subItems by subsItemsFlow.collectAsState()
    val subsIdToRaw by subsIdToRawFlow.collectAsState()

    var orderSubItems by remember {
        mutableStateOf(subItems)
    }
    LaunchedEffect(subItems) {
        orderSubItems = subItems
    }

    var menuSubItem: SubsItem? by remember { mutableStateOf(null) }

    var showAddLinkDialog by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }

    val refreshing by subsRefreshingFlow.collectAsState()
    val pullRefreshState = rememberPullRefreshState(refreshing, { checkSubsUpdate(true) })

    menuSubItem?.let { menuSubItemVal ->
        Dialog(onDismissRequest = { menuSubItem = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                val subsRawVal = subsIdToRaw[menuSubItemVal.id]
                if (subsRawVal != null) {
                    Text(text = "应用规则", modifier = Modifier
                        .clickable {
                            menuSubItem = null
                            navController.navigate(SubsPageDestination(subsRawVal.id))
                        }
                        .fillMaxWidth()
                        .padding(16.dp))
                    HorizontalDivider()
                    Text(text = "查看类别", modifier = Modifier
                        .clickable {
                            menuSubItem = null
                            navController.navigate(CategoryPageDestination(subsRawVal.id))
                        }
                        .fillMaxWidth()
                        .padding(16.dp))
                    HorizontalDivider()
                    Text(text = "全局规则", modifier = Modifier
                        .clickable {
                            menuSubItem = null
                            navController.navigate(GlobalRulePageDestination(subsRawVal.id))
                        }
                        .fillMaxWidth()
                        .padding(16.dp))
                    HorizontalDivider()
                }
                if (menuSubItemVal.id < 0 && subsRawVal != null) {
                    Text(text = "分享文件", modifier = Modifier
                        .clickable {
                            menuSubItem = null
                            vm.viewModelScope.launchTry {
                                val subsFile = subsFolder.resolve("${menuSubItemVal.id}.json")
                                context.shareFile(subsFile, "分享订阅文件")
                            }
                        }
                        .fillMaxWidth()
                        .padding(16.dp))
                    HorizontalDivider()
                }
                if (menuSubItemVal.updateUrl != null) {
                    Text(text = "复制链接", modifier = Modifier
                        .clickable {
                            menuSubItem = null
                            ClipboardUtils.copyText(menuSubItemVal.updateUrl)
                            toast("复制成功")
                        }
                        .fillMaxWidth()
                        .padding(16.dp))
                    HorizontalDivider()
                }
                if (subsRawVal?.supportUri != null) {
                    Text(text = "问题反馈", modifier = Modifier
                        .clickable {
                            menuSubItem = null
                            context.openUri(subsRawVal.supportUri)
                        }
                        .fillMaxWidth()
                        .padding(16.dp))
                    HorizontalDivider()
                }
                if (menuSubItemVal.id != -2L) {
                    Text(text = "删除订阅", modifier = Modifier
                        .clickable {
                            menuSubItem = null
                            vm.viewModelScope.launchTry {
                                val result = getDialogResult(
                                    "删除订阅",
                                    "是否删除订阅 ${subsIdToRaw[menuSubItemVal.id]?.name} ?",
                                )
                                if (!result) return@launchTry
                                menuSubItemVal.removeAssets()
                            }
                        }
                        .fillMaxWidth()
                        .padding(16.dp), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    LaunchedEffect(showAddLinkDialog) {
        if (!showAddLinkDialog) {
            link = ""
        }
    }
    if (showAddLinkDialog) {
        AlertDialog(title = { Text(text = "请输入订阅链接") }, text = {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it.trim() },
                maxLines = 8,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth()
            )
        }, onDismissRequest = { showAddLinkDialog = false }, confirmButton = {
            TextButton(
                enabled = link.isNotBlank(),
                onClick = {
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

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    return ScaffoldExt(
        navItem = subsNav,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = subsNav.label,
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (!subsRefreshingFlow.value) {
                    showAddLinkDialog = true
                } else {
                    toast("正在刷新订阅,请稍后添加")
                }
            }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "info",
                )
            }
        },
    ) { padding ->
        val lazyListState = rememberLazyListState()
        val reorderableLazyColumnState =
            rememberReorderableLazyColumnState(lazyListState) { from, to ->
                orderSubItems = orderSubItems.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                    forEachIndexed { index, subsItem ->
                        if (subsItem.order != index) {
                            this[index] = subsItem.copy(order = index)
                        }
                    }
                }.toImmutableList()
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
                    ReorderableItem(
                        reorderableLazyColumnState,
                        key = subItem.id,
                        enabled = !refreshing,
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }
                        Card(
                            onClick = {
                                if (!refreshing) {
                                    menuSubItem = subItem
                                }
                            },
                            modifier = Modifier
                                .longPressDraggableHandle(
                                    enabled = !refreshing,
                                    interactionSource = interactionSource,
                                    onDragStopped = {
                                        val changeItems = orderSubItems.filter { newItem ->
                                            subItems.find { oldItem -> oldItem.id == newItem.id }?.order != newItem.order
                                        }
                                        if (changeItems.isNotEmpty()) {
                                            vm.viewModelScope.launchTry {
                                                DbSet.subsItemDao.batchUpdateOrder(changeItems)
                                            }
                                        }
                                    },
                                )
                                .padding(vertical = 3.dp, horizontal = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            interactionSource = interactionSource,
                        ) {
                            SubsItemCard(
                                subsItem = subItem,
                                rawSubscription = subsIdToRaw[subItem.id],
                                index = index + 1,
                                onCheckedChange = { checked ->
                                    vm.viewModelScope.launch {
                                        DbSet.subsItemDao.updateEnable(subItem.id, checked)
                                    }
                                },
                            )
                        }
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