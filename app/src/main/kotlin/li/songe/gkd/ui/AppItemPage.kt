package li.songe.gkd.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.LogUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ImagePreviewPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import li.songe.gkd.MainActivity
import li.songe.gkd.data.ExcludeData
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.stringify
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.TowLineText
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.itemPadding
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.getGroupRawEnable
import li.songe.gkd.util.json
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubscription
import li.songe.json5.Json5
import li.songe.json5.encodeToJson5String

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AppItemPage(
    subsItemId: Long,
    appId: String,
    focusGroupKey: Int? = null, // 背景/边框高亮一下
) {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val vm = viewModel<AppItemVm>()
    val subsItem = vm.subsItemFlow.collectAsState().value
    val subsRaw = vm.subsRawFlow.collectAsState().value
    val subsConfigs by vm.subsConfigsFlow.collectAsState()
    val categoryConfigs by vm.categoryConfigsFlow.collectAsState()
    val appRaw by vm.subsAppFlow.collectAsState()
    val appInfoCache by appInfoCacheFlow.collectAsState()

    val groupToCategoryMap = subsRaw?.groupToCategoryMap ?: emptyMap()

    val (showGroupItem, setShowGroupItem) = remember {
        mutableStateOf<RawSubscription.RawAppGroup?>(
            null
        )
    }

    val editable = subsItem != null && subsItemId < 0

    var showAddDlg by remember { mutableStateOf(false) }

    val (editGroupRaw, setEditGroupRaw) = remember {
        mutableStateOf<RawSubscription.RawAppGroup?>(null)
    }
    val (excludeGroupRaw, setExcludeGroupRaw) = remember {
        mutableStateOf<RawSubscription.RawAppGroup?>(null)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
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
                title = subsRaw?.name ?: subsItemId.toString(),
                subTitle = appInfoCache[appId]?.name ?: appRaw.name ?: appId
            )
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
    }) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
            }
            itemsIndexed(appRaw.groups, { i, g -> i.toString() + g.key }) { _, group ->
                val subsConfig = subsConfigs.find { it.groupKey == group.key }
                val groupEnable = getGroupRawEnable(
                    group,
                    subsConfig,
                    groupToCategoryMap[group],
                    categoryConfigs.find { c -> c.categoryKey == groupToCategoryMap[group]?.key }
                )

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
                                contentDescription = "more",
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
                                    val groupAppText = json.encodeToJson5String(
                                        appRaw.copy(
                                            groups = listOf(group)
                                        )
                                    )
                                    ClipboardUtils.copyText(groupAppText)
                                    toast("复制成功")
                                    expanded = false
                                },
                            )
                            if (editable) {
                                DropdownMenuItem(
                                    text = {
                                        Text(text = "编辑")
                                    },
                                    onClick = {
                                        setEditGroupRaw(group)
                                        expanded = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(text = "编辑禁用")
                                },
                                onClick = {
                                    setExcludeGroupRaw(group)
                                    expanded = false
                                },
                            )
                            if (subsConfig?.enable != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(text = "重置开关")
                                    },
                                    onClick = {
                                        expanded = false
                                        vm.viewModelScope.launchTry(Dispatchers.IO) {
                                            DbSet.subsConfigDao.insert(subsConfig.copy(enable = null))
                                        }
                                    },
                                )
                            }
                            if (editable && subsRaw != null && subsItem != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(text = "删除", color = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = {
                                        expanded = false
                                        vm.viewModelScope.launchTry {
                                            context.mainVm.dialogFlow.waitResult(
                                                title = "删除规则组",
                                                text = "确定删除规则组 ${group.name} ?",
                                                error = true,
                                            )
                                            val newSubsRaw = subsRaw.copy(
                                                apps = subsRaw.apps
                                                    .toMutableList()
                                                    .apply {
                                                        set(
                                                            indexOfFirst { a -> a.id == appRaw.id },
                                                            appRaw.copy(
                                                                groups = appRaw.groups
                                                                    .filter { g -> g.key != group.key }
                                                            )
                                                        )
                                                    }
                                            )
                                            updateSubscription(newSubsRaw)
                                            DbSet.subsItemDao.update(subsItem.copy(mtime = System.currentTimeMillis()))
                                            DbSet.subsConfigDao.delete(
                                                subsItem.id, appRaw.id, group.key
                                            )
                                            toast("删除成功")
                                        }
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))
                    Switch(
                        checked = groupEnable, modifier = Modifier,
                        onCheckedChange = vm.viewModelScope.launchAsFn { enable ->
                            val newItem = (subsConfig?.copy(enable = enable) ?: SubsConfig(
                                type = SubsConfig.AppGroupType,
                                subsItemId = subsItemId,
                                appId = appId,
                                groupKey = group.key,
                                enable = enable
                            ))
                            DbSet.subsConfigDao.insert(newItem)
                        })
                }
            }

            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (appRaw.groups.isEmpty()) {
                    EmptyText(text = "暂无规则")
                } else if (editable) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }

    showGroupItem?.let { showGroupItemVal ->
        AlertDialog(
            onDismissRequest = { setShowGroupItem(null) },
            title = {
                Text(text = "规则组详情")
            },
            text = {
                Column {
                    Text(text = showGroupItemVal.name)
                    if (showGroupItemVal.desc != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = showGroupItemVal.desc)
                    }
                }
            },
            confirmButton = {
                if (showGroupItemVal.allExampleUrls.isNotEmpty()) {
                    TextButton(onClick = throttle {
                        setShowGroupItem(null)
                        navController.toDestinationsNavigator().navigate(
                            ImagePreviewPageDestination(
                                title = showGroupItemVal.name,
                                uris = showGroupItemVal.allExampleUrls.toTypedArray()
                            )
                        )
                    }) {
                        Text(text = "查看图片")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = throttle {
                    setShowGroupItem(null)
                }) {
                    Text(text = "关闭")
                }
            }
        )
    }

    if (editGroupRaw != null && subsItem != null) {
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
            onDismissRequest = {
                if (source.isEmpty()) {
                    setEditGroupRaw(null)
                }
            },
            dismissButton = {
                TextButton(onClick = { setEditGroupRaw(null) }) {
                    Text(text = "取消")
                }
            },
            confirmButton = {
                TextButton(onClick = vm.viewModelScope.launchAsFn(Dispatchers.Default) {
                    if (oldSource == source) {
                        toast("规则组无变动")
                        setEditGroupRaw(null)
                        return@launchAsFn
                    }

                    val element = try {
                        Json5.parseToJson5Element(source).jsonObject
                    } catch (e: Exception) {
                        LogUtils.d(e)
                        error("非法JSON:${e.message}")
                    }
                    val newGroupRaw = try {
                        if (element["groups"] is JsonArray) {
                            RawSubscription.parseApp(element).groups.let {
                                it.find { g -> g.key == editGroupRaw.key } ?: it.firstOrNull()
                            }
                        } else {
                            null
                        } ?: RawSubscription.parseGroup(element)
                    } catch (e: Exception) {
                        LogUtils.d(e)
                        error("非法规则:${e.message}")
                    }
                    if (newGroupRaw.key != editGroupRaw.key) {
                        toast("不能更改规则组的key")
                        return@launchAsFn
                    }
                    if (newGroupRaw.errorDesc != null) {
                        toast(newGroupRaw.errorDesc!!)
                        return@launchAsFn
                    }
                    setEditGroupRaw(null)
                    subsRaw ?: return@launchAsFn
                    val newSubsRaw = subsRaw.copy(apps = subsRaw.apps.toMutableList().apply {
                        set(
                            indexOfFirst { a -> a.id == appRaw.id },
                            appRaw.copy(groups = appRaw.groups.toMutableList().apply {
                                set(
                                    indexOfFirst { g -> g.key == newGroupRaw.key }, newGroupRaw
                                )
                            })
                        )
                    })
                    updateSubscription(newSubsRaw)
                    DbSet.subsItemDao.update(subsItem.copy(mtime = System.currentTimeMillis()))
                    toast("更新成功")
                }, enabled = source.isNotEmpty()) {
                    Text(text = "更新")
                }
            },
        )
    }

    if (excludeGroupRaw != null && subsItem != null) {
        var source by remember {
            mutableStateOf(
                ExcludeData.parse(subsConfigs.find { s -> s.groupKey == excludeGroupRaw.key }?.exclude)
                    .stringify(appId)
            )
        }
        val oldSource = remember { source }
        val focusRequester = remember { FocusRequester() }
        AlertDialog(
            title = { Text(text = "编辑禁用") },
            text = {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            text = "请填入需要禁用的 activityId\n以换行或英文逗号分割",
                            style = LocalTextStyle.current.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        )
                    },
                    maxLines = 10,
                    textStyle = LocalTextStyle.current.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize)
                )
                LaunchedEffect(null) {
                    focusRequester.requestFocus()
                }
            },
            onDismissRequest = {
                if (source.isEmpty()) {
                    setExcludeGroupRaw(null)
                }
            },
            dismissButton = {
                TextButton(onClick = { setExcludeGroupRaw(null) }) {
                    Text(text = "取消")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (oldSource == source) {
                        toast("禁用项无变动")
                        setExcludeGroupRaw(null)
                        return@TextButton
                    }
                    setExcludeGroupRaw(null)
                    val newSubsConfig =
                        (subsConfigs.find { s -> s.groupKey == excludeGroupRaw.key } ?: SubsConfig(
                            type = SubsConfig.AppGroupType,
                            subsItemId = subsItemId,
                            appId = appId,
                            groupKey = excludeGroupRaw.key,
                        )).copy(exclude = ExcludeData.parse(appId, source).stringify())
                    vm.viewModelScope.launchTry(Dispatchers.IO) {
                        DbSet.subsConfigDao.insert(newSubsConfig)
                        toast("更新成功")
                    }
                }) {
                    Text(text = "更新")
                }
            },
        )
    }

    if (showAddDlg && subsItem != null) {
        var source by remember {
            mutableStateOf("")
        }
        AlertDialog(title = { Text(text = "添加规则组") }, text = {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = "请输入规则组\n可以是APP规则\n也可以是单个规则组") },
                maxLines = 10,
            )
        }, onDismissRequest = {
            if (source.isEmpty()) {
                showAddDlg = false
            }
        }, confirmButton = {
            TextButton(onClick = {
                val newAppRaw = try {
                    RawSubscription.parseRawApp(source)
                } catch (_: Exception) {
                    null
                }
                val tempGroups = if (newAppRaw == null) {
                    val newGroupRaw = try {
                        RawSubscription.parseRawGroup(source)
                    } catch (e: Exception) {
                        LogUtils.d(e)
                        toast("非法规则:${e.message}")
                        return@TextButton
                    }
                    listOf(newGroupRaw)
                } else {
                    if (newAppRaw.id != appRaw.id) {
                        toast("id不一致,无法添加")
                        return@TextButton
                    }
                    if (newAppRaw.groups.isEmpty()) {
                        toast("不能添加空规则组")
                        return@TextButton
                    }
                    newAppRaw.groups
                }
                tempGroups.find { g -> g.errorDesc != null }?.errorDesc?.let { errorDesc ->
                    toast(errorDesc)
                    return@TextButton
                }
                tempGroups.forEach { g ->
                    if (appRaw.groups.any { g2 -> g2.name == g.name }) {
                        toast("存在同名规则[${g.name}]")
                        return@TextButton
                    }
                }
                val newKey = (appRaw.groups.maxByOrNull { g -> g.key }?.key ?: -1) + 1
                subsRaw ?: return@TextButton
                val newSubsRaw = subsRaw.copy(apps = subsRaw.apps.toMutableList().apply {
                    val newApp =
                        appRaw.copy(groups = (appRaw.groups + tempGroups.mapIndexed { i, g ->
                            g.copy(
                                key = newKey + i
                            )
                        }))
                    val i = indexOfFirst { a -> a.id == appRaw.id }
                    if (i < 0) {
                        add(newApp)
                    } else {
                        set(i, newApp)
                    }
                })
                vm.viewModelScope.launchTry(Dispatchers.IO) {
                    DbSet.subsItemDao.update(subsItem.copy(mtime = System.currentTimeMillis()))
                    updateSubscription(newSubsRaw)
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
}

