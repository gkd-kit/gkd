package li.songe.gkd.ui

import android.webkit.URLUtil
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import li.songe.gkd.data.SubsItem
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.SubsItemCard
import li.songe.gkd.ui.destinations.SubsPageDestination
import li.songe.gkd.util.DEFAULT_SUBS_UPDATE_URL
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.navigate
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

val subsNav = BottomNavItem(
    label = "订阅", icon = SafeR.ic_link, route = "subscription"
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SubsManagePage() {
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current

    val vm = hiltViewModel<SubsManageVm>()
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

    var showAddDialog by remember { mutableStateOf(false) }
    var showAddLinkDialog by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }


    val refreshing by vm.refreshingFlow.collectAsState()
    val pullRefreshState = rememberPullRefreshState(refreshing, vm::refreshSubs)

    val state = rememberReorderableLazyListState(onMove = { from, to ->
        orderSubItems.value = orderSubItems.value.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
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

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    text = "订阅",
                )
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (subItems.any { it.id == 0L }) {
                    showAddLinkDialog = true
                } else {
                    showAddDialog = true
                }
            }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "info",
                    modifier = Modifier.size(30.dp)
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                                    navController.navigate(SubsPageDestination(subItem.id))
                                },
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            SubsItemCard(
                                subsItem = subItem,
                                subscriptionRaw = subsIdToRaw[subItem.id],
                                index = index + 1,
                                onMenuClick = {
                                    menuSubItem = subItem
                                },
                                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                                    DbSet.subsItemDao.update(subItem.copy(enable = it))
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

    menuSubItem?.let { menuSubItemVal ->

        Dialog(onDismissRequest = { menuSubItem = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column {
                    if (menuSubItemVal.updateUrl != null) {
                        Text(text = "复制链接", modifier = Modifier
                            .clickable {
                                menuSubItem = null
                                ClipboardUtils.copyText(menuSubItemVal.updateUrl)
                                ToastUtils.showShort("复制成功")
                            }
                            .fillMaxWidth()
                            .padding(16.dp))
                    }
                    Divider()
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

    if (showAddDialog) {
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column {
                    Text(text = "导入默认订阅", modifier = Modifier
                        .clickable {
                            showAddDialog = false
                            vm.addSubsFromUrl(DEFAULT_SUBS_UPDATE_URL)
                        }
                        .fillMaxWidth()
                        .padding(16.dp))
                    Divider()
                    Text(text = "导入其它订阅", modifier = Modifier
                        .clickable {
                            showAddDialog = false
                            showAddLinkDialog = true
                        }
                        .fillMaxWidth()
                        .padding(16.dp))
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
                maxLines = 2,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth()
            )
        }, onDismissRequest = { showAddLinkDialog = false }, confirmButton = {
            TextButton(onClick = {
                if (!URLUtil.isNetworkUrl(link)) {
                    ToastUtils.showShort("非法链接")
                    return@TextButton
                }
                if (subItems.any { s -> s.updateUrl == link }) {
                    ToastUtils.showShort("链接已存在")
                    return@TextButton
                }
                showAddLinkDialog = false
                vm.addSubsFromUrl(url = link)
            }) {
                Text(text = "添加")
            }
        })
    }
}