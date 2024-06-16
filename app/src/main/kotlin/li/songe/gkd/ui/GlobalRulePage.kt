package li.songe.gkd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.LogUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.coroutines.Dispatchers
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.TowLineText
import li.songe.gkd.ui.component.getDialogResult
import li.songe.gkd.ui.destinations.GlobalRuleExcludePageDestination
import li.songe.gkd.ui.destinations.GroupImagePageDestination
import li.songe.gkd.ui.style.itemPadding
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.encodeToJson5String
import li.songe.gkd.util.json
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.navigate
import li.songe.gkd.util.toast
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
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                IconButton(onClick = {
                    navController.popBackStack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
            }, title = {
                TowLineText(
                    title = rawSubs?.name ?: subsItemId.toString(),
                    subTitle = "全局规则"
                )
            })
        },
        floatingActionButton = {
            if (editable) {
                FloatingActionButton(onClick = { showAddDlg = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "add",
                    )
                }
            }
        },
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            items(globalGroups, { g -> g.key }) { group ->
                Row(
                    modifier = Modifier
                        .background(
                            if (group.key == focusGroupKey) MaterialTheme.colorScheme.inversePrimary else Color.Transparent
                        )
                        .clickable { setShowGroupItem(group) }
                        .itemPadding(),
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
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (group.valid) {
                            if (!group.desc.isNullOrBlank()) {
                                Text(
                                    text = group.desc,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text = "暂无描述",
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            Text(
                                text = "非法选择器",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))

                    var expanded by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        IconButton(onClick = {
                            expanded = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null,
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(text = "复制")
                                },
                                onClick = {
                                    expanded = false
                                    val groupAppText = json.encodeToJson5String(group)
                                    ClipboardUtils.copyText(groupAppText)
                                    toast("复制成功")
                                }
                            )
                            if (editable) {
                                DropdownMenuItem(
                                    text = {
                                        Text(text = "编辑")
                                    },
                                    onClick = {
                                        expanded = false
                                        setEditGroupRaw(group)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(text = "编辑禁用")
                                },
                                onClick = {
                                    expanded = false
                                    navController.navigate(
                                        GlobalRuleExcludePageDestination(
                                            subsItemId,
                                            group.key
                                        )
                                    )
                                }
                            )
                            if (editable) {
                                DropdownMenuItem(
                                    text = {
                                        Text(text = "删除", color = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = {
                                        expanded = false
                                        vm.viewModelScope.launchTry {
                                            if (!getDialogResult(
                                                    "删除规则组",
                                                    "是否删除 ${group.name} ?"
                                                )
                                            ) {
                                                return@launchTry
                                            }
                                            updateSubscription(
                                                rawSubs!!.copy(
                                                    globalGroups = rawSubs.globalGroups.filter { g -> g.key != group.key }
                                                )
                                            )
                                            val subsConfig =
                                                subsConfigs.find { it.groupKey == group.key }
                                            if (subsConfig != null) {
                                                DbSet.subsConfigDao.delete(subsConfig)
                                            }
                                            DbSet.subsItemDao.updateMtime(rawSubs.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))

                    val groupEnable = subsConfigs.find { c -> c.groupKey == group.key }?.enable
                        ?: group.enable ?: true
                    val subsConfig = subsConfigs.find { it.groupKey == group.key }
                    Switch(
                        checked = groupEnable, modifier = Modifier,
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
                } else if (editable) {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }

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
                    toast("非法规则\n${e.message ?: e}")
                    return@TextButton
                }
                if (newGroup.errorDesc != null) {
                    toast(newGroup.errorDesc!!)
                    return@TextButton
                }
                if (rawSubs.globalGroups.any { g -> g.name == newGroup.name }) {
                    toast("存在同名规则[${newGroup.name}]")
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
                    toast("添加成功")
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

                if (editable) {
                    Text(text = "删除规则组", modifier = Modifier
                        .clickable {
                            setMenuGroupRaw(null)

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
        val focusRequester = remember { FocusRequester() }
        val oldSource = remember { source }
        AlertDialog(
            title = { Text(text = "编辑规则组") },
            text = {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text(text = "请输入规则组") },
                    maxLines = 10,
                )
                LaunchedEffect(null) {
                    focusRequester.requestFocus()
                }
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
                        setEditGroupRaw(null)
                        toast("规则无变动")
                        return@TextButton
                    }
                    val newGroupRaw = try {
                        RawSubscription.parseRawGlobalGroup(source)
                    } catch (e: Exception) {
                        LogUtils.d(e)
                        toast("非法规则:${e.message}")
                        return@TextButton
                    }
                    if (newGroupRaw.key != editGroupRaw.key) {
                        toast("不能更改规则组的key")
                        return@TextButton
                    }
                    if (newGroupRaw.errorDesc != null) {
                        toast(newGroupRaw.errorDesc!!)
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
                        toast("更新成功")
                    }
                }, enabled = source.isNotEmpty()) {
                    Text(text = "更新")
                }
            },
        )
    }

    if (showGroupItem != null) {
        AlertDialog(
            modifier = Modifier.defaultMinSize(300.dp),
            onDismissRequest = { setShowGroupItem(null) },
            title = {
                Text(text = "规则组详情")
            },
            text = {
                Column {
                    Text(text = showGroupItem.name)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = showGroupItem.desc ?: "")
                }
            },
            confirmButton = {
                if (showGroupItem.allExampleUrls.isNotEmpty()) {
                    TextButton(onClick = {
                        setShowGroupItem(null)
                        navController.navigate(
                            GroupImagePageDestination(
                                subsInt = subsItemId,
                                groupKey = showGroupItem.key
                            )
                        )
                    }) {
                        Text(text = "查看图片")
                    }
                }
            })
    }
}