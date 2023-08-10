package li.songe.gkd.ui

import android.webkit.URLUtil
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.ToastUtils
import com.google.zxing.BarcodeFormat
import com.ramcosta.composedestinations.navigation.navigate
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.data.SubsItem
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.SubsItemCard
import li.songe.gkd.ui.destinations.SubsPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.useNavigateForQrcodeResult
import li.songe.gkd.util.useTask

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
    val subItems by vm.subsItemsFlow.collectAsState()

    var shareSubItem: SubsItem? by remember { mutableStateOf(null) }
    var shareQrcode: ImageBitmap? by remember { mutableStateOf(null) }
    var deleteSubItem: SubsItem? by remember { mutableStateOf(null) }
    var menuSubItem: SubsItem? by remember { mutableStateOf(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showAddLinkDialog by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }


    val refreshing = scope.useTask()
    val pullRefreshState = rememberPullRefreshState(refreshing.loading, refreshing.launchAsFn(IO) {
        val newItems = subItems.mapNotNull { oldItem ->
            try {
                val subscriptionRaw = SubscriptionRaw.parse5(
                    Singleton.client.get(oldItem.updateUrl).bodyAsText()
                )
                if (subscriptionRaw.version <= oldItem.version) {
                    return@mapNotNull null
                }
                val newItem = oldItem.copy(
                    updateUrl = subscriptionRaw.updateUrl ?: oldItem.updateUrl,
                    name = subscriptionRaw.name,
                    mtime = System.currentTimeMillis(),
                    version = subscriptionRaw.version
                )
                withContext(IO) {
                    newItem.subsFile.writeText(
                        SubscriptionRaw.stringify(
                            subscriptionRaw
                        )
                    )
                }
                newItem
            } catch (e: Exception) {
                ToastUtils.showShort(e.message)
                null
            }
        }
        if (newItems.isEmpty()) {
            ToastUtils.showShort("暂无更新")
        } else {
            DbSet.subsItemDao.update(*newItems.toTypedArray())
            ToastUtils.showShort("更新 ${newItems.size} 条订阅")
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
            FloatingActionButton(onClick = {}) {
                Image(painter = painterResource(SafeR.ic_add),
                    contentDescription = "add_subs_item",
                    modifier = Modifier
                        .clickable {
                            showAddDialog = true
                        }
                        .padding(4.dp)
                        .size(25.dp))
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(subItems, { it.id }) { subItem ->
                    Card(
                        modifier = Modifier
                            .animateItemPlacement()
                            .padding(vertical = 3.dp, horizontal = 8.dp)
                            .clickable(
                                onClick = scope
                                    .useTask()
                                    .launchAsFn {
                                        navController.navigate(SubsPageDestination(subItem.id))
                                    }),
                        elevation = 0.dp,
                        border = BorderStroke(1.dp, Color(0xfff6f6f6)),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        SubsItemCard(
                            subsItem = subItem,
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
            PullRefreshIndicator(
                refreshing = refreshing.loading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }


    shareSubItem?.let { shareSubItemVal ->
        Dialog(onDismissRequest = { shareSubItem = null }) {
            Box(
                Modifier
                    .width(250.dp)
                    .background(Color.White)
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "二维码", modifier = Modifier
                        .clickable {
                            shareQrcode = Singleton.barcodeEncoder
                                .encodeBitmap(
                                    shareSubItemVal.updateUrl, BarcodeFormat.QR_CODE, 500, 500
                                )
                                .asImageBitmap()
                            shareSubItem = null
                        }
                        .fillMaxWidth()
                        .padding(8.dp))
                    Text(text = "导出至剪切板", modifier = Modifier
                        .clickable {
                            ClipboardUtils.copyText(shareSubItemVal.updateUrl)
                            ToastUtils.showShort("复制成功")
                            shareSubItem = null
                        }
                        .fillMaxWidth()
                        .padding(8.dp))
                }
            }
        }
    }

    shareQrcode?.let { shareQrcodeVal ->
        Dialog(onDismissRequest = { shareQrcode = null }) {
            Image(
                bitmap = shareQrcodeVal,
                contentDescription = "qrcode",
                modifier = Modifier.size(400.dp)
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
                Text(text = "分享", modifier = Modifier
                    .clickable {
                        shareSubItem = menuSubItemVal
                        menuSubItem = null
                    }
                    .fillMaxWidth()
                    .padding(8.dp))
                Text(text = "删除", modifier = Modifier
                    .clickable {
                        deleteSubItem = menuSubItemVal
                        menuSubItem = null
                    }
                    .fillMaxWidth()
                    .padding(8.dp))
                if (subItems.firstOrNull() != menuSubItemVal) {
                    Text(
                        text = "上移",
                        modifier = Modifier
                            .clickable(
                                onClick = scope
                                    .useTask()
                                    .launchAsFn {
                                        val lastItem =
                                            subItems[subItems.indexOf(menuSubItemVal) - 1]
                                        DbSet.subsItemDao.update(
                                            lastItem.copy(
                                                order = menuSubItemVal.order
                                            ), menuSubItemVal.copy(
                                                order = lastItem.order
                                            )
                                        )
                                        menuSubItem = null
                                    })
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
                if (subItems.lastOrNull() != menuSubItemVal) {
                    Text(
                        text = "下移",
                        modifier = Modifier
                            .clickable(
                                onClick = scope
                                    .useTask()
                                    .launchAsFn {
                                        val nextItem =
                                            subItems[subItems.indexOf(menuSubItemVal) + 1]
                                        DbSet.subsItemDao.update(
                                            nextItem.copy(
                                                order = menuSubItemVal.order
                                            ), menuSubItemVal.copy(
                                                order = nextItem.order
                                            )
                                        )
                                        menuSubItem = null
                                    })
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }
        }
    }


    deleteSubItem?.let { deleteSubItemVal ->
        AlertDialog(onDismissRequest = { deleteSubItem = null },
            title = { Text(text = "是否删除该项") },
            confirmButton = {
                Button(onClick = scope.launchAsFn {
                    deleteSubItemVal.removeAssets()
                    deleteSubItem = null
                }) {
                    Text("是")
                }
            },
            dismissButton = {
                Button(onClick = {
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
                Text(
                    text = "二维码", modifier = Modifier
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
                Text(text = "链接", modifier = Modifier
                    .clickable {
                        showAddLinkDialog = true
                        showAddDialog = false
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
            Box(
                Modifier
                    .width(300.dp)
                    .background(Color.White)
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "请输入订阅链接")
                    TextField(
                        value = link, onValueChange = { link = it.trim() }, singleLine = true
                    )
                    Button(onClick = {
                        if (!URLUtil.isNetworkUrl(link)) {
                            return@Button ToastUtils.showShort("非法链接")
                        }
                        showAddLinkDialog = false
                        vm.viewModelScope.launch {
                            vm.addSubsFromUrl(url = link)
                        }
                    }) {
                        Text(text = "添加")
                    }
                }
            }
        }
    }
}