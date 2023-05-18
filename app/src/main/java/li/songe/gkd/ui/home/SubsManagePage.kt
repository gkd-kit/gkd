package li.songe.gkd.ui.home

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
import com.blankj.utilcode.util.PathUtils
import com.blankj.utilcode.util.ToastUtils
import com.google.zxing.BarcodeFormat
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.R
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.table.SubsConfig
import li.songe.gkd.db.table.SubsItem
import li.songe.gkd.db.util.Operator.eq
import li.songe.gkd.db.util.RoomX
import li.songe.gkd.hooks.useNavigateForQrcodeResult
import li.songe.gkd.ui.SubsPage
import li.songe.gkd.ui.component.SubsItemCard
import li.songe.gkd.util.Ext.launchTry
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.ThrottleState
import li.songe.router.LocalRouter
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubscriptionManagePage() {
    val scope = rememberCoroutineScope()
    val router = LocalRouter.current

    var subItemList by remember { mutableStateOf(listOf<SubsItem>()) }
    var shareSubItem: SubsItem? by remember { mutableStateOf(null) }
    var shareQrcode: ImageBitmap? by remember { mutableStateOf(null) }
    var deleteSubItem: SubsItem? by remember { mutableStateOf(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showLinkInputDialog by remember { mutableStateOf(false) }
    val viewSubItemThrottle = ThrottleState.use(scope)
    val editSubItemThrottle = ThrottleState.use(scope)
    val refreshSubItemThrottle = ThrottleState.use(scope, 250)
    val navigateForQrcodeResult = useNavigateForQrcodeResult()

    var linkText by remember {
        mutableStateOf("")
    }

    LaunchedEffect(Unit) {
        subItemList = RoomX.select<SubsItem>().sortedBy { it.index }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxHeight()
    ) {
        item(subItemList) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp, 0.dp)
            ) {
                Text(
                    text = "共有${subItemList.size}条订阅,激活:${subItemList.count { it.enable }},禁用:${subItemList.count { !it.enable }}",
                )
                Row {
                    Image(painter = painterResource(R.drawable.ic_add),
                        contentDescription = "",
                        modifier = Modifier
                            .clickable {
                                showAddDialog = true
                            }
                            .padding(4.dp)
                            .size(25.dp))
                    Image(painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = "",
                        modifier = Modifier
                            .clickable {
                                scope.launchTry {
                                    subItemList.mapIndexed { i, oldItem ->
                                        val subscriptionRaw = SubscriptionRaw.parse5(
                                            Singleton.client
                                                .get(oldItem.updateUrl)
                                                .bodyAsText()
                                        )
                                        if (subscriptionRaw.version <= oldItem.version) {
                                            ToastUtils.showShort("暂无更新:${oldItem.name}")
                                            return@mapIndexed
                                        }
                                        val newItem = oldItem.copy(
                                            updateUrl = subscriptionRaw.updateUrl
                                                ?: oldItem.updateUrl,
                                            name = subscriptionRaw.name,
                                            mtime = System.currentTimeMillis(),
                                            version = subscriptionRaw.version
                                        )
                                        RoomX.update(newItem)
                                        File(newItem.filePath).writeText(
                                            SubscriptionRaw.stringify(
                                                subscriptionRaw
                                            )
                                        )
                                        ToastUtils.showShort("更新成功:${newItem.name}")
                                        subItemList = subItemList
                                            .toMutableList()
                                            .also {
                                                it[i] = newItem
                                            }
                                    }
                                }
                            }
                            .padding(4.dp)
                            .size(25.dp))
                }
            }
        }

        items(subItemList.size) { i ->
            Card(
                modifier = Modifier
                    .animateItemPlacement()
                    .padding(vertical = 3.dp, horizontal = 8.dp)
                    .clickable(onClick = { router.navigate(SubsPage, subItemList[i]) }),
                elevation = 0.dp,
                border = BorderStroke(1.dp, Color(0xfff6f6f6)),
                shape = RoundedCornerShape(8.dp),
            ) {
                SubsItemCard(subItemList[i], onShareClick = {
                    shareSubItem = subItemList[i]
                }, onEditClick = editSubItemThrottle.invoke {
                }, onDelClick = {
                    deleteSubItem = subItemList[i]
                }, onRefreshClick = refreshSubItemThrottle.invoke {
                    val oldItem = subItemList[i]
                    val subscriptionRaw = SubscriptionRaw.parse5(
                        Singleton.client.get(oldItem.updateUrl).bodyAsText()
                    )
                    if (subscriptionRaw.version <= oldItem.version) {
                        ToastUtils.showShort("暂无更新:${oldItem.name}")
                        return@invoke
                    }
                    val newItem = oldItem.copy(
                        updateUrl = subscriptionRaw.updateUrl
                            ?: oldItem.updateUrl,
                        name = subscriptionRaw.name,
                        mtime = System.currentTimeMillis(),
                        version = subscriptionRaw.version
                    )
                    RoomX.update(newItem)
                    withContext(IO) {
                        File(newItem.filePath).writeText(SubscriptionRaw.stringify(subscriptionRaw))
                    }
                    subItemList = subItemList.toMutableList().also {
                        it[i] = newItem
                    }
                    ToastUtils.showShort("更新成功:${newItem.name}")
                }.catch {
                    if (!it.message.isNullOrEmpty()) {
                        ToastUtils.showShort(it.message)
                    }
                })
            }
        }
    }

    shareSubItem?.let { _shareSubItem ->
        Dialog(onDismissRequest = { shareSubItem = null }) {
            Box(
                Modifier
                    .width(250.dp)
                    .background(Color.White)
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "二维码",
                        modifier = Modifier
                            .clickable {
                                shareQrcode = Singleton.barcodeEncoder
                                    .encodeBitmap(
                                        _shareSubItem.updateUrl,
                                        BarcodeFormat.QR_CODE,
                                        500,
                                        500
                                    )
                                    .asImageBitmap()
                                shareSubItem = null
                            }
                            .fillMaxWidth()
                            .padding(8.dp))
                    Text(text = "导出至剪切板",
                        modifier = Modifier
                            .clickable {
                                ClipboardUtils.copyText(_shareSubItem.updateUrl)
                                shareSubItem = null
                            }
                            .fillMaxWidth()
                            .padding(8.dp))
                }
            }
        }
    }

    shareQrcode?.let { _shareQrcode ->
        Dialog(onDismissRequest = { shareQrcode = null }) {
            Image(
                bitmap = _shareQrcode,
                contentDescription = "qrcode",
                modifier = Modifier.size(400.dp)
            )
        }
    }


    val delSubItemThrottle = ThrottleState.use(scope)
    if (deleteSubItem != null) {
        AlertDialog(onDismissRequest = { deleteSubItem = null },
            title = { Text(text = "是否删除该项") },
            confirmButton = {
                Button(onClick = delSubItemThrottle.invoke {
                    if (deleteSubItem == null) return@invoke
                    deleteSubItem?.let {
                        RoomX.delete(it)
                        RoomX.delete { SubsConfig::subsItemId eq it.id }
                    }
                    withContext(IO) {
                        try {
                            File(deleteSubItem!!.filePath).delete()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    subItemList = subItemList.toMutableList().also { it.remove(deleteSubItem) }
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
        val clickQrcodeThrottle = ThrottleState.use(scope)
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Box(
                Modifier
                    .width(250.dp)
                    .background(Color.White)
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "二维码", modifier = Modifier
                            .clickable(onClick = clickQrcodeThrottle.invoke {
                                showAddDialog = false
                                val qrCode = navigateForQrcodeResult()
                                val contents = qrCode.contents
                                if (contents != null) {
                                    showLinkInputDialog = true
                                    linkText = contents
                                }
                            })
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                    Text(text = "链接", modifier = Modifier
                        .clickable {
                            showLinkInputDialog = true
                            showAddDialog = false
                        }
                        .fillMaxWidth()
                        .padding(8.dp))
                }
            }
        }
    }
    if (showLinkInputDialog) {
        Dialog(onDismissRequest = { showLinkInputDialog = false;linkText = "" }) {
            Box(
                Modifier
                    .width(250.dp)
                    .background(Color.White)
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "请输入订阅链接")
                    TextField(
                        value = linkText,
                        onValueChange = { linkText = it },
                        singleLine = true
                    )
                    Button(onClick = {
                        showLinkInputDialog = false
                        if (subItemList.any { it.updateUrl == linkText }) {
                            ToastUtils.showShort("该链接已经添加过")
                            return@Button
                        }
                        scope.launch {
                            try {
                                val text = Singleton.client.get(linkText).bodyAsText()
                                val subscriptionRaw = SubscriptionRaw.parse5(text)
                                File(
                                    PathUtils.getExternalAppFilesPath()
                                        .plus("/subscription/")
                                ).apply {
                                    if (!exists()) {
                                        mkdir()
                                    }
                                }
                                val file = File(
                                    PathUtils.getExternalAppFilesPath()
                                        .plus("/subscription/")
                                        .plus(System.currentTimeMillis())
                                        .plus(".json")
                                )
                                withContext(IO) {
                                    file.writeText(text)
                                }
                                val tempItem = SubsItem(
                                    updateUrl = subscriptionRaw.updateUrl ?: linkText,
                                    filePath = file.absolutePath,
                                    name = subscriptionRaw.name,
                                    version = subscriptionRaw.version
                                )
                                val newItem = tempItem.copy(
                                    id = RoomX.insert(tempItem)[0]
                                )
                                subItemList = subItemList.toMutableList().apply { add(newItem) }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                ToastUtils.showShort(e.message ?: "")
                            }
                        }
                    }) {
                        Text(text = "添加")
                    }
                }
            }
        }
    }
}