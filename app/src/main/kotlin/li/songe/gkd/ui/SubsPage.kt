package li.songe.gkd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.KeyboardUtils
import com.blankj.utilcode.util.LogUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AppItemPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.flow.update
import li.songe.gkd.MainActivity
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.AppBarTextField
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.QueryPkgAuthCard
import li.songe.gkd.ui.component.SubsAppCard
import li.songe.gkd.ui.component.TowLineText
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.menuPadding
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.SortTypeOption
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.json
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.mapHashCode
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubscription
import li.songe.json5.encodeToJson5String


@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun SubsPage(
    subsItemId: Long,
) {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current

    val vm = viewModel<SubsVm>()
    val subsItem = vm.subsItemFlow.collectAsState().value
    val appAndConfigs by vm.filterAppAndConfigsFlow.collectAsState()
    val searchStr by vm.searchStrFlow.collectAsState()
    val appInfoCache by appInfoCacheFlow.collectAsState()

    val subsRaw = vm.subsRawFlow.collectAsState().value

    // 本地订阅
    val editable = subsItem?.id.let { it != null && it < 0 }

    var showAddDlg by remember {
        mutableStateOf(false)
    }

    var editRawApp by remember {
        mutableStateOf<RawSubscription.RawApp?>(null)
    }

    var showSearchBar by rememberSaveable {
        mutableStateOf(false)
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(key1 = showSearchBar, block = {
        if (showSearchBar && searchStr.isEmpty()) {
            focusRequester.requestFocus()
        }
        if (!showSearchBar) {
            vm.searchStrFlow.value = ""
        }
    })
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var expanded by remember { mutableStateOf(false) }
    val showUninstallApp by vm.showUninstallAppFlow.collectAsState()
    val sortType by vm.sortTypeFlow.collectAsState()
    val listState = rememberLazyListState()
    var isFirstVisit by remember { mutableStateOf(true) }
    LaunchedEffect(
        key1 = appAndConfigs.mapHashCode { it.first.id }
    ) {
        if (isFirstVisit) {
            isFirstVisit = false
        } else {
            listState.scrollToItem(0)
        }
    }

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
                if (showSearchBar) {
                    val softwareKeyboardController = LocalSoftwareKeyboardController.current
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
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                } else {
                    TowLineText(
                        title = subsRaw?.name ?: subsItemId.toString(),
                        subtitle = "应用规则",
                    )
                }
            }, actions = {
                if (showSearchBar) {
                    IconButton(onClick = {
                        if (vm.searchStrFlow.value.isEmpty()) {
                            showSearchBar = false
                        } else {
                            vm.searchStrFlow.value = ""
                        }
                    }) {
                        Icon(Icons.Outlined.Close, contentDescription = null)
                    }
                } else {
                    IconButton(onClick = {
                        showSearchBar = true
                    }) {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                    }
                }
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = null
                    )
                }
                Box(
                    modifier = Modifier.wrapContentSize(Alignment.TopStart)
                ) {
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
                                            storeFlow.update { s -> s.copy(subsAppSortType = sortOption.value) }
                                        })
                                },
                                onClick = {
                                    storeFlow.update { s -> s.copy(subsAppSortType = sortOption.value) }
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
                                Text("显示未安装应用")
                            },
                            trailingIcon = {
                                Checkbox(checked = showUninstallApp, onCheckedChange = {
                                    storeFlow.update { s -> s.copy(subsAppShowUninstallApp = it) }
                                })
                            },
                            onClick = {
                                storeFlow.update { s -> s.copy(subsAppShowUninstallApp = !showUninstallApp) }
                            },
                        )
                    }
                }

            })
        },
        floatingActionButton = {
            if (editable) {
                FloatingActionButton(onClick = { showAddDlg = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                    )
                }
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState
        ) {
            itemsIndexed(appAndConfigs, { i, a -> i.toString() + a.first.id }) { _, a ->
                val (appRaw, subsConfig, enableSize) = a
                SubsAppCard(
                    rawApp = appRaw,
                    appInfo = appInfoCache[appRaw.id],
                    subsConfig = subsConfig,
                    enableSize = enableSize,
                    onClick = throttle {
                        navController.toDestinationsNavigator()
                            .navigate(AppItemPageDestination(subsItemId, appRaw.id))
                    },
                    onValueChange = throttle(fn = vm.viewModelScope.launchAsFn { enable ->
                        val newItem = subsConfig?.copy(
                            enable = enable
                        ) ?: SubsConfig(
                            enable = enable,
                            type = SubsConfig.AppType,
                            subsItemId = subsItemId,
                            appId = appRaw.id,
                        )
                        DbSet.subsConfigDao.insert(newItem)
                    }),
                    showMenu = editable,
                    onDelClick = throttle(fn = vm.viewModelScope.launchAsFn {
                        context.mainVm.dialogFlow.waitResult(
                            title = "删除规则组",
                            text = "确定删除 ${appInfoCache[appRaw.id]?.name ?: appRaw.name ?: appRaw.id} 下所有规则组?",
                            error = true,
                        )
                        if (subsRaw != null && subsItem != null) {
                            updateSubscription(subsRaw.copy(apps = subsRaw.apps.filter { a -> a.id != appRaw.id }))
                            DbSet.subsItemDao.update(subsItem.copy(mtime = System.currentTimeMillis()))
                            DbSet.subsConfigDao.delete(subsItem.id, appRaw.id)
                            toast("删除成功")
                        }
                    })
                )
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (appAndConfigs.isEmpty()) {
                    EmptyText(
                        text = if (searchStr.isNotEmpty()) {
                            if (showUninstallApp) "暂无搜索结果" else "暂无搜索结果,请尝试修改筛选条件"
                        } else {
                            "暂无规则"
                        }
                    )
                } else if (editable) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
                QueryPkgAuthCard()
            }
        }
    }


    if (showAddDlg && subsRaw != null && subsItem != null) {
        var source by remember {
            mutableStateOf("")
        }
        val inputFocused = rememberSaveable { mutableStateOf(false) }
        AlertDialog(title = { Text(text = "添加应用规则") }, text = {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                maxLines = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        if (it.isFocused) {
                            inputFocused.value = true
                        }
                    },
                placeholder = { Text(text = "请输入规则\n若应用规则已经存在则追加") },
            )
        }, onDismissRequest = {
            if (!inputFocused.value) {
                showAddDlg = false
            }
        }, confirmButton = {
            TextButton(onClick = {
                val newAppRaw = try {
                    RawSubscription.parseRawApp(source)
                } catch (e: Exception) {
                    LogUtils.d(e)
                    toast("非法规则${e.message}")
                    return@TextButton
                }
                if (newAppRaw.groups.isEmpty()) {
                    toast("不允许添加空规则组")
                    return@TextButton
                }
                if (newAppRaw.groups.any { s -> s.name.isBlank() }) {
                    toast("不允许添加空白名规则组,请先命名")
                    return@TextButton
                }
                val oldAppRawIndex = subsRaw.apps.indexOfFirst { a -> a.id == newAppRaw.id }
                val oldAppRaw = subsRaw.apps.getOrNull(oldAppRawIndex)
                if (oldAppRaw != null) {
                    // check same group name
                    newAppRaw.groups.forEach { g ->
                        if (oldAppRaw.groups.any { g0 -> g0.name == g.name }) {
                            toast("已经存在同名规则[${g.name}]\n请修改名称后再添加")
                            return@TextButton
                        }
                    }
                }
                // 重写添加的规则的 key
                val initKey =
                    ((oldAppRaw?.groups ?: emptyList()).maxByOrNull { g -> g.key }?.key ?: -1) + 1
                val finalAppRaw = if (oldAppRaw != null) {
                    newAppRaw.copy(groups = oldAppRaw.groups + newAppRaw.groups.mapIndexed { i, g ->
                        g.copy(
                            key = initKey + i
                        )
                    })
                } else {
                    newAppRaw.copy(groups = newAppRaw.groups.mapIndexed { i, g ->
                        g.copy(
                            key = initKey + i
                        )
                    })
                }
                val newApps = if (oldAppRaw != null) {
                    subsRaw.apps.toMutableList().apply {
                        set(oldAppRawIndex, finalAppRaw)
                    }
                } else {
                    subsRaw.apps.toMutableList().apply {
                        add(finalAppRaw)
                    }
                }
                vm.viewModelScope.launchTry {
                    updateSubscription(
                        subsRaw.copy(
                            apps = newApps, version = subsRaw.version + 1
                        )
                    )
                    DbSet.subsItemDao.update(subsItem.copy(mtime = System.currentTimeMillis()))
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

    val editAppRawVal = editRawApp
    if (editAppRawVal != null && subsItem != null && subsRaw != null) {
        var source by remember {
            mutableStateOf(json.encodeToJson5String(editAppRawVal))
        }
        val inputFocused = rememberSaveable { mutableStateOf(false) }
        AlertDialog(
            title = { Text(text = "编辑应用规则") },
            text = {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    maxLines = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (it.isFocused) {
                                inputFocused.value = true
                            }
                        },
                    placeholder = { Text(text = "请输入规则") },
                )
                LaunchedEffect(null) {
                    focusRequester.requestFocus()
                }
            },
            onDismissRequest = {
                if (!inputFocused.value) {
                    editRawApp = null
                }
            }, confirmButton = {
                TextButton(onClick = {
                    try {
                        val newAppRaw = RawSubscription.parseRawApp(source)
                        if (newAppRaw.id != editAppRawVal.id) {
                            toast("不允许修改规则id")
                            return@TextButton
                        }
                        val oldAppRawIndex =
                            subsRaw.apps.indexOfFirst { a -> a.id == editAppRawVal.id }
                        vm.viewModelScope.launchTry {
                            updateSubscription(
                                subsRaw.copy(
                                    apps = subsRaw.apps.toMutableList().apply {
                                        set(oldAppRawIndex, newAppRaw)
                                    }, version = subsRaw.version + 1
                                )
                            )
                            DbSet.subsItemDao.update(subsItem.copy(mtime = System.currentTimeMillis()))
                            editRawApp = null
                            toast("更新成功")
                        }
                    } catch (e: Exception) {
                        LogUtils.d(e)
                        toast("非法规则${e.message}")
                    }
                }, enabled = source.isNotEmpty()) {
                    Text(text = "添加")
                }
            }, dismissButton = {
                TextButton(onClick = { editRawApp = null }) {
                    Text(text = "取消")
                }
            })
    }
}