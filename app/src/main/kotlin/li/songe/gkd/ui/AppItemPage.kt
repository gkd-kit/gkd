package li.songe.gkd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
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
import li.songe.gkd.ui.destinations.GroupItemPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.encodeToJson5String
import li.songe.gkd.util.getGroupRawEnable
import li.songe.gkd.util.json
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.navigate

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun AppItemPage(
    subsItemId: Long,
    appId: String,
    focusGroupKey: Int? = null, // 背景/边框高亮一下
) {
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val vm = hiltViewModel<AppItemVm>()
    val subsItem by vm.subsItemFlow.collectAsState()
    val subsRaw = vm.subsRawFlow.collectAsState().value
    val subsConfigs by vm.subsConfigsFlow.collectAsState()
    val categoryConfigs by vm.categoryConfigsFlow.collectAsState()
    val appRaw by vm.subsAppFlow.collectAsState()
    val appInfoCache by appInfoCacheFlow.collectAsState()

    val appRawVal = appRaw
    val subsItemVal = subsItem
    val groupToCategoryMap = subsRaw?.groupToCategoryMap ?: emptyMap()

    val (showGroupItem, setShowGroupItem) = remember {
        mutableStateOf<SubscriptionRaw.GroupRaw?>(
            null
        )
    }

    val editable = subsItem != null && subsItemId < 0

    var showAddDlg by remember { mutableStateOf(false) }

    val (menuGroupRaw, setMenuGroupRaw) = remember {
        mutableStateOf<SubscriptionRaw.GroupRaw?>(null)
    }
    val (editGroupRaw, setEditGroupRaw) = remember {
        mutableStateOf<SubscriptionRaw.GroupRaw?>(null)
    }

    Scaffold(topBar = {
        TopAppBar(navigationIcon = {
            IconButton(onClick = {
                navController.popBackStack()
            }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                )
            }
        }, title = {
            val text = if (subsRaw == null) {
                "订阅文件缺失"
            } else {
                "${subsRaw.name}/${appInfoCache[appRaw?.id]?.name ?: appRaw?.name ?: appRaw?.id}"
            }
            Text(text = text)
        }, actions = {})
    }, floatingActionButton = {
        if (editable) {
            FloatingActionButton(onClick = { showAddDlg = true }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "add",
                )
            }
        }
    }, content = { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
            }
            appRaw?.groups?.let { groupsVal ->
                itemsIndexed(groupsVal, { i, g -> i.toString() + g.key }) { _, group ->
                    Row(
                        modifier = Modifier
                            .background(
                                if (group.key == focusGroupKey) MaterialTheme.colorScheme.inversePrimary else Color.Transparent
                            )
                            .clickable { setShowGroupItem(group) }
                            .padding(10.dp, 6.dp)
                            .fillMaxWidth()
                            .height(45.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = group.name,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (group.valid) {
                                Text(
                                    text = group.desc ?: "-",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontSize = 14.sp
                                )
                            } else {
                                Text(
                                    text = "规则组损坏",
                                    modifier = Modifier.fillMaxWidth(),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))

                        if (editable) {
                            IconButton(onClick = {
                                setMenuGroupRaw(group)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "more",
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        val groupEnable = getGroupRawEnable(
                            group,
                            subsConfigs,
                            groupToCategoryMap[group],
                            categoryConfigs
                        )
                        val subsConfig = subsConfigs.find { it.groupKey == group.key }
                        Switch(checked = groupEnable, modifier = Modifier,
                            onCheckedChange = scope.launchAsFn { enable ->
                                val newItem = (subsConfig?.copy(enable = enable) ?: SubsConfig(
                                    type = SubsConfig.GroupType,
                                    subsItemId = subsItemId,
                                    appId = appId,
                                    groupKey = group.key,
                                    enable = enable
                                ))
                                DbSet.subsConfigDao.insert(newItem)
                            })
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    })


    showGroupItem?.let { showGroupItemVal ->
        AlertDialog(modifier = Modifier.defaultMinSize(300.dp),
            onDismissRequest = { setShowGroupItem(null) },
            title = {
                Text(text = showGroupItemVal.name)
            },
            text = {
                Column {
                    if (showGroupItemVal.enable == false) {
                        Text(text = "该规则组默认不启用")
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Text(text = showGroupItemVal.desc ?: "-")
                }
            },
            confirmButton = {
                Row {
                    if (showGroupItemVal.allExampleUrls.isNotEmpty()) {
                        TextButton(onClick = {
                            setShowGroupItem(null)
                            navController.navigate(
                                GroupItemPageDestination(
                                    subsInt = subsItemId,
                                    appId = appId,
                                    groupKey = showGroupItemVal.key
                                )
                            )
                        }) {
                            Text(text = "查看图片")
                        }
                    }
                    TextButton(onClick = {
                        val groupAppText = json.encodeToJson5String(
                            appRaw?.copy(
                                groups = listOf(showGroupItemVal)
                            )
                        )
                        ClipboardUtils.copyText(groupAppText)
                        ToastUtils.showShort("复制成功")
                    }) {
                        Text(text = "复制规则组")
                    }
                }
            })
    }

    if (menuGroupRaw != null && appRawVal != null && subsItemVal != null) {
        Dialog(onDismissRequest = { setMenuGroupRaw(null) }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column {
                    Text(text = "编辑", modifier = Modifier
                        .clickable {
                            setEditGroupRaw(menuGroupRaw)
                            setMenuGroupRaw(null)
                        }
                        .padding(16.dp)
                        .fillMaxWidth())
                    Text(text = "删除", modifier = Modifier
                        .clickable {
                            vm.viewModelScope.launchTry(Dispatchers.IO) {
                                subsRaw ?: return@launchTry
                                val newSubsRaw = subsRaw.copy(
                                    apps = subsRaw.apps
                                        .toMutableList()
                                        .apply {
                                            set(
                                                indexOfFirst { a -> a.id == appRawVal.id },
                                                appRawVal.copy(groups = appRawVal.groups.filter { g -> g.key != menuGroupRaw.key })
                                            )
                                        })
                                subsItemVal.subsFile.writeText(
                                    json.encodeToString(
                                        newSubsRaw
                                    )
                                )
                                DbSet.subsItemDao.update(subsItemVal.copy(mtime = System.currentTimeMillis()))
                                DbSet.subsConfigDao.delete(
                                    subsItemVal.id, appRawVal.id, menuGroupRaw.key
                                )
                                ToastUtils.showShort("删除成功")
                                setMenuGroupRaw(null)
                            }
                        }
                        .padding(16.dp)
                        .fillMaxWidth(), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (editGroupRaw != null && appRawVal != null && subsItemVal != null) {
        var source by remember {
            mutableStateOf(json.encodeToJson5String(editGroupRaw))
        }
        val oldSource = remember { source }
        AlertDialog(
            title = { Text(text = "编辑规则组") },
            text = {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = "请输入规则组") },
                    maxLines = 8,
                )
            },
            onDismissRequest = { setEditGroupRaw(null) },
            dismissButton = {
                TextButton(onClick = { setEditGroupRaw(null) }) {
                    Text(text = "取消")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (oldSource == source) {
                        ToastUtils.showShort("规则无变动")
                        return@TextButton
                    }
                    val newGroupRaw = try {
                        SubscriptionRaw.parseGroupRaw(source)
                    } catch (e: Exception) {
                        LogUtils.d(e)
                        ToastUtils.showShort("非法规则:${e.message}")
                        return@TextButton
                    }
                    if (newGroupRaw.key != editGroupRaw.key) {
                        ToastUtils.showShort("不能更改规则组的key")
                        return@TextButton
                    }
                    if (!newGroupRaw.valid) {
                        ToastUtils.showShort("非法规则:存在非法选择器")
                        return@TextButton
                    }
                    setEditGroupRaw(null)
                    subsRaw ?: return@TextButton
                    val newSubsRaw = subsRaw.copy(apps = subsRaw.apps.toMutableList().apply {
                        set(
                            indexOfFirst { a -> a.id == appRawVal.id },
                            appRawVal.copy(groups = appRawVal.groups.toMutableList().apply {
                                set(
                                    indexOfFirst { g -> g.key == newGroupRaw.key }, newGroupRaw
                                )
                            })
                        )
                    })
                    vm.viewModelScope.launchTry(Dispatchers.IO) {
                        subsItemVal.subsFile.writeText(
                            json.encodeToString(
                                newSubsRaw
                            )
                        )
                        DbSet.subsItemDao.update(subsItemVal.copy(mtime = System.currentTimeMillis()))
                        ToastUtils.showShort("更新成功")
                    }
                }, enabled = source.isNotEmpty()) {
                    Text(text = "更新")
                }
            },
        )
    }

    if (showAddDlg && appRawVal != null && subsItemVal != null) {
        var source by remember {
            mutableStateOf("")
        }
        AlertDialog(title = { Text(text = "添加规则组") }, text = {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = "请输入规则组\n可以是APP规则\n也可以是单个规则组") },
                maxLines = 8,
            )
        }, onDismissRequest = { showAddDlg = false }, confirmButton = {
            TextButton(onClick = {
                val newAppRaw = try {
                    SubscriptionRaw.parseAppRaw(source)
                } catch (_: Exception) {
                    null
                }
                val tempGroups = if (newAppRaw == null) {
                    val newGroupRaw = try {
                        SubscriptionRaw.parseGroupRaw(source)
                    } catch (e: Exception) {
                        LogUtils.d(e)
                        ToastUtils.showShort("非法规则:${e.message}")
                        return@TextButton
                    }
                    listOf(newGroupRaw)
                } else {
                    if (newAppRaw.id != appRawVal.id) {
                        ToastUtils.showShort("id不一致,无法添加")
                        return@TextButton
                    }
                    if (newAppRaw.groups.isEmpty()) {
                        ToastUtils.showShort("不能添加空规则组")
                        return@TextButton
                    }
                    newAppRaw.groups
                }
                if (!tempGroups.all { g -> g.valid }) {
                    ToastUtils.showShort("非法规则:存在非法选择器")
                    return@TextButton
                }
                tempGroups.forEach { g ->
                    if (appRawVal.groups.any { g2 -> g2.name == g.name }) {
                        ToastUtils.showShort("存在同名规则[${g.name}]")
                        return@TextButton
                    }
                }
                val newKey = appRawVal.groups.maxBy { g -> g.key }.key + 1
                subsRaw ?: return@TextButton
                val newSubsRaw = subsRaw.copy(apps = subsRaw.apps.toMutableList().apply {
                    set(
                        indexOfFirst { a -> a.id == appRawVal.id },
                        appRawVal.copy(groups = appRawVal.groups + tempGroups.mapIndexed { i, g ->
                            g.copy(
                                key = newKey + i
                            )
                        })
                    )
                })
                vm.viewModelScope.launchTry(Dispatchers.IO) {
                    subsItemVal.subsFile.writeText(json.encodeToString(newSubsRaw))
                    DbSet.subsItemDao.update(subsItemVal.copy(mtime = System.currentTimeMillis()))
                    showAddDlg = false
                    ToastUtils.showShort("添加成功")
                }
            }, enabled = source.isNotEmpty()) {
                Text(text = "添加")
            }
        }, dismissButton = {
            TextButton(onClick = { showAddDlg = false }) {
                Text(text = "取消")
            }
        })
    }
}

