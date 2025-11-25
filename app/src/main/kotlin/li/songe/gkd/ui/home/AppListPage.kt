package li.songe.gkd.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.generated.destinations.AppConfigPageDestination
import com.ramcosta.composedestinations.generated.destinations.EditBlockAppListPageDestination
import kotlinx.coroutines.flow.update
import li.songe.gkd.MainActivity
import li.songe.gkd.data.AppInfo
import li.songe.gkd.permission.canQueryPkgState
import li.songe.gkd.store.blockMatchAppListFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.AnimatedIconButton
import li.songe.gkd.ui.component.AnimationFloatingActionButton
import li.songe.gkd.ui.component.AppBarTextField
import li.songe.gkd.ui.component.AppIcon
import li.songe.gkd.ui.component.AppNameText
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.PerfCheckbox
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.QueryPkgAuthCard
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.component.updateDialogOptions
import li.songe.gkd.ui.component.useListScrollState
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.noRippleClickable
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.appItemPadding
import li.songe.gkd.ui.style.menuPadding
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.appListAuthAbnormalFlow
import li.songe.gkd.util.getUpDownTransform
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.switchItem
import li.songe.gkd.util.throttle
import li.songe.gkd.util.updateAllAppInfo
import li.songe.gkd.util.updateAppMutex

@Composable
fun useAppListPage(): ScaffoldExt {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity

    val vm = viewModel<HomeVm>()
    val showBlockApp by vm.showBlockAppFlow.collectAsState()
    val sortType by vm.sortTypeFlow.collectAsState()
    val appInfos by vm.appInfosFlow.collectAsState()
    val searchStr by vm.searchStrFlow.collectAsState()
    val ruleSummary by ruleSummaryFlow.collectAsState()

    val globalDesc = if (ruleSummary.globalGroups.isNotEmpty()) {
        "${ruleSummary.globalGroups.size}全局"
    } else {
        null
    }
    val appListKey by mainVm.appListKeyFlow.collectAsState()
    val showSearchBar by vm.showSearchBarFlow.collectAsState()
    val (scrollBehavior, listState) = useListScrollState(appListKey)
    val refreshing by updateAppMutex.state.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()
    val editWhiteListMode by vm.editWhiteListModeFlow.collectAsState()
    return ScaffoldExt(
        navItem = BottomNavItem.AppList,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DisposableEffect(null) {
                onDispose {
                    if (vm.searchStrFlow.value.isEmpty()) {
                        vm.showSearchBarFlow.value = false
                    }
                    vm.editWhiteListModeFlow.value = false
                }
            }
            PerfTopAppBar(scrollBehavior = scrollBehavior, title = {
                val firstShowSearchBar = remember { showSearchBar }
                if (showSearchBar) {
                    BackHandler {
                        if (!context.justHideSoftInput()) {
                            vm.showSearchBarFlow.value = false
                        }
                    }
                    AppBarTextField(
                        value = searchStr,
                        onValueChange = { newValue -> vm.searchStrFlow.value = newValue.trim() },
                        hint = "请输入应用名称/ID",
                        modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                    )
                } else {
                    val titleModifier = Modifier
                        .noRippleClickable(
                            onClick = throttle {
                                mainVm.appListKeyFlow.update { it + 1 }
                            }
                        )
                    if (editWhiteListMode) {
                        BackHandler {
                            vm.editWhiteListModeFlow.value = false
                        }
                    }
                    AnimatedContent(
                        targetState = editWhiteListMode,
                        transitionSpec = { getUpDownTransform() },
                    ) { localEditWhiteListMode ->
                        if (localEditWhiteListMode) {
                            Text(
                                modifier = titleModifier,
                                text = "应用白名单",
                            )
                        } else {
                            Text(
                                modifier = titleModifier,
                                text = BottomNavItem.AppList.label,
                            )
                        }
                    }
                }
            }, actions = {
                if (appListAuthAbnormalFlow.collectAsState().value) {
                    PerfIconButton(
                        imageVector = PerfIcon.WarningAmber,
                        contentDescription = canQueryPkgState.name + "异常",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = throttle {
                            mainVm.dialogFlow.updateDialogOptions(
                                title = "权限异常",
                                text = "检测到已授予「${canQueryPkgState.name}」但实际获取用户应用数量稀少\n\n在应用列表下拉刷新可重新获取，若无法解决可尝试关闭权限后重新授予或重启设备"
                            )
                        },
                    )
                }
                PerfIconButton(
                    imageVector = PerfIcon.Block,
                    contentDescription = "切换白名单编辑模式",
                    onClickLabel = if (editWhiteListMode) "退出编辑" else "进入编辑",
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (editWhiteListMode) {
                            CheckboxDefaults.colors().checkedBoxColor
                        } else {
                            LocalContentColor.current
                        }
                    ),
                    onClick = throttle {
                        vm.editWhiteListModeFlow.update { !it }
                    },
                )
                AnimatedIconButton(
                    onClick = throttle {
                        if (showSearchBar) {
                            if (vm.searchStrFlow.value.isEmpty()) {
                                vm.showSearchBarFlow.value = false
                            } else {
                                vm.searchStrFlow.value = ""
                            }
                        } else {
                            vm.showSearchBarFlow.value = true
                        }
                    },
                    id = SafeR.ic_anim_search_close,
                    atEnd = showSearchBar,
                    contentDescription = if (showSearchBar) "关闭搜索" else "搜索应用列表",
                )
                var expanded by remember { mutableStateOf(false) }
                PerfIconButton(
                    imageVector = PerfIcon.Sort,
                    contentDescription = "排序筛选",
                    onClick = {
                        expanded = true
                    })
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
                        val handleItem: (AppSortOption) -> Unit = throttle { v ->
                            storeFlow.update { s -> s.copy(appSort = v.value) }
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
                                        }
                                    )
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
                        val handle3 = {
                            storeFlow.update { s -> s.copy(showBlockApp = !s.showBlockApp) }
                        }
                        DropdownMenuItem(
                            text = {
                                Text("显示白名单")
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = showBlockApp,
                                    onCheckedChange = { handle3() }
                                )
                            },
                            onClick = handle3,
                        )
                    }
                }
            })
        },
        floatingActionButton = {
            AnimationFloatingActionButton(
                visible = editWhiteListMode,
                contentDescription = "编辑白名单",
                onClick = {
                    mainVm.navigatePage(EditBlockAppListPageDestination)
                },
                imageVector = PerfIcon.Edit,
            )
        }
    ) { contentPadding ->
        val canQueryPkg by canQueryPkgState.stateFlow.collectAsState()
        PullToRefreshBox(
            modifier = Modifier.padding(contentPadding),
            state = pullToRefreshState,
            isRefreshing = refreshing,
            onRefresh = { updateAllAppInfo() }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                if (!canQueryPkg) {
                    item(key = 1, contentType = 1) {
                        QueryPkgAuthCard()
                    }
                }
                items(appInfos, { it.id }) { appInfo ->
                    val desc = run {
                        if (editWhiteListMode) return@run null
                        val appGroups = ruleSummary.appIdToAllGroups[appInfo.id] ?: emptyList()
                        val appDesc = if (appGroups.isNotEmpty()) {
                            when (val disabledCount = appGroups.count { g -> !g.enable }) {
                                0 -> "${appGroups.size}组规则"
                                appGroups.size -> "${appGroups.size}组规则/${disabledCount}关闭"
                                else -> {
                                    "${appGroups.size}组规则/${appGroups.size - disabledCount}启用/${disabledCount}关闭"
                                }
                            }
                        } else {
                            null
                        }
                        if (globalDesc != null) {
                            if (appDesc != null) {
                                "$globalDesc/$appDesc"
                            } else {
                                globalDesc
                            }
                        } else {
                            appDesc
                        }
                    }
                    AppItemCard(
                        appInfo = appInfo,
                        desc = desc,
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    if (appInfos.isEmpty() && searchStr.isNotEmpty()) {
                        val hasShowAll = showBlockApp
                        EmptyText(text = if (hasShowAll) "暂无搜索结果" else "暂无搜索结果，请尝试修改筛选条件")
                        Spacer(modifier = Modifier.height(EmptyHeight / 2))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppItemCard(
    appInfo: AppInfo,
    desc: String?,
) {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel<HomeVm>()
    val editWhiteListMode = vm.editWhiteListModeFlow.collectAsState().value
    val inWhiteList = blockMatchAppListFlow.collectAsState().value.contains(appInfo.id)
    Row(
        modifier = Modifier
            .clickable(
                onClick = throttle {
                    if (vm.editWhiteListModeFlow.value) {
                        blockMatchAppListFlow.update { it.switchItem(appInfo.id) }
                    } else {
                        context.justHideSoftInput()
                        mainVm.navigatePage(AppConfigPageDestination(appInfo.id))
                    }
                })
            .clearAndSetSemantics {
                contentDescription = if (editWhiteListMode) {
                    appInfo.name
                } else {
                    "应用：${appInfo.name}，${desc ?: appInfo.id}"
                }
                if (inWhiteList) {
                    stateDescription = "已加入白名单"
                } else if (editWhiteListMode) {
                    stateDescription = "未加入白名单"
                }
                onClick(
                    label = if (editWhiteListMode) if (inWhiteList) "从白名单中移除" else "加入白名单" else "进入规则汇总页面",
                    action = null
                )
            }
            .appItemPadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(appId = appInfo.id)
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            AppNameText(appInfo = appInfo)
            Text(
                text = desc ?: appInfo.id,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
        if (editWhiteListMode) {
            PerfCheckbox(
                key = appInfo.id,
                checked = inWhiteList,
            )
        } else if (inWhiteList) {
            PerfIcon(
                modifier = Modifier
                    .padding(2.dp)
                    .size(20.dp),
                imageVector = PerfIcon.Block,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
