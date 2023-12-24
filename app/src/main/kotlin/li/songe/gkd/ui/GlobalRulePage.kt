package li.songe.gkd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
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
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.getDialogResult
import li.songe.gkd.ui.destinations.GroupItemPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.encodeToJson5String
import li.songe.gkd.util.json
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.navigate
import li.songe.gkd.util.updateSubscription

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun GlobalRulePage(subsItemId: Long, focusGroupKey: Int? = null) {
    val navController = LocalNavController.current
    val vm = hiltViewModel<GlobalRuleVm>()
    val subsItem by vm.subsItemFlow.collectAsState()
    val rawSubs = vm.subsRawFlow.collectAsState().value
    val subsConfigs by vm.subsConfigsFlow.collectAsState()

    val editable = subsItemId < 0 && rawSubs != null && subsItem != null
    val globalGroups = rawSubs?.globalGroups ?: emptyList()

    var showAddDlg by remember { mutableStateOf(false) }
    val (menuGroupRaw, setMenuGroupRaw) = remember {
        mutableStateOf<RawSubscription.RawGlobalGroup?>(null)
    }
    val (editGroupRaw, setEditGroupRaw) = remember {
        mutableStateOf<RawSubscription.RawGlobalGroup?>(null)
    }
    val (showGroupItem, setShowGroupItem) = remember {
        mutableStateOf<RawSubscription.RawGlobalGroup?>(
            null
        )
    }

    Scaffold(
        topBar = {
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
                Text(text = "${rawSubs?.name ?: subsItemId}/全局规则")
            })
        }, floatingActionButton = {
            if (editable) {
                FloatingActionButton(onClick = { showAddDlg = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "add",
                    )
                }
            }
        },
        content = { paddingValues ->
            LazyColumn(modifier = Modifier.padding(paddingValues)) {
                items(globalGroups, { g -> g.key }) { group ->
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
                                    text = group.desc ?: "",
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
                                    contentDescription = null,
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        val groupEnable = subsConfigs.find { c -> c.groupKey == group.key }?.enable
                            ?: group.enable ?: true
                        val subsConfig = subsConfigs.find { it.groupKey == group.key }
                        Switch(checked = groupEnable, modifier = Modifier,
                            onCheckedChange = vm.viewModelScope.launchAsFn { enable ->
                                val newItem = (subsConfig?.copy(enable = enable) ?: SubsConfig(
                                    type = SubsConfig.GlobalGroupType,
                                    subsItemId = subsItemId,
                                    groupKey = group.key,
                                    enable = enable
                                ))
                                DbSet.subsConfigDao.insert(newItem)
                            }
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(40.dp))
                    if (globalGroups.isEmpty()) {
                        Text(
                            text = "暂无规则",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    )

    if (showAddDlg && rawSubs != null) {
        var source by remember {
            mutableStateOf("")
        }
        AlertDialog(title = { Text(text = "添加全局规则组") }, text = {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = "请输入规则组") },
                maxLines = 10,
            )
        }, onDismissRequest = { showAddDlg = false }, confirmButton = {
            TextButton(onClick = {
                val newGroup = try {
                    RawSubscription.parseRawGlobalGroup(source)
                } catch (e: Exception) {
                    ToastUtils.showShort("非法规则\n${e.message ?: e}")
                    return@TextButton
                }
                if (!newGroup.valid) {
                    ToastUtils.showShort("非法规则:存在非法选择器")
                    return@TextButton
                }
                if (rawSubs.globalGroups.any { g -> g.name == newGroup.name }) {
                    ToastUtils.showShort("存在同名规则[${newGroup.name}]")
                    return@TextButton
                }
                val newKey = (rawSubs.globalGroups.maxByOrNull { g -> g.key }?.key ?: -1) + 1
                val newRawSubs = rawSubs.copy(
                    globalGroups = rawSubs.globalGroups.toMutableList()
                        .apply { add(newGroup.copy(key = newKey)) }
                )
                updateSubscription(newRawSubs)
                vm.viewModelScope.launchTry(Dispatchers.IO) {
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

    if (menuGroupRaw != null && rawSubs != null) {
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
                            setMenuGroupRaw(null)
                            vm.viewModelScope.launchTry {
                                if (!getDialogResult("是否删除${menuGroupRaw.name}")) return@launchTry
                                updateSubscription(
                                    rawSubs.copy(
                                        globalGroups = rawSubs.globalGroups.filter { g -> g.key != menuGroupRaw.key }
                                    )
                                )
                                DbSet.subsItemDao.updateMtime(rawSubs.id)
                            }
                        }
                        .padding(16.dp)
                        .fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
    if (editGroupRaw != null && rawSubs != null) {
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
                    maxLines = 10,
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
                        RawSubscription.parseRawGlobalGroup(source)
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
                    val newGlobalGroups = rawSubs.globalGroups.toMutableList().apply {
                        val i = rawSubs.globalGroups.indexOfFirst { g -> g.key == newGroupRaw.key }
                        if (i >= 0) {
                            set(i, newGroupRaw)
                        }
                    }
                    updateSubscription(rawSubs.copy(globalGroups = newGlobalGroups))
                    vm.viewModelScope.launchTry(Dispatchers.IO) {
                        DbSet.subsItemDao.updateMtime(rawSubs.id)
                        ToastUtils.showShort("更新成功")
                    }
                }, enabled = source.isNotEmpty()) {
                    Text(text = "更新")
                }
            },
        )
    }


    if (showGroupItem != null) {
        AlertDialog(modifier = Modifier.defaultMinSize(300.dp),
            onDismissRequest = { setShowGroupItem(null) },
            title = {
                Text(text = showGroupItem.name)
            },
            text = {
                Column {
                    if (showGroupItem.enable == false) {
                        Text(text = "该规则组默认不启用")
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Text(text = showGroupItem.desc ?: "")
                }
            },
            confirmButton = {
                Row {
                    if (showGroupItem.allExampleUrls.isNotEmpty()) {
                        TextButton(onClick = {
                            setShowGroupItem(null)
                            navController.navigate(
                                GroupItemPageDestination(
                                    subsInt = subsItemId,
                                    groupKey = showGroupItem.key
                                )
                            )
                        }) {
                            Text(text = "查看图片")
                        }
                    }
                    TextButton(onClick = {
                        val groupAppText = json.encodeToJson5String(showGroupItem)
                        ClipboardUtils.copyText(groupAppText)
                        ToastUtils.showShort("复制成功")
                    }) {
                        Text(text = "复制规则组")
                    }
                }
            })
    }
}