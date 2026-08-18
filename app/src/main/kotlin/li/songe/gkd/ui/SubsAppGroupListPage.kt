package li.songe.gkd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.ui.component.AnimationFloatingActionButton
import li.songe.gkd.ui.component.BatchActionButtonGroup
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.RuleGroupCard
import li.songe.gkd.ui.component.SubscriptionPageContent
import li.songe.gkd.ui.component.TowLineText
import li.songe.gkd.ui.component.animateListItem
import li.songe.gkd.ui.component.rememberMultiSelectionState
import li.songe.gkd.ui.component.rememberListScrollState
import li.songe.gkd.ui.icon.BackCloseIcon
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.ui.share.Loadable
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.noRippleClickable
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.copyText
import li.songe.gkd.util.getUpDownTransform
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Serializable
data class SubsAppGroupListRoute(
    val subsItemId: Long,
    val appId: String,
    val focusGroupKey: Int? = null, // 背景/边框高亮一下
) : NavKey

@Composable
fun SubsAppGroupListPage(route: SubsAppGroupListRoute) {
    val subsItemId = route.subsItemId
    val appId = route.appId
    val focusGroupKey = route.focusGroupKey

    val mainVm = LocalMainViewModel.current
    val vm = viewModel { SubsAppGroupListVm(route) }
    val scope = vm.scope
    val focusGroup = vm.focusGroupFlow?.collectAsStateWithLifecycle()?.value

    SubscriptionPageContent(vm.uiState) { state ->
        val subs = state.subscription
        val configs = state.configs.value
        val subsConfigs = configs?.subsConfigs.orEmpty()
        val categoryConfigs = configs?.categoryConfigs.orEmpty()
        val switchEnabled = state.configs is Loadable.Ready
        val app = state.app
        val editable = subsItemId < 0
        val selectionState = rememberMultiSelectionState<Int>()
        val selectedKeys = selectionState.selectedKeys
        val isSelectedMode = selectionState.active
        LaunchedEffect(app.groups) {
            selectionState.retain(app.groups.mapTo(mutableSetOf()) { it.key })
        }
        BackHandler(isSelectedMode) {
            selectionState.clear()
        }
        val updateSelected: (Boolean?) -> Unit = { enabled ->
            scope.launchTry {
                val action = when (enabled) {
                    false -> "关闭"
                    true -> "启用"
                    null -> "重置开关至默认值"
                }
                if (!mainVm.dialogRequests.confirm(
                    title = "操作提示",
                    text = "是否将所选规则全部${action}?\n\n注: 也可在「订阅-规则类别」操作",
                )) return@launchTry
                val changedSize = vm.updateSelectedEnabled(selectedKeys, enabled)
                if (changedSize > 0) {
                    val result = if (enabled == null) "重置" else if (enabled) "已启用" else "已关闭"
                    toast("$result $changedSize 规则")
                } else {
                    toast(if (enabled == null) "无可重置规则" else "无规则被改变")
                }
            }
        }
        val pageScrollState = rememberListScrollState()
        val scrollBehavior = pageScrollState.scrollBehavior
        val listState = pageScrollState.listState
        pageScrollState.ResetOnChange(app.groups.isEmpty())
        if (focusGroupKey != null) {
            LaunchedEffect(null) {
                if (focusGroup != null) {
                    val i = app.groups.indexOfFirst { it.key == focusGroupKey }
                    if (i >= 0) {
                        listState.scrollToItem(i)
                    }
                }
            }
        }
        Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                IconButton(onClick = throttle {
                    if (isSelectedMode) {
                        selectionState.clear()
                    } else {
                        mainVm.popPage()
                    }
                }) {
                    BackCloseIcon(backOrClose = !isSelectedMode)
                }
            }, title = {
                val titleModifier = Modifier.noRippleClickable(onClick = pageScrollState::resetScroll)
                if (isSelectedMode) {
                    Text(
                        modifier = titleModifier,
                        text = selectedKeys.size.toString(),
                    )
                } else {
                    TowLineText(
                        modifier = titleModifier,
                        title = subs.name,
                        subtitle = appId,
                        showApp = true,
                        appFallbackName = app.name,
                    )
                }
            }, actions = {
                var expanded by remember { mutableStateOf(false) }
                AnimatedContent(
                    targetState = isSelectedMode,
                    transitionSpec = { getUpDownTransform() },
                    contentAlignment = Alignment.TopEnd,
                ) {
                    if (it) {
                        Row {
                            PerfIconButton(
                                imageVector = PerfIcon.ContentCopy,
                                onClick = throttle {
                                    scope.launchTry {
                                        copyText(vm.buildSelectedGroupsText(selectedKeys))
                                    }
                                },
                            )
                            BatchActionButtonGroup(
                                onDisable = { updateSelected(false) },
                                onEnable = { updateSelected(true) },
                                onReset = { updateSelected(null) },
                            )
                            if (editable) {
                                PerfIconButton(
                                    imageVector = PerfIcon.Delete,
                                    onClick = throttle {
                                        val keysToDelete = selectedKeys
                                        scope.launchTry {
                                            if (!mainVm.dialogRequests.confirm(
                                                title = "删除规则",
                                                text = "删除当前所选规则?",
                                                error = true,
                                            )) return@launchTry
                                            val deletedSize = vm.deleteSelectedGroups(keysToDelete)
                                            selectionState.clear()
                                            toast(
                                                if (deletedSize > 0) {
                                                    "删除成功"
                                                } else {
                                                    "所选规则已变化"
                                                }
                                            )
                                        }
                                    },
                                )
                            }
                            PerfIconButton(imageVector = PerfIcon.MoreVert, onClick = {
                                expanded = true
                            })
                        }
                    }
                }
                if (isSelectedMode) {
                    Box(
                        modifier = Modifier
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(text = "全选")
                                },
                                onClick = {
                                    expanded = false
                                    selectionState.selectAll(app.groups.map { it.key })
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(text = "反选")
                                },
                                onClick = {
                                    expanded = false
                                    selectionState.invert(app.groups.map { it.key })
                                }
                            )
                        }
                    }
                }
            })
        }, floatingActionButton = {
            if (editable) {
                AnimationFloatingActionButton(
                    visible = !isSelectedMode,
                    onClick = {
                        mainVm.navigatePage(
                            UpsertRuleGroupRoute(
                                subsId = subsItemId,
                                groupKey = null,
                                appId = appId
                            )
                        )
                    },
                    contentDescription = "添加规则",
                    imageVector = PerfIcon.Add,
                )
            }
        }) { contentPadding ->
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(app.groups, { it.key }) { group ->
                    val category = subs.getCategory(group.name)
                    val subsConfig = subsConfigs.find { it.groupKey == group.key }
                    val categoryConfig = categoryConfigs.find {
                        it.categoryKey == category?.key
                    }
                    RuleGroupCard(
                        modifier = Modifier.animateListItem(),
                        subs = subs,
                        appId = appId,
                        group = group,
                        subsConfig = subsConfig,
                        categoryConfig = categoryConfig,
                        switchEnabled = switchEnabled,
                        onOpen = {
                            mainVm.showRuleGroup(
                                subscriptionId = subs.id,
                                appId = appId,
                                group = group,
                            )
                        },
                        onCheckedChange = { enabled ->
                            scope.launchTry {
                                vm.setGroupEnabled(group, subsConfig, enabled)
                            }
                        },
                        focusGroup = focusGroup,
                        onFocusHandled = vm::consumeFocusGroup,
                        isSelectedMode = isSelectedMode,
                        isSelected = group.key in selectedKeys,
                        onLongClick = {
                            if (app.groups.size > 1) {
                                selectionState.selectOnly(group.key)
                            }
                        },
                        onSelectedChange = {
                            selectionState.toggle(group.key)
                        }
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    if (app.groups.isEmpty()) {
                        EmptyText(text = "暂无规则")
                    }
                }
            }
        }
    }
}
