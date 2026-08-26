package li.gkd.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import li.gkd.app.MainActivity
import li.gkd.app.R
import li.gkd.app.store.blockA11yAppListFlow
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.component.AnimatedBooleanContent
import li.gkd.app.ui.component.AnimatedIconButton
import li.gkd.app.ui.component.AnimationFloatingActionButton
import li.gkd.app.ui.component.AppBarTextField
import li.gkd.app.ui.component.AppCheckBoxCard
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.component.MenuGroupCard
import li.gkd.app.ui.component.MenuItemCheckbox
import li.gkd.app.ui.component.MenuItemRadioButton
import li.gkd.app.ui.component.MultiTextField
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.component.isFullVisible
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.icon.BackCloseIcon
import li.gkd.app.ui.icon.LockOpenRight
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.AppGroupOption
import li.gkd.app.util.AppSortOption
import li.gkd.app.util.findOption
import li.gkd.app.util.launchAsFn
import li.gkd.app.util.switchItem
import li.gkd.app.util.throttle

@Serializable
data object BlockA11yAppListRoute : NavKey

@Composable
fun BlockA11yAppListPage() {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel { BlockA11yAppListVm(mainVm) }
    val store by storeFlow.collectAsStateWithLifecycle()
    val appInfos by vm.appInfosFlow.collectAsStateWithLifecycle()
    val searchStr by vm.searchStrFlow.collectAsStateWithLifecycle()
    val showSearchBar by vm.showSearchBarFlow.collectAsStateWithLifecycle()
    val editable by vm.editableFlow.collectAsStateWithLifecycle()
    val editText by vm.textFlow.collectAsStateWithLifecycle()
    val pageScrollState = rememberListScrollState(canScroll = { !editable })
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnChange(appInfos)
    BackHandler(editable, vm.scope.launchAsFn {
        context.imeController.requestHide()
        if (vm.textChanged) {
            if (!mainVm.dialogRequests.confirm(
                title = "提示",
                text = "当前内容未保存，是否放弃编辑？",
            )) return@launchAsFn
        }
        vm.setEditable(false)
    })
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                canScroll = !editable && !store.blockA11yAppListFollowMatch,
                navigationIcon = {
                    IconButton(
                        onClick = throttle(vm.scope.launchAsFn {
                            if (editable) {
                                if (vm.textChanged) {
                                    context.imeController.requestHide()
                                    if (!mainVm.dialogRequests.confirm(
                                        title = "提示",
                                        text = "当前内容未保存，是否放弃编辑？",
                                    )) return@launchAsFn
                                }
                                vm.setEditable(false)
                            } else {
                                context.imeController.hideAndAwait()
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
                            if (!context.imeController.requestHide()) {
                                vm.setSearchBarVisible(false)
                            }
                        }
                        AppBarTextField(
                            value = searchStr,
                            onValueChange = vm::setSearchStr,
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
                                    vm.saveText()
                                    context.imeController.requestHide()
                                },
                            )
                        },
                        contentFalse = {
                            Row {
                                PerfIconButton(
                                    imageVector = if (store.blockA11yAppListFollowMatch) PerfIcon.Lock else LockOpenRight,
                                    contentDescription = if (store.blockA11yAppListFollowMatch) "已设置为跟随应用白名单" else "已设置为独立无障碍白名单",
                                    onClickLabel = "切换模式",
                                    onClick = throttle {
                                        vm.toggleFollowMatchList()
                                    }
                                )

                                var expanded by remember { mutableStateOf(false) }
                                AnimatedVisibility(!store.blockA11yAppListFollowMatch) {
                                    Row {
                                        AnimatedIconButton(
                                            onClick = throttle {
                                                vm.toggleSearchBar()
                                            },
                                            id = R.drawable.ic_anim_search_close,
                                            atEnd = showSearchBar,
                                        )
                                        PerfIconButton(imageVector = PerfIcon.Sort, onClick = {
                                            expanded = true
                                        })
                                    }
                                }
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
                                                    selected = AppSortOption.objects.findOption(store.a11yAppSort) == option,
                                                    onClick = { vm.setSortType(option) },
                                                )
                                            }
                                        }
                                        MenuGroupCard(inTop = true, title = "筛选") {
                                            AppGroupOption.normalObjects.forEach { option ->
                                                val newValue = option.invert(store.a11yAppGroupType)
                                                MenuItemCheckbox(
                                                    enabled = newValue != 0,
                                                    text = option.label,
                                                    checked = option.include(store.a11yAppGroupType),
                                                    onClick = { vm.setAppGroupType(newValue) },
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
                visible = !editable && scrollBehavior.isFullVisible && !store.blockA11yAppListFollowMatch,
                onClickLabel = "进入白名单文本编辑模式",
                onClick = {
                    vm.setEditable(true)
                },
                imageVector = PerfIcon.Edit,
                contentDescription = "编辑白名单文本"
            )
        },
    ) { contentPadding ->
        if (store.blockA11yAppListFollowMatch) {
            Column(
                modifier = Modifier.scaffoldPadding(contentPadding),
            ) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                Text(
                    text = "已设置为跟随应用白名单",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        } else if (editable) {
            MultiTextField(
                modifier = Modifier.scaffoldPadding(contentPadding),
                text = editText,
                onTextChange = vm::setText,
                immediateFocus = true,
                placeholderText = "请输入应用ID列表\n示例:\ncom.android.systemui\ncom.android.settings",
                indicatorSize = vm.indicatorSizeFlow.collectAsStateWithLifecycle().value,
            )
        } else {
            val blockA11yAppList by blockA11yAppListFlow.collectAsStateWithLifecycle()
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
            ) {
                items(appInfos, { it.id }) { appInfo ->
                    AppCheckBoxCard(
                        appInfo = appInfo,
                        checked = blockA11yAppList.contains(appInfo.id),
                        onCheckedChange = {
                            blockA11yAppListFlow.update {
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
