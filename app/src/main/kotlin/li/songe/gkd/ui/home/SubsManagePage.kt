package li.songe.gkd.ui.home

import android.content.Intent
import android.net.Uri
import android.webkit.URLUtil
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.pullrefresh.PullRefreshIndicator
import androidx.compose.material3.pullrefresh.pullRefresh
import androidx.compose.material3.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ClipboardUtils
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import li.songe.gkd.data.SubsItem
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.SubsItemCard
import li.songe.gkd.ui.component.getDialogResult
import li.songe.gkd.ui.destinations.CategoryPageDestination
import li.songe.gkd.ui.destinations.GlobalRulePageDestination
import li.songe.gkd.ui.destinations.SubsPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.isSafeUrl
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.navigate
import li.songe.gkd.util.shareFile
import li.songe.gkd.util.subsFolder
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.toast
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

val subsNav = BottomNavItem(
    label = "订阅", icon = Icons.AutoMirrored.Filled.FormatListBulleted
)

@Composable
fun useSubsManagePage(): ScaffoldExt {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current

    val vm = hiltViewModel<HomeVm>()
    val subItems by subsItemsFlow.collectAsState()
    val subsIdToRaw by subsIdToRawFlow.collectAsState()

    val orderSubItems = remember {
        mutableStateOf(subItems)
    }
    LaunchedEffect(subItems, block = {
        orderSubItems.value = subItems
    })


    var deleteSubItem: SubsItem? by remember { mutableStateOf(null) }
    var menuSubItem: SubsItem? by remember { mutableStateOf(null) }

    var showAddLinkDialog by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }

    val refreshing by vm.refreshingFlow.collectAsState()
    val pullRefreshState = rememberPullRefreshState(refreshing, vm::refreshSubs)

    val state = rememberReorderableLazyListState(onMove = { from, to ->
        orderSubItems.value = orderSubItems.value.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }.toImmutableList()
    }, onDragEnd = { _, _ ->
        vm.viewModelScope.launch(Dispatchers.IO) {
            val changeItems = mutableListOf<SubsItem>()
            orderSubItems.value.forEachIndexed { index, subsItem ->
                if (subItems[index] != subsItem) {
                    changeItems.add(
                        subsItem.copy(
                            order = index
                        )
                    )
                }
            }
            DbSet.subsItemDao.update(*changeItems.toTypedArray())
        }
    })

    menuSubItem?.let { menuSubItemVal ->
        Dialog(onDismissRequest = { menuSubItem = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column {
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
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW, Uri.parse(subsRawVal.supportUri)
                                    )
                                )
                            }
                            .fillMaxWidth()
                            .padding(16.dp))
                        HorizontalDivider()
                    }
                    if (menuSubItemVal.id != -2L) {
                        Text(text = "删除订阅", modifier = Modifier
                            .clickable {
                                deleteSubItem = menuSubItemVal
                                menuSubItem = null
                            }
                            .fillMaxWidth()
                            .padding(16.dp), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    deleteSubItem?.let { deleteSubItemVal ->
        AlertDialog(onDismissRequest = { deleteSubItem = null },
            title = { Text(text = "是否删除 ${subsIdToRaw[deleteSubItemVal.id]?.name}?") },
            confirmButton = {
                TextButton(onClick = scope.launchAsFn {
                    deleteSubItem = null
                    deleteSubItemVal.removeAssets()
                }) {
                    Text(text = "是", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    deleteSubItem = null
                }) {
                    Text(text = "否")
                }
            })
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
                maxLines = 2,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth()
            )
        }, onDismissRequest = { showAddLinkDialog = false }, confirmButton = {
            TextButton(onClick = {
                if (!URLUtil.isNetworkUrl(link)) {
                    toast("非法链接")
                    return@TextButton
                }
                if (subItems.any { s -> s.updateUrl == link }) {
                    toast("链接已存在")
                    return@TextButton
                }
                vm.viewModelScope.launch {
                    if (!isSafeUrl(link)) {
                        val result = getDialogResult(
                            "未知来源",
                            "你正在添加一个未验证的远程订阅\n\n这可能含有恶意的规则\n\n是否仍然确认添加?"
                        )
                        if (!result) return@launch
                    }
                    showAddLinkDialog = false
                    vm.addSubsFromUrl(url = link)
                }
            }) {
                Text(text = "添加")
            }
        })
    }

    return ScaffoldExt(
        navItem = subsNav,
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (!vm.refreshingFlow.value) {
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
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .pullRefresh(pullRefreshState, subItems.isNotEmpty())
        ) {
            LazyColumn(
                state = state.listState,
                modifier = Modifier
                    .reorderable(state)
                    .detectReorderAfterLongPress(state)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(orderSubItems.value, { _, subItem -> subItem.id }) { index, subItem ->
                    ReorderableItem(state, key = subItem.id) { isDragging ->
                        val elevation = animateDpAsState(
                            if (isDragging) 16.dp else 0.dp, label = ""
                        )
                        Card(
                            modifier = Modifier
                                .shadow(elevation.value)
                                .animateItemPlacement()
                                .padding(vertical = 3.dp, horizontal = 8.dp)
                                .clickable {
                                    menuSubItem = subItem
                                },
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            SubsItemCard(
                                subsItem = subItem,
                                rawSubscription = subsIdToRaw[subItem.id],
                                index = index + 1,
                                onCheckedChange = { checked ->
                                    vm.viewModelScope.launch {
                                        DbSet.subsItemDao.update(subItem.copy(enable = checked))
                                    }
                                },
                            )
                        }
                    }
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