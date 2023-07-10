package li.songe.gkd.ui.home

import android.webkit.URLUtil
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.ToastUtils
import com.google.zxing.BarcodeFormat
import com.ramcosta.composedestinations.navigation.navigate
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import li.songe.gkd.data.SubsItem
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.SubsItemCard
import li.songe.gkd.ui.destinations.SubsPageDestination
import li.songe.gkd.utils.LaunchedEffectTry
import li.songe.gkd.utils.LocalNavController
import li.songe.gkd.utils.SafeR
import li.songe.gkd.utils.Singleton
import li.songe.gkd.utils.launchAsFn
import li.songe.gkd.utils.rememberCache
import li.songe.gkd.utils.useNavigateForQrcodeResult
import li.songe.gkd.utils.useTask

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubscriptionManagePage() {
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current

    var subItems by rememberCache { mutableStateOf(listOf<SubsItem>()) }
    var shareSubItem: SubsItem? by rememberCache { mutableStateOf(null) }
    var shareQrcode: ImageBitmap? by rememberCache { mutableStateOf(null) }
    var deleteSubItem: SubsItem? by rememberCache { mutableStateOf(null) }
    var moveSubItem: SubsItem? by rememberCache { mutableStateOf(null) }

    var showAddDialog by rememberCache { mutableStateOf(false) }

    var showLinkDialog by rememberCache { mutableStateOf(false) }
    var link by rememberCache { mutableStateOf("") }

    val navigateForQrcodeResult = useNavigateForQrcodeResult()

    LaunchedEffectTry(Unit) {
        DbSet.subsItemDao.query().flowOn(IO).collect {
            subItems = it
        }
    }

    val addSubs = scope.useTask(dialog = true).launchAsFn<List<String>> { urls ->
        val safeUrls = urls.filter { url ->
            URLUtil.isNetworkUrl(url) && subItems.all { it.updateUrl != url }
        }
        if (safeUrls.isEmpty()) return@launchAsFn
        onChangeLoading(true)
        val newItems = safeUrls.mapIndexedNotNull { index, url ->
            try {
                val text = Singleton.client.get(url).bodyAsText()
                val subscriptionRaw = SubscriptionRaw.parse5(text)
                val newItem = SubsItem(
                    updateUrl = subscriptionRaw.updateUrl ?: url,
                    name = subscriptionRaw.name,
                    version = subscriptionRaw.version,
                    order = index + 1 + subItems.size
                )
                withContext(IO) {
                    newItem.subsFile.writeText(text)
                }
                newItem
            } catch (e: Exception) {
                null
            }
        }
        if (newItems.isNotEmpty()) {
            DbSet.subsItemDao.insert(*newItems.toTypedArray())
            ToastUtils.showShort("成功添加 ${newItems.size} 条订阅")
        } else {
            ToastUtils.showShort("添加失败")
        }
    }

    val updateSubs = scope.useTask(dialog = true).launchAsFn<List<SubsItem>> { oldItems ->
        if (oldItems.isEmpty()) return@launchAsFn
        onChangeLoading(true)
        val newItems = oldItems.mapNotNull { oldItem ->
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
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxHeight()
    ) {
        item(subItems) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp, 0.dp)
            ) {
                if (subItems.isEmpty()) {
                    Text(
                        text = "暂无订阅",
                    )
                } else {
                    Text(
                        text = "共有${subItems.size}条订阅,激活:${subItems.count { it.enable }},禁用:${subItems.count { !it.enable }}",
                    )
                }
                Row {
                    Image(painter = painterResource(SafeR.ic_add),
                        contentDescription = "",
                        modifier = Modifier
                            .clickable {
                                showAddDialog = true
                            }
                            .padding(4.dp)
                            .size(25.dp))
                    Image(
                        painter = painterResource(SafeR.ic_refresh),
                        contentDescription = "",
                        modifier = Modifier
                            .clickable(onClick = {
                                updateSubs(subItems)
                            })
                            .padding(4.dp)
                            .size(25.dp)
                    )
                }
            }
        }

        items(subItems.size) { i ->
            Card(
                modifier = Modifier
                    .animateItemPlacement()
                    .padding(vertical = 3.dp, horizontal = 8.dp)
                    .combinedClickable(
                        onClick = scope
                            .useTask()
                            .launchAsFn {
                                navController.navigate(SubsPageDestination(subItems[i]))
                            }, onLongClick = {
                            if (subItems.size > 1) {
                                moveSubItem = subItems[i]
                            }
                        }),
                elevation = 0.dp,
                border = BorderStroke(1.dp, Color(0xfff6f6f6)),
                shape = RoundedCornerShape(8.dp),
            ) {
                SubsItemCard(subItems[i], onShareClick = {
                    shareSubItem = subItems[i]
                }, onDelClick = {
                    deleteSubItem = subItems[i]
                }, onRefreshClick = {
                    updateSubs(listOf(subItems[i]))
                })
            }
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
                            shareSubItem = null
                            ToastUtils.showShort("复制成功")
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

    moveSubItem?.let { moveSubItemVal ->
        Dialog(onDismissRequest = { moveSubItem = null }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .width(200.dp)
                    .wrapContentHeight()
                    .background(Color.White)
                    .padding(8.dp)
            ) {
                if (subItems.firstOrNull() != moveSubItemVal) {
                    Text(
                        text = "上移",
                        modifier = Modifier
                            .clickable(
                                onClick = scope
                                    .useTask()
                                    .launchAsFn {
                                        val lastItem =
                                            subItems[subItems.indexOf(moveSubItemVal) - 1]
                                        DbSet.subsItemDao.update(
                                            lastItem.copy(
                                                order = moveSubItemVal.order
                                            ),
                                            moveSubItemVal.copy(
                                                order = lastItem.order
                                            )
                                        )
                                        moveSubItem = null
                                    })
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
                if (subItems.lastOrNull() != moveSubItemVal) {
                    Text(
                        text = "下移",
                        modifier = Modifier
                            .clickable(
                                onClick = scope
                                    .useTask()
                                    .launchAsFn {
                                        val nextItem =
                                            subItems[subItems.indexOf(moveSubItemVal) + 1]
                                        DbSet.subsItemDao.update(
                                            nextItem.copy(
                                                order = moveSubItemVal.order
                                            ),
                                            moveSubItemVal.copy(
                                                order = nextItem.order
                                            )
                                        )
                                        moveSubItem = null
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
                    text = "默认订阅", modifier = Modifier
                        .clickable(onClick = {
                            showAddDialog = false
                            addSubs(
                                listOf(
                                    "https://cdn.lisonge.com/startup_ad.json",
                                    "https://cdn.lisonge.com/internal_ad.json",
                                    "https://cdn.lisonge.com/quick_util.json",
                                )
                            )
                        })
                        .fillMaxWidth()
                        .padding(8.dp)
                )
                Text(
                    text = "二维码", modifier = Modifier
                        .clickable(onClick = scope.launchAsFn {
                            showAddDialog = false
                            val qrCode = navigateForQrcodeResult()
                            val contents = qrCode.contents
                            if (contents != null) {
                                showLinkDialog = true
                                link = contents
                            }
                        })
                        .fillMaxWidth()
                        .padding(8.dp)
                )
                Text(text = "链接", modifier = Modifier
                    .clickable {
                        showLinkDialog = true
                        showAddDialog = false
                    }
                    .fillMaxWidth()
                    .padding(8.dp))
            }
        }
    }



    LaunchedEffect(showLinkDialog) {
        if (!showLinkDialog) {
            link = ""
        }
    }
    if (showLinkDialog) {
        Dialog(onDismissRequest = { showLinkDialog = false }) {
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
                        addSubs(listOf(link))
                        showLinkDialog = false
                    }) {
                        Text(text = "添加")
                    }
                }
            }
        }
    }
}