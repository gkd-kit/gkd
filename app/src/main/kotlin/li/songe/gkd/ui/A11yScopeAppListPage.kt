package li.songe.gkd.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import li.songe.gkd.MainActivity
import li.songe.gkd.R
import li.songe.gkd.store.a11yScopeAppListFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.AnimatedBooleanContent
import li.songe.gkd.ui.component.AnimatedIconButton
import li.songe.gkd.ui.component.AnimationFloatingActionButton
import li.songe.gkd.ui.component.AppBarTextField
import li.songe.gkd.ui.component.AppCheckBoxCard
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.MenuGroupCard
import li.songe.gkd.ui.component.MenuItemCheckbox
import li.songe.gkd.ui.component.MenuItemRadioButton
import li.songe.gkd.ui.component.MultiTextField
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.component.isFullVisible
import li.songe.gkd.ui.component.useListScrollState
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.icon.BackCloseIcon
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.asMutableState
import li.songe.gkd.ui.share.noRippleClickable
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.AppGroupOption
import li.songe.gkd.util.AppListString
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.switchItem
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Serializable
data object A11YScopeAppListRoute : NavKey

@Composable
fun A11yScopeAppListPage() {
    val store by storeFlow.collectAsState()
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel<A11yScopeAppListVm>()
    val appInfos by vm.appInfosFlow.collectAsState()
    val searchStr by vm.searchStrFlow.collectAsState()
    var showSearchBar by vm.showSearchBarFlow.asMutableState()
    var editable by vm.editableFlow.asMutableState()
    val (scrollBehavior, listState) = useListScrollState(vm.resetKey, canScroll = { !editable })
    BackHandler(editable, vm.viewModelScope.launchAsFn {
        context.justHideSoftInput()
        if (vm.textChanged) {
            mainVm.dialogFlow.waitResult(
                title = "提示",
                text = "当前内容未保存，是否放弃编辑？",
            )
        }
        editable = false
    })
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                canScroll = !editable && !store.blockA11yAppListFollowMatch,
                navigationIcon = {
                    IconButton(
                        onClick = throttle(vm.viewModelScope.launchAsFn {
                            if (editable) {
                                if (vm.textChanged) {
                                    context.justHideSoftInput()
                                    mainVm.dialogFlow.waitResult(
                                        title = "提示",
                                        text = "当前内容未保存，是否放弃编辑？",
                                    )
                                }
                                editable = !editable
                            } else {
                                context.hideSoftInput()
                                mainVm.popPage()
                            }
                        })
                    ) {
                        BackCloseIcon(backOrClose = !editable)
                    }
                },
                title = {
                    val firstShowSearchBar = remember { showSearchBar }
                    if (showSearchBar) {
                        BackHandler {
                            if (!context.justHideSoftInput()) {
                                showSearchBar = false
                            }
                        }
                        AppBarTextField(
                            value = searchStr,
                            onValueChange = { newValue ->
                                vm.searchStrFlow.value = newValue.trim()
                            },
                            hint = "请输入应用名称/ID",
                            modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                        )
                    } else {
                        val titleModifier = Modifier
                            .noRippleClickable(
                                onClick = throttle {
                                    vm.resetKey.intValue++
                                }
                            )
                        Text(
                            modifier = titleModifier,
                            text = "局部无障碍",
                        )
                    }
                },
                actions = {
                    AnimatedBooleanContent(
                        targetState = editable,
                        contentAlignment = Alignment.TopEnd,
                        contentTrue = {
                            PerfIconButton(
                                imageVector = PerfIcon.Save,
                                onClick = throttle {
                                    if (vm.textChanged) {
                                        a11yScopeAppListFlow.value =
                                            AppListString.decode(vm.textFlow.value)
                                        toast("更新成功")
                                    } else {
                                        toast("未修改")
                                    }
                                    context.justHideSoftInput()
                                    editable = false
                                },
                            )
                        },
                        contentFalse = {
                            Row {
                                var expanded by remember { mutableStateOf(false) }
                                AnimatedIconButton(
                                    onClick = throttle {
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
                                    id = R.drawable.ic_anim_search_close,
                                    atEnd = showSearchBar,
                                )
                                PerfIconButton(imageVector = PerfIcon.Sort, onClick = {
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
                                        MenuGroupCard(inTop = true, title = "排序") {
                                            var sortType by vm.sortTypeFlow.asMutableState()
                                            AppSortOption.objects.forEach { option ->
                                                MenuItemRadioButton(
                                                    text = option.label,
                                                    selected = sortType == option,
                                                    onClick = { sortType = option },
                                                )
                                            }
                                        }
                                        MenuGroupCard(inTop = true, title = "筛选") {
                                            var appGroupType by vm.appGroupTypeFlow.asMutableState()
                                            AppGroupOption.normalObjects.forEach { option ->
                                                val newValue = option.invert(appGroupType)
                                                MenuItemCheckbox(
                                                    enabled = newValue != 0,
                                                    text = option.label,
                                                    checked = option.include(appGroupType),
                                                    onClick = { appGroupType = newValue },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    )
                })
        },
        floatingActionButton = {
            AnimationFloatingActionButton(
                visible = !editable && scrollBehavior.isFullVisible,
                onClickLabel = "进入文本编辑模式",
                onClick = {
                    editable = !editable
                },
                imageVector = PerfIcon.Edit,
                contentDescription = "编辑文本"
            )
        },
    ) { contentPadding ->
        if (editable) {
            MultiTextField(
                modifier = Modifier.scaffoldPadding(contentPadding),
                textFlow = vm.textFlow,
                immediateFocus = true,
                placeholderText = "请输入应用ID列表\n示例:\ncom.android.systemui\ncom.android.settings",
                indicatorSize = vm.indicatorSizeFlow.collectAsState().value,
            )
        } else {
            val a11yScopeAppList by a11yScopeAppListFlow.collectAsState()
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
            ) {
                items(appInfos, { it.id }) { appInfo ->
                    val checked = a11yScopeAppList.contains(appInfo.id)
                    AppCheckBoxCard(
                        appInfo = appInfo,
                        checked = checked,
                        onCheckedChange = {
                            a11yScopeAppListFlow.update {
                                it.switchItem(appInfo.id)
                            }
                        },
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    if (appInfos.isEmpty() && searchStr.isNotEmpty()) {
                        EmptyText(text = "暂无搜索结果")
                        Spacer(modifier = Modifier.height(EmptyHeight / 2))
                    }
                }
            }
        }
    }
}
