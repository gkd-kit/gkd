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
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import li.songe.gkd.MainActivity
import li.songe.gkd.R
import li.songe.gkd.data.AppInfo
import li.songe.gkd.permission.PermissionStates
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.AppConfigRoute
import li.songe.gkd.ui.EditBlockAppListRoute
import li.songe.gkd.ui.component.AnimatedIconButton
import li.songe.gkd.ui.component.AnimationFloatingActionButton
import li.songe.gkd.ui.component.AppBarTextField
import li.songe.gkd.ui.component.AppIcon
import li.songe.gkd.ui.component.AppNameText
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.MenuGroupCard
import li.songe.gkd.ui.component.MenuItemCheckbox
import li.songe.gkd.ui.component.MenuItemRadioButton
import li.songe.gkd.ui.component.PerfCheckbox
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.QueryPkgAuthCard
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.component.rememberListScrollState
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.noRippleClickable
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.appItemPadding
import li.songe.gkd.util.AppGroupOption
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.findOption
import li.songe.gkd.util.getUpDownTransform
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle

@Composable
fun useAppListPage(): ScaffoldExt {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity

    val vm = viewModel { AppListVm(mainVm) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val store by storeFlow.collectAsStateWithLifecycle()
    val appInfos = state.appInfos
    val searchStr = state.searchText
    val ruleSummary = state.ruleSummary

    val globalDesc = if (ruleSummary.globalGroups.isNotEmpty()) {
        "${ruleSummary.globalGroups.size}全局"
    } else {
        null
    }
    val showSearchBar = state.showSearchBar
    val refreshing = state.refreshing
    val pullToRefreshState = rememberPullToRefreshState()
    val editWhiteListMode = state.editWhiteListMode
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    LaunchedEffect(null) {
        listOf(
            PermissionStates.queryPackages.stateFlow,
            vm.appInfosFlow,
        ).forEach {
            launch {
                it.drop(1).collect {
                    pageScrollState.resetScroll()
                }
            }
        }
    }
    ResetPageScrollOnRequest(BottomNavItem.AppList, pageScrollState::resetScrollAndAwait)
    return ScaffoldExt(
        navItem = BottomNavItem.AppList,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DisposableEffect(null) {
                onDispose {
                    vm.onLeaveScreen()
                }
            }
            PerfTopAppBar(scrollBehavior = scrollBehavior, title = {
                val firstShowSearchBar = remember { showSearchBar }
                if (showSearchBar) {
                    BackHandler {
                        if (!context.imeController.requestHide()) {
                            vm.closeSearch()
                        }
                    }
                    AppBarTextField(
                        value = searchStr,
                        onValueChange = vm::setSearchText,
                        hint = "请输入应用名称/ID",
                        modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                    )
                } else {
                    val titleModifier = Modifier
                        .noRippleClickable(
                            onClick = throttle {
                                pageScrollState.resetScroll()
                            }
                        )
                    if (editWhiteListMode) {
                        BackHandler(onBack = vm::closeEditWhiteListMode)
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
                if (state.queryPackagesAbnormal) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.error) {
                        PerfIconButton(
                            imageVector = PerfIcon.WarningAmber,
                            contentDescription = PermissionStates.queryPackages.name + "异常",
                            onClick = throttle(vm.scope.launchAsFn {
                                mainVm.dialogRequests.showMessage(
                                    title = "权限异常",
                                    text = "检测到已授予「${PermissionStates.queryPackages.name}」但实际获取应用数量稀少，已使用其它方式获取但可能不全，在应用列表下拉刷新可重新获取，若无法解决可尝试关闭权限后重新授予或重启设备"
                                )
                            }),
                        )
                    }
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
                    onClick = throttle(vm::toggleEditWhiteListMode),
                )
                AnimatedIconButton(
                    onClick = throttle(vm::toggleSearch),
                    id = R.drawable.ic_anim_search_close,
                    atEnd = showSearchBar,
                    contentDescription = if (showSearchBar) "关闭搜索" else "搜索应用列表",
                )
                var expanded by remember { mutableStateOf(false) }
                PerfIconButton(
                    imageVector = PerfIcon.Sort,
                    contentDescription = "排序筛选",
                    onClick = {
                        expanded = true
                    }
                )
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart)
                ) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                        ) {
                        MenuGroupCard(inTop = true, title = "排序") {
                            AppSortOption.objects.forEach { option ->
                                MenuItemRadioButton(
                                    text = option.label,
                                    selected = AppSortOption.objects.findOption(store.appSort) == option,
                                    onClick = { vm.setSortType(option) },
                                )
                            }
                        }
                        MenuGroupCard(title = "分组") {
                            AppGroupOption.normalObjects.forEach { option ->
                                val newValue = option.invert(store.appGroupType)
                                MenuItemCheckbox(
                                    enabled = newValue != 0,
                                    text = option.label,
                                    checked = option.include(store.appGroupType),
                                    onClick = { vm.setAppGroupType(newValue) },
                                )
                            }
                        }
                        MenuGroupCard(title = "筛选") {
                            MenuItemCheckbox(
                                text = "白名单",
                                checked = store.showBlockApp,
                                onClick = {
                                    vm.setShowBlockApp(!store.showBlockApp)
                                },
                            )
                        }
                    }
                }
            })
        },
        floatingActionButton = {
            AnimationFloatingActionButton(
                visible = editWhiteListMode,
                contentDescription = "编辑白名单",
                onClick = {
                    mainVm.navigatePage(EditBlockAppListRoute)
                },
                imageVector = PerfIcon.Edit,
            )
        }
    ) { contentPadding ->
        PullToRefreshBox(
            modifier = Modifier.padding(contentPadding),
            state = pullToRefreshState,
            isRefreshing = refreshing,
            onRefresh = vm::refresh,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                if (!state.canQueryPackages) {
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
                        editWhiteListMode = editWhiteListMode,
                        inWhiteList = appInfo.id in state.whiteListAppIds,
                        onClick = {
                            if (editWhiteListMode) {
                                vm.toggleWhiteList(appInfo.id)
                            } else {
                                context.imeController.requestHide()
                                mainVm.navigatePage(AppConfigRoute(appInfo.id))
                            }
                        },
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    if (appInfos.isEmpty() && searchStr.isNotEmpty()) {
                        EmptyText(text = if (state.showAllApps) "暂无搜索结果" else "暂无搜索结果，或修改筛选")
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
    editWhiteListMode: Boolean,
    inWhiteList: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = throttle(onClick))
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
