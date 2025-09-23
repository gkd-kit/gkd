package li.songe.gkd.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.flow.update
import li.songe.gkd.MainActivity
import li.songe.gkd.data.AppInfo
import li.songe.gkd.store.blockA11yAppListFlow
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.AnimatedBooleanContent
import li.songe.gkd.ui.component.AnimatedIcon
import li.songe.gkd.ui.component.AppBarTextField
import li.songe.gkd.ui.component.AppIcon
import li.songe.gkd.ui.component.AppNameText
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.MultiTextField
import li.songe.gkd.ui.component.PerfCheckbox
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.QueryPkgAuthCard
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.component.useListScrollState
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.icon.BackCloseIcon
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.asMutableState
import li.songe.gkd.ui.share.noRippleClickable
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.ProfileTransitions
import li.songe.gkd.ui.style.appItemPadding
import li.songe.gkd.ui.style.menuPadding
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.AppListString
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.mapState
import li.songe.gkd.util.switchItem
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun BlockA11yAppListPage() {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel<BlockA11yAppListVm>()
    val showSystemApp by vm.showSystemAppFlow.collectAsState()
    val sortType by vm.sortTypeFlow.collectAsState()
    val appInfos by vm.appInfosFlow.collectAsState()
    val searchStr by vm.searchStrFlow.collectAsState()
    val showSearchBar by vm.showSearchBarFlow.collectAsState()
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
                canScroll = !editable,
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
                                mainVm.popBackStack()
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
                                vm.showSearchBarFlow.value = false
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
                            text = "无障碍白名单",
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
                                        blockA11yAppListFlow.value =
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
                                PerfIconButton(
                                    imageVector = PerfIcon.Edit,
                                    onClick = vm.viewModelScope.launchAsFn {
                                        if (editable && vm.textChanged) {
                                            context.justHideSoftInput()
                                            mainVm.dialogFlow.waitResult(
                                                title = "提示",
                                                text = "当前内容未保存，是否放弃编辑？",
                                            )
                                        }
                                        editable = !editable
                                    })
                                IconButton(onClick = throttle {
                                    if (showSearchBar) {
                                        if (vm.searchStrFlow.value.isEmpty()) {
                                            vm.showSearchBarFlow.value = false
                                        } else {
                                            vm.searchStrFlow.value = ""
                                        }
                                    } else {
                                        vm.showSearchBarFlow.value = true
                                    }
                                }) {
                                    AnimatedIcon(
                                        id = SafeR.ic_anim_search_close,
                                        atEnd = showSearchBar,
                                    )
                                }
                                var expanded by remember { mutableStateOf(false) }
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
                                        Text(
                                            text = "排序",
                                            modifier = Modifier.menuPadding(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        val handleItem: (AppSortOption) -> Unit = throttle { v ->
                                            storeFlow.update { s -> s.copy(a11yAppSort = v.value) }
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
                                        val handle1 = {
                                            storeFlow.update { s -> s.copy(a11yShowSystemApp = !showSystemApp) }
                                        }
                                        DropdownMenuItem(
                                            text = {
                                                Text("显示系统应用")
                                            },
                                            trailingIcon = {
                                                Checkbox(
                                                    checked = showSystemApp,
                                                    onCheckedChange = { handle1() }
                                                )
                                            },
                                            onClick = handle1,
                                        )
                                    }
                                }
                            }
                        },
                    )
                })
        },
        floatingActionButton = {},
    ) { contentPadding ->
        if (editable) {
            MultiTextField(
                modifier = Modifier.scaffoldPadding(contentPadding),
                textFlow = vm.textFlow,
                immediateFocus = true,
                placeholderText = "请输入应用ID列表\n示例:\ncom.android.systemui\ncom.android.settings",
                indicatorText = vm.indicatorTextFlow.collectAsState().value,
            )
        } else {
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
            ) {
                items(appInfos, { it.id }) { appInfo ->
                    AppItemCard(appInfo)
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    if (appInfos.isEmpty() && searchStr.isNotEmpty()) {
                        val hasShowAll = showSystemApp
                        EmptyText(text = if (hasShowAll) "暂无搜索结果" else "暂无搜索结果，请尝试修改筛选条件")
                        Spacer(modifier = Modifier.height(EmptyHeight / 2))
                    }
                    QueryPkgAuthCard()
                }
            }
        }
    }
}

@Composable
private fun AppItemCard(
    appInfo: AppInfo,
) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier
            .clickable(onClick = throttle {
                blockA11yAppListFlow.update { it.switchItem(appInfo.id) }
            })
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
                text = appInfo.id,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
        PerfCheckbox(
            key = appInfo.id,
            checked = remember(appInfo.id) {
                blockA11yAppListFlow.mapState(scope) {
                    it.contains(appInfo.id)
                }
            }.collectAsState().value,
        )
    }
}
