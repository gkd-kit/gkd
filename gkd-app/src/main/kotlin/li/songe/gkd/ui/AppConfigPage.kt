package li.songe.gkd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import li.songe.gkd.data.ActionLog
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.AnimatedBooleanContent
import li.songe.gkd.ui.component.AnimationFloatingActionButton
import li.songe.gkd.ui.component.AppNameText
import li.songe.gkd.ui.component.BatchActionButtonGroup
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.MenuGroupCard
import li.songe.gkd.ui.component.MenuItemCheckbox
import li.songe.gkd.ui.component.MenuItemRadioButton
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.RuleGroupCard
import li.songe.gkd.ui.component.ShowGroupState
import li.songe.gkd.ui.component.animateListItem
import li.songe.gkd.ui.component.rememberMultiSelectionState
import li.songe.gkd.ui.component.toGroupState
import li.songe.gkd.ui.component.rememberListScrollState
import li.songe.gkd.ui.icon.BackCloseIcon
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.ui.share.Loadable
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.noRippleClickable
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.iconTextSize
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.LOCAL_SUBS_ID
import li.songe.gkd.util.RuleSortOption
import li.songe.gkd.util.copyText
import li.songe.gkd.util.findOption
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Serializable
data class AppConfigRoute(
    val appId: String,
    val focusLog: ActionLog? = null,
) : NavKey

@Composable
fun AppConfigPage(route: AppConfigRoute) {
    val appId = route.appId
    val focusLog = route.focusLog
    val mainVm = LocalMainViewModel.current
    val vm = viewModel { AppConfigVm(route) }
    val scope = vm.scope

    val store by storeFlow.collectAsStateWithLifecycle()
    val loadableState by vm.uiState.collectAsStateWithLifecycle()
    val state = loadableState.value
    val firstLoading = loadableState is Loadable.Loading
    val loadError = (loadableState as? Loadable.Failure)?.cause
    val globalSubsConfigs = state?.globalSubsConfigs.orEmpty()
    val categoryConfigs = state?.categoryConfigs.orEmpty()
    val appSubsConfigs = state?.appSubsConfigs.orEmpty()
    val subsPairs = state?.subsPairs.orEmpty()
    val groupSize = subsPairs.sumOf { it.second.size }
    val focusGroup = vm.focusGroupFlow?.collectAsStateWithLifecycle()?.value

    val allGroupStates = remember(subsPairs, appId) {
        subsPairs.flatMap { (entry, groups) ->
            groups.map { group -> group.toGroupState(entry.subsItem.id, appId) }
        }.toSet()
    }
    val selectionState = rememberMultiSelectionState<ShowGroupState>()
    val selectedDataSet = selectionState.selectedKeys
    val isSelectedMode = selectionState.active
    LaunchedEffect(allGroupStates) {
        selectionState.retain(allGroupStates)
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
            val changedSize = vm.updateSelectedEnabled(selectedDataSet, enabled)
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
    pageScrollState.ResetOnChange(
        groupSize > 0,
        firstLoading,
    )
    if (focusLog != null) {
        LaunchedEffect(focusGroup, groupSize) {
            if (focusGroup != null && groupSize > 0) {
                val i = subsPairs.run {
                    var j = 0
                    forEach { (entry, groups) ->
                        groups.forEach {
                            if (entry.subsItem.id == focusLog.subsId && it.groupType == focusLog.groupType && it.key == focusLog.groupKey) {
                                return@run j
                            }
                            j++
                        }
                    }
                    -1
                }
                if (i >= 0) {
                    listState.scrollToItem(i)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
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
                val titleModifier = Modifier.noRippleClickable {
                    pageScrollState.resetScroll()
                }
                if (isSelectedMode) {
                    Text(
                        modifier = titleModifier,
                        text = if (selectedDataSet.isNotEmpty()) selectedDataSet.size.toString() else "",
                    )
                } else {
                    AppNameText(
                        modifier = titleModifier,
                        appId = appId
                    )
                }
            }, actions = {
                var expanded by remember { mutableStateOf(false) }
                AnimatedBooleanContent(
                    targetState = isSelectedMode,
                    contentAlignment = Alignment.TopEnd,
                    contentTrue = {
                        Row {
                            PerfIconButton(
                                imageVector = PerfIcon.ContentCopy,
                                enabled = selectedDataSet.any { a -> a.appId != null },
                                onClick = throttle {
                                    scope.launchTry {
                                        copyText(vm.buildSelectedGroupsText(selectedDataSet))
                                    }
                                },
                            )
                            BatchActionButtonGroup(
                                onDisable = { updateSelected(false) },
                                onEnable = { updateSelected(true) },
                                onReset = { updateSelected(null) },
                            )
                            PerfIconButton(imageVector = PerfIcon.MoreVert, onClick = {
                                expanded = true
                            })
                        }
                    },
                    contentFalse = {
                        Row {
                            PerfIconButton(imageVector = PerfIcon.History, onClick = throttle {
                                mainVm.navigatePage(ActionLogRoute(appId = appId))
                            })
                            PerfIconButton(imageVector = PerfIcon.Sort, onClick = {
                                expanded = true
                            })
                        }
                    },
                )
                Box(
                    modifier = Modifier.wrapContentSize(Alignment.TopStart)
                ) {
                    key(isSelectedMode) {
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            if (isSelectedMode) {
                                DropdownMenuItem(
                                    text = {
                                        Text(text = "全选")
                                    },
                                    onClick = {
                                        expanded = false
                                        selectionState.selectAll(allGroupStates)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(text = "反选")
                                    },
                                    onClick = {
                                        expanded = false
                                        selectionState.invert(allGroupStates)
                                    }
                                )
                            } else {
                                MenuGroupCard(inTop = true, title = "排序") {
                                    val handleItem: (RuleSortOption) -> Unit =
                                        throttle(vm::setRuleSortType)
                                    RuleSortOption.objects.forEach { s ->
                                        MenuItemRadioButton(
                                            text = s.label,
                                            selected = RuleSortOption.objects.findOption(store.appRuleSort) == s,
                                            onClick = {
                                                handleItem(s)
                                            },
                                        )
                                    }
                                }
                                MenuGroupCard(title = "筛选") {
                                    MenuItemCheckbox(
                                        text = "未启用",
                                        checked = store.showDisabledRule,
                                        onClick = vm::toggleShowDisabledRule,
                                    )
                                }
                            }
                        }
                    }
                }
            })
        },
        floatingActionButton = {
            AnimationFloatingActionButton(
                visible = !isSelectedMode,
                onClick = {
                    mainVm.navigatePage(
                        UpsertRuleGroupRoute(
                            subsId = LOCAL_SUBS_ID,
                            groupKey = null,
                            appId = appId
                        )
                    )
                },
                imageVector = PerfIcon.Add,
                contentDescription = "添加规则"
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            subsPairs.forEach { (entry, groups) ->
                val subsId = entry.subsItem.id
                stickyHeader(entry.subsItem.id) {
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 8.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable(onClick = throttle {
                                mainVm.navigatePage(
                                    SubsAppGroupListRoute(
                                        subsItemId = subsId,
                                        appId = appId,
                                    )
                                )
                            })
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = entry.subscription.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        PerfIcon(
                            imageVector = PerfIcon.KeyboardArrowRight,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.iconTextSize()
                        )
                    }
                }
                items(groups, { Triple(subsId, it.groupType, it.key) }) { group ->
                    val subsConfig = when (group) {
                        is RawSubscription.RawAppGroup -> appSubsConfigs
                        is RawSubscription.RawGlobalGroup -> globalSubsConfigs
                    }.find { it.subsId == entry.subsItem.id && it.groupKey == group.key }
                    val category = when (group) {
                        is RawSubscription.RawAppGroup -> entry.subscription.getCategory(group.name)
                        is RawSubscription.RawGlobalGroup -> null
                    }
                    val categoryConfig = if (category != null) {
                        categoryConfigs.find { it.subsId == subsId && it.categoryKey == category.key }
                    } else {
                        null
                    }
                    val isSelected = selectedDataSet.any {
                        it.subsId == subsId && it.groupType == group.groupType && it.groupKey == group.key
                    }
                    val onLongClick = {
                        if (groupSize > 1 && !isSelectedMode) {
                            selectionState.selectOnly(
                                group.toGroupState(
                                    subsId = subsId,
                                    appId = appId,
                                ),
                            )
                        }
                    }
                    val onSelectedChange = {
                        selectionState.toggle(
                            group.toGroupState(
                                subsId = subsId,
                                appId = appId,
                            )
                        )
                    }
                    RuleGroupCard(
                        modifier = Modifier.animateListItem(),
                        subs = entry.subscription,
                        appId = appId,
                        group = group,
                        subsConfig = subsConfig,
                        categoryConfig = categoryConfig,
                        onOpen = {
                            mainVm.showRuleGroup(
                                subscriptionId = subsId,
                                appId = appId,
                                group = group,
                            )
                        },
                        onCheckedChange = { enabled ->
                            scope.launchTry(Dispatchers.Default) {
                                vm.setGroupEnabled(entry.subscription, group, subsConfig, enabled)
                            }
                        },
                        onLongClick = onLongClick,
                        isSelectedMode = isSelectedMode,
                        isSelected = isSelected,
                        onSelectedChange = onSelectedChange,
                        focusGroup = focusGroup,
                        onFocusHandled = vm::consumeFocusGroup,
                    )
                }
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (groupSize == 0 && !firstLoading) {
                    EmptyText(
                        text = if (loadError != null) {
                            loadError.message ?: "数据加载失败"
                        } else if (store.showDisabledRule) {
                            "暂无数据"
                        } else {
                            "暂无数据，或修改筛选"
                        }
                    )
                }
            }
        }
    }
}
