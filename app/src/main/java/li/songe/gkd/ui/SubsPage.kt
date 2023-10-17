package li.songe.gkd.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.AlertDialog
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.SimpleTopAppBar
import li.songe.gkd.ui.component.SubsAppCard
import li.songe.gkd.ui.destinations.AppItemPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.formatTimeAgo
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.navigate
import li.songe.gkd.util.subsIdToRawFlow

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun SubsPage(
    subsItemId: Long,
) {
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val context = LocalContext.current

    val vm = hiltViewModel<SubsVm>()
    val subsItem by vm.subsItemFlow.collectAsState()
    val subsIdToRaw by subsIdToRawFlow.collectAsState()
    val appAndConfigs by vm.appAndConfigsFlow.collectAsState()
    val appInfoCache by appInfoCacheFlow.collectAsState()

    val subsRaw = subsIdToRaw[subsItem?.id]

    // 本地订阅
    val editable = subsItem?.id.let { it != null && it < 0 }

    var showDetailDlg by remember {
        mutableStateOf(false)
    }
    var showAddDlg by remember {
        mutableStateOf(false)
    }

    var menuAppRaw by remember {
        mutableStateOf<SubscriptionRaw.AppRaw?>(null)
    }
    var editAppRaw by remember {
        mutableStateOf<SubscriptionRaw.AppRaw?>(null)
    }


    Scaffold(
        topBar = {
            SimpleTopAppBar(onClickIcon = { navController.popBackStack() },
                title = subsRaw?.name ?: subsItem?.id.toString(),
                actions = {
                    IconButton(onClick = {
                        if (subsRaw != null) {
                            showDetailDlg = true
                        }
                    }) {
                        Icon(
                            painter = painterResource(SafeR.ic_info),
                            contentDescription = "info",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                })
        },
        floatingActionButton = {
            if (editable) {
                FloatingActionButton(onClick = { showAddDlg = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "add",
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.padding(padding)
        ) {
            itemsIndexed(appAndConfigs, { i, a -> i.toString() + a.t0.id }) { _, a ->
                val (appRaw, subsConfig, enableSize) = a
                SubsAppCard(appRaw = appRaw,
                    appInfo = appInfoCache[appRaw.id],
                    subsConfig = subsConfig,
                    enableSize = enableSize,
                    onClick = {
                        navController.navigate(AppItemPageDestination(subsItemId, appRaw.id))
                    },
                    onValueChange = scope.launchAsFn { enable ->
                        val newItem = subsConfig?.copy(
                            enable = enable
                        ) ?: SubsConfig(
                            enable = enable,
                            type = SubsConfig.AppType,
                            subsItemId = subsItemId,
                            appId = appRaw.id,
                        )
                        DbSet.subsConfigDao.insert(newItem)
                    },
                    showMenu = editable,
                    onMenuClick = {
                        menuAppRaw = appRaw
                    })
            }
            item {
                if (appAndConfigs.isEmpty()) {
                    Spacer(modifier = Modifier.height(40.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = "此订阅文件暂无规则")
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(20.dp))
            }

        }
    }

    val subsItemVal = subsItem
    if (showDetailDlg && subsRaw != null) {
        AlertDialog(onDismissRequest = { showDetailDlg = false }, title = {
            Text(text = "订阅详情")
        }, text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier
            ) {
                Text(text = "名称: " + subsRaw.name)
                Text(text = "版本: " + subsRaw.version)
                if (subsRaw.author != null) {
                    Text(text = "作者: " + subsRaw.author)
                }
                val apps = subsRaw.apps
                val groupsSize = apps.sumOf { it.groups.size }
                if (groupsSize > 0) {
                    Text(text = "规则: ${apps.size}应用/${groupsSize}规则组")
                }
                Text(text = "更新: " + formatTimeAgo(subsItem!!.mtime))
            }
        }, confirmButton = {
            if (subsRaw.supportUri != null) {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW, Uri.parse(subsRaw.supportUri)
                        )
                    )
                }) {
                    Text(text = "问题反馈")
                }
            }
        })
    }

    if (showAddDlg && subsRaw != null && subsItemVal != null) {
        var source by remember {
            mutableStateOf("")
        }
        Dialog(onDismissRequest = { showAddDlg = false }) {
            Column(
                modifier = Modifier.defaultMinSize(minWidth = 300.dp),
            ) {
                Text(text = "添加APP规则", fontSize = 18.sp, modifier = Modifier.padding(10.dp))
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    maxLines = 8,
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    placeholder = { Text(text = "请输入规则\n若APP规则已经存在则追加") },
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .padding(start = 10.dp, end = 10.dp)
                        .fillMaxWidth()
                ) {
                    TextButton(onClick = {
                        val newAppRaw = try {
                            SubscriptionRaw.parseAppRaw(source)
                        } catch (e: Exception) {
                            LogUtils.d(e)
                            ToastUtils.showShort("非法规则${e.message}")
                            return@TextButton
                        }
                        if (newAppRaw.groups.any { s -> s.name.isBlank() }) {
                            ToastUtils.showShort("不允许添加空白名规则组,请先命名")
                            return@TextButton
                        }
                        val oldAppRawIndex = subsRaw.apps.indexOfFirst { a -> a.id == newAppRaw.id }
                        val oldAppRaw = subsRaw.apps.getOrNull(oldAppRawIndex)
                        if (oldAppRaw != null) {
                            // check same group name
                            newAppRaw.groups.forEach { g ->
                                if (oldAppRaw.groups.any { g0 -> g0.name == g.name }) {
                                    ToastUtils.showShort("已经存在同名规则[${g.name}]\n请修改名称后再添加")
                                    return@TextButton
                                }
                            }
                        }
                        val newApps = if (oldAppRaw != null) {
                            val oldKey = oldAppRaw.groups.maxBy { g -> g.key }.key + 1
                            val finalAppRaw =
                                newAppRaw.copy(groups = oldAppRaw.groups + newAppRaw.groups.mapIndexed { i, g ->
                                    g.copy(
                                        key = oldKey + i
                                    )
                                })
                            subsRaw.apps.toMutableList().apply {
                                set(oldAppRawIndex, finalAppRaw)
                            }
                        } else {
                            subsRaw.apps.toMutableList().apply {
                                add(newAppRaw)
                            }
                        }
                        vm.viewModelScope.launchTry {
                            subsItemVal.subsFile.writeText(
                                SubscriptionRaw.stringify(
                                    subsRaw.copy(
                                        apps = newApps, version = subsRaw.version + 1
                                    )
                                )
                            )
                            DbSet.subsItemDao.update(subsItemVal.copy(mtime = System.currentTimeMillis()))
                            showAddDlg = false
                            ToastUtils.showShort("添加成功")
                        }
                    }, enabled = source.isNotEmpty()) {
                        Text(text = "添加")
                    }
                }
            }
        }
    }

    val editAppRawVal = editAppRaw
    if (editAppRawVal != null && subsItemVal != null && subsRaw != null) {
        var source by remember {
            mutableStateOf(Singleton.json.encodeToString(editAppRawVal))
        }
        Dialog(onDismissRequest = { editAppRaw = null }) {
            Column(
                modifier = Modifier.defaultMinSize(minWidth = 300.dp),
            ) {
                Text(text = "编辑本地APP规则", fontSize = 18.sp, modifier = Modifier.padding(10.dp))
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    maxLines = 8,
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    placeholder = { Text(text = "请输入规则") },
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .padding(start = 10.dp, end = 10.dp)
                        .fillMaxWidth()
                ) {
                    TextButton(onClick = {
                        try {
                            val newAppRaw = SubscriptionRaw.parseAppRaw(source)
                            if (newAppRaw.id != editAppRawVal.id) {
                                ToastUtils.showShort("不允许修改规则id")
                                return@TextButton
                            }
                            val oldAppRawIndex =
                                subsRaw.apps.indexOfFirst { a -> a.id == editAppRawVal.id }
                            vm.viewModelScope.launchTry {
                                subsItemVal.subsFile.writeText(
                                    SubscriptionRaw.stringify(
                                        subsRaw.copy(
                                            apps = subsRaw.apps.toMutableList().apply {
                                                set(oldAppRawIndex, newAppRaw)
                                            }, version = subsRaw.version + 1
                                        )
                                    )
                                )
                                DbSet.subsItemDao.update(subsItemVal.copy(mtime = System.currentTimeMillis()))
                                editAppRaw = null
                                ToastUtils.showShort("更新成功")
                            }
                        } catch (e: Exception) {
                            LogUtils.d(e)
                            ToastUtils.showShort("非法规则${e.message}")
                        }
                    }, enabled = source.isNotEmpty()) {
                        Text(text = "添加")
                    }
                }
            }
        }
    }


    val menuAppRawVal = menuAppRaw
    if (menuAppRawVal != null && subsItemVal != null && subsRaw != null) {
        Dialog(onDismissRequest = { menuAppRaw = null }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(10.dp)
                    .width(200.dp)
            ) {
                Text(text = "复制", modifier = Modifier
                    .clickable {
                        ClipboardUtils.copyText(
                            Singleton.json.encodeToString(
                                menuAppRawVal
                            )
                        )
                        ToastUtils.showShort("复制成功")
                        menuAppRaw = null
                    }
                    .padding(10.dp)
                    .fillMaxWidth())
                Text(text = "删除", color = Color.Red, modifier = Modifier
                    .clickable {
                        // 也许需要二次确认
                        vm.viewModelScope.launchTry(Dispatchers.IO) {
                            subsItemVal.subsFile.writeText(
                                Singleton.json.encodeToString(
                                    subsRaw.copy(apps = subsRaw.apps.filter { a -> a.id != menuAppRawVal.id })
                                )
                            )
                            DbSet.subsItemDao.update(subsItemVal.copy(mtime = System.currentTimeMillis()))
                            DbSet.subsConfigDao.delete(subsItemVal.id, menuAppRawVal.id)
                            ToastUtils.showShort("删除成功")
                        }
                        menuAppRaw = null
                    }
                    .padding(10.dp)
                    .fillMaxWidth())
            }
        }
    }
}