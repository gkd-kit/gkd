package li.songe.gkd.ui

import android.webkit.URLUtil
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.ToastUtils
import com.google.zxing.BarcodeFormat
import com.ramcosta.composedestinations.navigation.navigate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import li.songe.gkd.data.SubsItem
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.SubsItemCard
import li.songe.gkd.ui.destinations.SubsPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.getImportUrl
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.subsIdToRawFlow
import li.songe.gkd.util.subsItemsFlow
import li.songe.gkd.util.useNavigateForQrcodeResult
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

val subsNav = BottomNavItem(
    label = "订阅", icon = SafeR.ic_link, route = "subscription"
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun SubsManagePage() {
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val navigateForQrcodeResult = useNavigateForQrcodeResult()

    val vm = hiltViewModel<SubsManageVm>()
    val homeVm = hiltViewModel<HomePageVm>()
    val subItems by subsItemsFlow.collectAsState()
    val subsIdToRaw by subsIdToRawFlow.collectAsState()

    val intent by homeVm.intentFlow.collectAsState()
    LaunchedEffect(key1 = intent, block = {
        val importUrl = getImportUrl(intent)
        if (importUrl != null) {

            homeVm.intentFlow.value = null
        }
    })

    val orderSubItems = remember {
        mutableStateOf(subItems)
    }
    LaunchedEffect(subItems, block = {
        orderSubItems.value = subItems
    })


    var shareSubItem: SubsItem? by remember { mutableStateOf(null) }
    var shareQrcode: ImageBitmap? by remember { mutableStateOf(null) }
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
            TopAppBar(backgroundColor = Color(0xfff8f9f9), title = {
                Text(
                    text = "订阅", color = Color.Black
                )
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
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
                            elevation = 0.dp,
                            border = BorderStroke(1.dp, Color(0xfff6f6f6)),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            SubsItemCard(
                                subsItem = subItem,
                                subscriptionRaw = subsIdToRaw[subItem.id],
                                index = index + 1,
                                onMenuClick = {
                                    menuSubItem = subItem
                                },
                                onCheckedChange = scope.launchAsFn<Boolean> {
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


    shareSubItem?.let { shareSubItemVal ->
        Dialog(onDismissRequest = { shareSubItem = null }) {
            Column(
                modifier = Modifier
                    .width(250.dp)
                    .background(Color.White)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "显示二维码", modifier = Modifier
                    .clickable {
                        shareSubItem = null
                        scope.launch(Dispatchers.Default) {
                            shareQrcode = Singleton.barcodeEncoder
                                .encodeBitmap(
                                    shareSubItemVal.updateUrl, BarcodeFormat.QR_CODE, 500, 500
                                )
                                .asImageBitmap()
                        }
                    }
                    .fillMaxWidth()
                    .padding(8.dp))
                Text(text = "导出至剪切板", modifier = Modifier
                    .clickable {
                        shareSubItem = null
                        ClipboardUtils.copyText(shareSubItemVal.updateUrl)
                        ToastUtils.showShort("复制成功")
                    }
                    .fillMaxWidth()
                    .padding(8.dp))
            }
        }
    }

    shareQrcode?.let { shareQrcodeVal ->
        Dialog(onDismissRequest = { shareQrcode = null }) {
            Image(
                bitmap = shareQrcodeVal, contentDescription = null, modifier = Modifier.size(400.dp)
            )
        }
    }

    menuSubItem?.let { menuSubItemVal ->

        Dialog(onDismissRequest = { menuSubItem = null }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .width(200.dp)
                    .wrapContentHeight()
                    .background(Color.White)
                    .padding(8.dp)
            ) {
                if (menuSubItemVal.updateUrl != null) {
                    Text(text = "分享", modifier = Modifier
                        .clickable {
                            shareSubItem = menuSubItemVal
                            menuSubItem = null
                        }
                        .fillMaxWidth()
                        .padding(8.dp))
                }

                Text(text = "删除", modifier = Modifier
                    .clickable {
                        deleteSubItem = menuSubItemVal
                        menuSubItem = null
                    }
                    .fillMaxWidth()
                    .padding(8.dp))

            }
        }
    }


    deleteSubItem?.let { deleteSubItemVal ->
        AlertDialog(onDismissRequest = { deleteSubItem = null },
            title = { Text(text = "是否删除 ${subsIdToRaw[deleteSubItemVal.id]?.name}?") },
            confirmButton = {
                TextButton(onClick = scope.launchAsFn {
                    deleteSubItemVal.removeAssets()
                    deleteSubItem = null
                }) {
                    Text("是")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    deleteSubItem = null
                }) {
                    Text("否")
                }
            })
    }

    if (showAddDialog) {
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Column(
                modifier = Modifier
                    .width(250.dp)
                    .background(Color.White)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (subItems.all { it.id != 0L }) {
                    Text(text = "导入默认订阅", modifier = Modifier
                        .clickable {
                            showAddDialog = false
                            vm.addSubsFromUrl("https://registry.npmmirror.com/@gkd-kit/subscription/latest/files")
                        }
                        .fillMaxWidth()
                        .padding(8.dp))
                }

                Text(
                    text = "扫描二维码导入", modifier = Modifier
                        .clickable(onClick = scope.launchAsFn {
                            showAddDialog = false
                            val qrCode = navigateForQrcodeResult()
                            val contents = qrCode.contents
                            if (contents != null) {
                                showAddLinkDialog = true
                                link = contents
                            }
                        })
                        .fillMaxWidth()
                        .padding(8.dp)
                )
                Text(text = "输入链接导入", modifier = Modifier
                    .clickable {
                        showAddDialog = false
                        showAddLinkDialog = true
                    }
                    .fillMaxWidth()
                    .padding(8.dp))
            }
        }
    }



    LaunchedEffect(showAddLinkDialog) {
        if (!showAddLinkDialog) {
            link = ""
        }
    }
    if (showAddLinkDialog) {
        Dialog(onDismissRequest = { showAddLinkDialog = false }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .width(300.dp)
                    .background(Color.White)
                    .padding(10.dp)
            ) {
                Text(text = "请输入订阅链接", fontSize = 20.sp)
                Spacer(modifier = Modifier.height(2.dp))
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it.trim() },
                    maxLines = 2,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                )
                Row(
                    horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()
                ) {
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
                }
            }
        }
    }
}