package li.songe.gkd.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.MainActivity
import li.songe.gkd.R
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.AnimatedIconButton
import li.songe.gkd.ui.component.AppBarTextField
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.MenuGroupCard
import li.songe.gkd.ui.component.MenuItemCheckbox
import li.songe.gkd.ui.component.MenuItemRadioButton
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.SubsAppCard
import li.songe.gkd.ui.component.TowLineText
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.component.rememberListScrollState
import li.songe.gkd.ui.component.useSubs
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.ui.share.Loadable
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.noRippleClickable
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.AppGroupOption
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.LOCAL_SUBS_IDS
import li.songe.gkd.util.appInfoMapFlow
import li.songe.gkd.util.findOption
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.throttle

@Serializable
data class SubsAppListRoute(val subsItemId: Long) : NavKey

@Composable
fun SubsAppListPage(route: SubsAppListRoute) {
    val subsItemId = route.subsItemId

    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel { SubsAppListVm(route, mainVm) }
    val scope = vm.scope
    val subscription = useSubs(subsItemId)

    val loadableState by vm.uiState.collectAsStateWithLifecycle()
    val appConfigMapState by vm.appConfigMapState.collectAsStateWithLifecycle()
    val enableSizeMapState by vm.enableSizeMapState.collectAsStateWithLifecycle()
    val appInfoMap by appInfoMapFlow.collectAsStateWithLifecycle()
    val store by storeFlow.collectAsStateWithLifecycle()
    val state = loadableState.value
    val firstLoading = loadableState is Loadable.Loading
    val loadError = (loadableState as? Loadable.Failure)?.cause
    val apps = state?.apps.orEmpty()
    val showAllApps = state?.showAllApps ?: true
    val appConfigMap = appConfigMapState.value.orEmpty()
    val enableSizeMap = enableSizeMapState.value.orEmpty()
    val switchEnabled = appConfigMapState is Loadable.Ready
    val searchStr by vm.searchStrFlow.collectAsStateWithLifecycle()
    val showSearchBar by vm.showSearchBarFlow.collectAsStateWithLifecycle()

    LaunchedEffect(key1 = showSearchBar, block = {
        if (!showSearchBar) {
            vm.setSearchText("")
        }
    })
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnChange(apps.map { it.id })
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                PerfIconButton(
                    imageVector = PerfIcon.ArrowBack,
                    onClick = throttle {
                        scope.launchTry {
                            context.imeController.hideAndAwait()
                            mainVm.popPage()
                        }
                    },
                )
            }, title = {
                val firstShowSearchBar = remember { showSearchBar }
                if (showSearchBar) {
                    BackHandler {
                        if (!context.imeController.requestHide()) {
                            vm.setSearchBarVisible(false)
                        }
                    }
                    AppBarTextField(
                        value = searchStr,
                        onValueChange = { newValue -> vm.setSearchText(newValue.trim()) },
                        hint = "请输入应用名称/ID",
                        modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                    )
                } else {
                    TowLineText(
                        title = subscription?.name ?: subsItemId.toString(),
                        subtitle = "应用规则",
                        modifier = Modifier.noRippleClickable {
                            pageScrollState.resetScroll()
                        }
                    )
                }
            }, actions = {
                AnimatedIconButton(
                    onClick = {
                        if (showSearchBar) {
                            if (searchStr.isEmpty()) {
                                vm.setSearchBarVisible(false)
                            } else {
                                vm.setSearchText("")
                            }
                        } else {
                            vm.setSearchBarVisible(true)
                        }
                    },
                    id = R.drawable.ic_anim_search_close,
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
                        MenuGroupCard(inTop = true, title = "排序") {
                            AppSortOption.objects.forEach { option ->
                                MenuItemRadioButton(
                                    text = option.label,
                                    selected = AppSortOption.objects.findOption(store.subsAppSort) == option,
                                    onClick = { vm.setSortType(option) },
                                )
                            }
                        }
                        MenuGroupCard(title = "分组") {
                            AppGroupOption.allObjects.forEach { option ->
                                val newValue = option.invert(store.subsAppGroupType)
                                MenuItemCheckbox(
                                    enabled = newValue != 0,
                                    text = option.label,
                                    checked = option.include(store.subsAppGroupType),
                                    onClick = { vm.setAppGroupType(newValue) },
                                )
                            }
                        }
                        MenuGroupCard(title = "筛选") {
                            MenuItemCheckbox(
                                text = "白名单",
                                checked = store.subsAppShowBlock,
                                onClick = vm::toggleShowBlockApps,
                            )
                        }
                    }
                }
            })
        },
        floatingActionButton = {
            if (LOCAL_SUBS_IDS.contains(subsItemId)) {
                FloatingActionButton(onClick = throttle {
                    mainVm.navigatePage(
                        UpsertRuleGroupRoute(
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
            items(apps, { it.id }) { app ->
                SubsAppCard(
                    rawApp = app,
                    appInfo = appInfoMap[app.id],
                    appConfig = appConfigMap[app.id],
                    enableSize = enableSizeMap[app.id],
                    switchEnabled = switchEnabled,
                    onClick = throttle {
                        context.imeController.requestHide()
                        mainVm.navigatePage(SubsAppGroupListRoute(subsItemId, app.id))
                    },
                    onValueChange = { enable ->
                        scope.launchTry {
                            vm.setAppEnabled(app.id, enable)
                        }
                    },
                )
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (apps.isEmpty() && !firstLoading) {
                    EmptyText(
                        text = if (loadError != null) {
                            loadError.message ?: "订阅加载失败"
                        } else if (searchStr.isNotEmpty()) {
                            if (showAllApps) "暂无搜索结果" else "暂无搜索结果，或修改筛选"
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
