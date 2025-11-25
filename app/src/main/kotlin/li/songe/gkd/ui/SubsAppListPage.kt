package li.songe.gkd.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.SubsAppGroupListPageDestination
import com.ramcosta.composedestinations.generated.destinations.UpsertRuleGroupPageDestination
import kotlinx.coroutines.flow.update
import li.songe.gkd.MainActivity
import li.songe.gkd.data.AppConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.AnimatedIconButton
import li.songe.gkd.ui.component.AppBarTextField
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.SubsAppCard
import li.songe.gkd.ui.component.TowLineText
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.component.useListScrollState
import li.songe.gkd.ui.component.useSubs
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.noRippleClickable
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.ProfileTransitions
import li.songe.gkd.ui.style.menuPadding
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.LOCAL_SUBS_IDS
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle


@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun SubsAppListPage(
    subsItemId: Long,
) {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel<SubsAppListVm>()

    val appTripleList by vm.appItemListFlow.collectAsState()
    val searchStr by vm.searchStrFlow.collectAsState()

    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(key1 = showSearchBar, block = {
        if (!showSearchBar) {
            vm.searchStrFlow.value = ""
        }
    })
    val (scrollBehavior, listState) = useListScrollState(
        vm.resetKey,
    )
    var expanded by remember { mutableStateOf(false) }
    val showUninstallApp by vm.showUninstallAppFlow.collectAsState()
    val sortType by vm.sortTypeFlow.collectAsState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                PerfIconButton(
                    imageVector = PerfIcon.ArrowBack,
                    onClick = throttle(vm.viewModelScope.launchAsFn {
                        context.hideSoftInput()
                        mainVm.popBackStack()
                    }),
                )
            }, title = {
                val firstShowSearchBar = remember { showSearchBar }
                if (showSearchBar) {
                    BackHandler {
                        if (!context.justHideSoftInput()) {
                            showSearchBar = false
                        }
                    }
                    AppBarTextField(
                        value = searchStr,
                        onValueChange = { newValue -> vm.searchStrFlow.value = newValue.trim() },
                        hint = "请输入应用名称/ID",
                        modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                    )
                } else {
                    TowLineText(
                        title = useSubs(subsItemId)?.name ?: subsItemId.toString(),
                        subtitle = "应用规则",
                        modifier = Modifier.noRippleClickable {
                            vm.resetKey.intValue++
                        }
                    )
                }
            }, actions = {
                AnimatedIconButton(
                    onClick = {
                        if (showSearchBar) {
                            if (vm.searchStrFlow.value.isEmpty()) {
                                showSearchBar = false
                            } else {
                                vm.searchStrFlow.value = ""
                            }
                        } else {
                            showSearchBar = true
                        }
                    },
                    id = SafeR.ic_anim_search_close,
                    atEnd = showSearchBar,
                )
                PerfIconButton(
                    imageVector = PerfIcon.Sort,
                    onClick = {
                        expanded = true
                    },
                )
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
                        val handleItem: (AppSortOption) -> Unit = throttle { v ->
                            storeFlow.update { s -> s.copy(subsAppSort = v.value) }
                        }
                        AppSortOption.objects.forEach { sortOption ->
                            DropdownMenuItem(
                                text = {
                                    Text(sortOption.label)
                                },
                                trailingIcon = {
                                    RadioButton(
                                        selected = sortType == sortOption,
                                        onClick = {
                                            handleItem(sortOption)
                                        })
                                },
                                onClick = {
                                    handleItem(sortOption)
                                },
                            )
                        }
                        Text(
                            text = "筛选",
                            modifier = Modifier.menuPadding(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        val handle1 = {
                            storeFlow.update { s -> s.copy(subsAppShowUninstallApp = !s.subsAppShowUninstallApp) }
                        }
                        DropdownMenuItem(
                            text = {
                                Text("显示未安装应用")
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = showUninstallApp,
                                    onCheckedChange = { handle1() }
                                )
                            },
                            onClick = handle1,
                        )
                    }
                }
            })
        },
        floatingActionButton = {
            if (LOCAL_SUBS_IDS.contains(subsItemId)) {
                FloatingActionButton(onClick = throttle {
                    mainVm.navigatePage(
                        UpsertRuleGroupPageDestination(
                            subsId = subsItemId,
                            groupKey = null,
                            appId = "",
                            forward = true,
                        )
                    )
                }) {
                    PerfIcon(
                        imageVector = PerfIcon.Add,
                    )
                }
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState
        ) {
            items(appTripleList, { it.id }) { a ->
                SubsAppCard(
                    data = a,
                    onClick = throttle {
                        context.justHideSoftInput()
                        mainVm.navigatePage(SubsAppGroupListPageDestination(subsItemId, a.id))
                    },
                    onValueChange = vm.viewModelScope.launchAsFn { enable ->
                        val newItem = a.appConfig?.copy(
                            enable = enable
                        ) ?: AppConfig(
                            enable = enable,
                            subsId = subsItemId,
                            appId = a.id,
                        )
                        DbSet.appConfigDao.insert(newItem)
                    },
                )
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                val firstLoading by vm.firstLoadingFlow.collectAsState()
                if (appTripleList.isEmpty() && !firstLoading) {
                    EmptyText(
                        text = if (searchStr.isNotEmpty()) {
                            if (showUninstallApp) "暂无搜索结果" else "暂无搜索结果，请尝试修改筛选条件"
                        } else {
                            "暂无规则"
                        }
                    )
                    Spacer(modifier = Modifier.height(EmptyHeight / 2))
                }
            }
        }
    }
}