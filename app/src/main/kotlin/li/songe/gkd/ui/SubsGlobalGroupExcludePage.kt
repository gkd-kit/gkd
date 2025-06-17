package li.songe.gkd.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.KeyboardUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.flow.update
import li.songe.gkd.data.ExcludeData
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.service.launcherAppId
import li.songe.gkd.ui.component.AnimatedIcon
import li.songe.gkd.ui.component.AppBarTextField
import li.songe.gkd.ui.component.AppIcon
import li.songe.gkd.ui.component.AppNameText
import li.songe.gkd.ui.component.CardFlagBar
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.InnerDisableSwitch
import li.songe.gkd.ui.component.QueryPkgAuthCard
import li.songe.gkd.ui.component.TowLineText
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.component.useListScrollState
import li.songe.gkd.ui.local.LocalNavController
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.ProfileTransitions
import li.songe.gkd.ui.style.itemFlagPadding
import li.songe.gkd.ui.style.menuPadding
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.LIST_PLACEHOLDER_KEY
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.SortTypeOption
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.mapHashCode
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.systemAppsFlow
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun SubsGlobalGroupExcludePage(subsItemId: Long, groupKey: Int) {
    val context = LocalActivity.current!!
    val navController = LocalNavController.current
    val vm = viewModel<SubsGlobalGroupExcludeVm>()
    val rawSubs = vm.rawSubsFlow.collectAsState().value
    val group = vm.groupFlow.collectAsState().value
    val excludeData = vm.excludeDataFlow.collectAsState().value
    val showAppInfos = vm.showAppInfosFlow.collectAsState().value
    val searchStr by vm.searchStrFlow.collectAsState()
    val showSystemApp by vm.showSystemAppFlow.collectAsState()
    val showHiddenApp by vm.showHiddenAppFlow.collectAsState()
    val showDisabledApp by vm.showDisabledAppFlow.collectAsState()
    val sortType by vm.sortTypeFlow.collectAsState()
    val disabledAppSet by vm.disabledAppSetFlow.collectAsState()

    var showEditDlg by remember {
        mutableStateOf(false)
    }
    var showSearchBar by rememberSaveable {
        mutableStateOf(false)
    }
    LaunchedEffect(key1 = showSearchBar, block = {
        if (!showSearchBar) {
            vm.searchStrFlow.value = ""
        }
    })
    val resetKey = showAppInfos.mapHashCode { it.id }
    val (scrollBehavior, listState) = useListScrollState(resetKey)
    var expanded by remember { mutableStateOf(false) }
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            IconButton(onClick = throttle {
                if (KeyboardUtils.isSoftInputVisible(context)) {
                    softwareKeyboardController?.hide()
                }
                navController.popBackStack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                )
            }
        }, title = {
            if (showSearchBar) {
                BackHandler {
                    if (KeyboardUtils.isSoftInputVisible(context)) {
                        softwareKeyboardController?.hide()
                    } else {
                        showSearchBar = false
                    }
                }
                AppBarTextField(
                    value = searchStr,
                    onValueChange = { newValue -> vm.searchStrFlow.value = newValue.trim() },
                    hint = "请输入应用名称/ID",
                    modifier = Modifier.autoFocus()
                )
            } else {
                TowLineText(
                    title = "编辑禁用",
                    subtitle = "${rawSubs?.name ?: subsItemId}/${group?.name ?: groupKey}"
                )
            }
        }, actions = {
            IconButton(onClick = {
                if (showSearchBar) {
                    if (vm.searchStrFlow.value.isEmpty()) {
                        showSearchBar = false
                    } else {
                        vm.searchStrFlow.value = ""
                    }
                } else {
                    showSearchBar = true
                }
            }) {
                AnimatedIcon(
                    id = SafeR.ic_anim_search_close,
                    atEnd = showSearchBar,
                )
            }
            IconButton(onClick = {
                expanded = true
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null
                )
            }
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart)
            ) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    Text(
                        text = "排序",
                        modifier = Modifier.menuPadding(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    SortTypeOption.allSubObject.forEach { sortOption ->
                        DropdownMenuItem(
                            text = {
                                Text(sortOption.label)
                            },
                            trailingIcon = {
                                RadioButton(
                                    selected = sortType == sortOption,
                                    onClick = {
                                        storeFlow.update { it.copy(subsExcludeSortType = sortOption.value) }
                                    }
                                )
                            },
                            onClick = {
                                storeFlow.update { it.copy(subsExcludeSortType = sortOption.value) }
                            },
                        )
                    }
                    Text(
                        text = "筛选",
                        modifier = Modifier.menuPadding(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    DropdownMenuItem(
                        text = {
                            Text("显示系统应用")
                        },
                        trailingIcon = {
                            Checkbox(
                                checked = showSystemApp,
                                onCheckedChange = {
                                    storeFlow.update { it.copy(subsExcludeShowSystemApp = !it.subsExcludeShowSystemApp) }
                                })
                        },
                        onClick = {
                            storeFlow.update { it.copy(subsExcludeShowSystemApp = !it.subsExcludeShowSystemApp) }
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text("显示隐藏应用")
                        },
                        trailingIcon = {
                            Checkbox(
                                checked = showHiddenApp,
                                onCheckedChange = {
                                    storeFlow.update { it.copy(subsExcludeShowHiddenApp = !it.subsExcludeShowHiddenApp) }
                                })
                        },
                        onClick = {
                            storeFlow.update { it.copy(subsExcludeShowHiddenApp = !it.subsExcludeShowHiddenApp) }
                        },
                    )
                    if (disabledAppSet.isNotEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text("显示禁用应用")
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = showDisabledApp,
                                    onCheckedChange = {
                                        storeFlow.update { it.copy(subsExcludeShowDisabledApp = !it.subsExcludeShowDisabledApp) }
                                    })
                            },
                            onClick = {
                                storeFlow.update { it.copy(subsExcludeShowDisabledApp = !it.subsExcludeShowDisabledApp) }
                            },
                        )
                    }
                }
            }

        })
    }, content = { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState
        ) {
            items(showAppInfos, { it.id }) { appInfo ->
                Row(
                    modifier = Modifier
                        .itemFlagPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(appInfo = appInfo)
                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        AppNameText(appInfo = appInfo)
                        Text(
                            text = appInfo.id,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (group != null) {
                        val checked = getGlobalGroupChecked(
                            rawSubs!!,
                            excludeData,
                            group,
                            appInfo.id
                        )
                        if (checked != null) {
                            key(appInfo.id) {
                                Switch(
                                    checked = checked,
                                    onCheckedChange = throttle(vm.viewModelScope.launchAsFn<Boolean> { newChecked ->
                                        val subsConfig = (vm.subsConfigFlow.value ?: SubsConfig(
                                            type = SubsConfig.GlobalGroupType,
                                            subsId = subsItemId,
                                            groupKey = groupKey,
                                        )).copy(
                                            exclude = excludeData.copy(
                                                appIds = excludeData.appIds.toMutableMap().apply {
                                                    set(appInfo.id, !newChecked)
                                                })
                                                .stringify()
                                        )
                                        DbSet.subsConfigDao.insert(subsConfig)
                                    }),
                                )
                            }
                        } else {
                            InnerDisableSwitch()
                        }
                    }
                    CardFlagBar(visible = excludeData.appIds.containsKey(appInfo.id))
                }
            }
            item(LIST_PLACEHOLDER_KEY) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (showAppInfos.isEmpty() && searchStr.isNotEmpty()) {
                    val hasShowAll = showSystemApp && showHiddenApp
                    EmptyText(text = if (hasShowAll) "暂无搜索结果" else "暂无搜索结果,请尝试修改筛选条件")
                }
                QueryPkgAuthCard()
            }
        }
    })

    if (group != null && showEditDlg) {
        var source by remember {
            mutableStateOf(
                excludeData.stringify()
            )
        }
        val oldSource = remember { source }
        AlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(text = "编辑禁用") },
            text = {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    modifier = Modifier.fillMaxWidth().autoFocus(),
                    placeholder = {
                        Text(
                            text = tipText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    minLines = 8,
                    maxLines = 12,
                    textStyle = MaterialTheme.typography.bodySmall
                )
            },
            onDismissRequest = {
                showEditDlg = false
            },
            dismissButton = {
                TextButton(onClick = { showEditDlg = false }) {
                    Text(text = "取消")
                }
            },
            confirmButton = {
                TextButton(onClick = throttle(vm.viewModelScope.launchAsFn {
                    if (oldSource == source) {
                        toast("禁用项无变动")
                        showEditDlg = false
                        return@launchAsFn
                    }
                    showEditDlg = false
                    val subsConfig = (vm.subsConfigFlow.value ?: SubsConfig(
                        type = SubsConfig.GlobalGroupType,
                        subsId = subsItemId,
                        groupKey = groupKey,
                    )).copy(
                        exclude = ExcludeData.parse(source).stringify()
                    )
                    DbSet.subsConfigDao.insert(subsConfig)
                    toast("更新成功")
                })) {
                    Text(text = "更新")
                }
            },
        )
    }

}

// null - 内置禁用
// true - 启用
// false - 禁用
fun getGlobalGroupChecked(
    subscription: RawSubscription,
    excludeData: ExcludeData,
    group: RawSubscription.RawGlobalGroup,
    appId: String,
): Boolean? {
    if (subscription.getGlobalGroupInnerDisabled(group, appId)) {
        return null
    }
    excludeData.appIds[appId]?.let { return !it }
    if (group.appIdEnable[appId] == true) return true
    if (appId == launcherAppId) {
        return group.matchLauncher ?: false
    }
    if (systemAppsFlow.value.contains(appId)) {
        return group.matchSystemApp ?: false
    }
    return group.matchAnyApp ?: true
}

private val tipText = """
以换行或英文逗号分割每条禁用
示例1-禁用单个页面
appId/activityId
示例2-禁用整个应用(移除/)
appId
示例3-开启此应用(前置!)
!appId
""".trimIndent()