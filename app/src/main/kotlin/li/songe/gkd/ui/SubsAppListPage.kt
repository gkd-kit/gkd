package li.songe.gkd.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.KeyboardUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.SubsAppGroupListPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.flow.update
import li.songe.gkd.MainActivity
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.AppBarTextField
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.QueryPkgAuthCard
import li.songe.gkd.ui.component.SubsAppCard
import li.songe.gkd.ui.component.TowLineText
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.component.useSubs
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.menuPadding
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.SortTypeOption
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.mapHashCode
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.throttle


@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun SubsAppListPage(
    subsItemId: Long,
) {
    val context = LocalActivity.current as MainActivity
    val navController = LocalNavController.current

    val vm = viewModel<SubsAppListVm>()
    val appAndConfigs by vm.filterAppAndConfigsFlow.collectAsState()
    val searchStr by vm.searchStrFlow.collectAsState()
    val appInfoCache by appInfoCacheFlow.collectAsState()

    var showSearchBar by rememberSaveable {
        mutableStateOf(false)
    }
    LaunchedEffect(key1 = showSearchBar, block = {
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
                        modifier = Modifier.autoFocus()
                    )
                } else {
                    TowLineText(
                        title = useSubs(subsItemId)?.name ?: subsItemId.toString(),
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
                            .navigate(SubsAppGroupListPageDestination(subsItemId, appRaw.id))
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
                )
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                val firstLoading by vm.linkLoad.firstLoadingFlow.collectAsState()
                if (appAndConfigs.isEmpty() && !firstLoading) {
                    EmptyText(
                        text = if (searchStr.isNotEmpty()) {
                            if (showUninstallApp) "暂无搜索结果" else "暂无搜索结果,请尝试修改筛选条件"
                        } else {
                            "暂无规则"
                        }
                    )
                }
                QueryPkgAuthCard()
            }
        }
    }
}