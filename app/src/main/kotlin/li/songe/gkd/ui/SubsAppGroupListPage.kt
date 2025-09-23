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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.UpsertRuleGroupPageDestination
import kotlinx.coroutines.Dispatchers
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.AnimationFloatingActionButton
import li.songe.gkd.ui.component.BatchActionButtonGroup
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.RuleGroupCard
import li.songe.gkd.ui.component.TowLineText
import li.songe.gkd.ui.component.animateListItem
import li.songe.gkd.ui.component.toGroupState
import li.songe.gkd.ui.component.useListScrollState
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.icon.BackCloseIcon
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.noRippleClickable
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.ProfileTransitions
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.copyText
import li.songe.gkd.util.getUpDownTransform
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.switchItem
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toJson5String
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubscription

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun SubsAppGroupListPage(
    subsItemId: Long,
    appId: String,
    @Suppress("unused") focusGroupKey: Int? = null, // 背景/边框高亮一下
) {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<SubsAppGroupListVm>()
    val subs = vm.subsFlow.collectAsState().value
    val subsConfigs by vm.subsConfigsFlow.collectAsState()
    val categoryConfigs by vm.categoryConfigsFlow.collectAsState()
    val app by vm.subsAppFlow.collectAsState()

    val groupToCategoryMap = subs.groupToCategoryMap

    val editable = subsItemId < 0
    val isSelectedMode = vm.isSelectedModeFlow.collectAsState().value
    val selectedDataSet = vm.selectedDataSetFlow.collectAsState().value
    LaunchedEffect(key1 = isSelectedMode) {
        if (!isSelectedMode) {
            vm.selectedDataSetFlow.value = emptySet()
        }
    }
    LaunchedEffect(key1 = selectedDataSet.isEmpty()) {
        if (selectedDataSet.isEmpty()) {
            vm.isSelectedModeFlow.value = false
        }
    }
    BackHandler(isSelectedMode) {
        vm.isSelectedModeFlow.value = false
    }
    val resetKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, listState) = useListScrollState(resetKey, app.groups.isEmpty())
    if (focusGroupKey != null) {
        LaunchedEffect(null) {
            if (vm.focusGroupFlow?.value != null) {
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
                    vm.isSelectedModeFlow.value = false
                } else {
                    mainVm.popBackStack()
                }
            }) {
                BackCloseIcon(backOrClose = !isSelectedMode)
            }
        }, title = {
            val titleModifier = Modifier.noRippleClickable { resetKey.intValue++ }
            if (isSelectedMode) {
                Text(
                    modifier = titleModifier,
                    text = if (selectedDataSet.isNotEmpty()) selectedDataSet.size.toString() else "",
                )
            } else {
                TowLineText(
                    modifier = titleModifier,
                    title = subs.name,
                    subtitle = appId,
                    showApp = true,
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
                            onClick = throttle(vm.viewModelScope.launchAsFn(Dispatchers.Default) {
                                val copyGroups = app.groups.filter { g ->
                                    selectedDataSet.any { s -> s.groupKey == g.key }
                                }
                                val str = toJson5String(app.copy(groups = copyGroups))
                                copyText(str)
                            })
                        )
                        BatchActionButtonGroup(vm, selectedDataSet)
                        if (editable) {
                            PerfIconButton(
                                imageVector = PerfIcon.Delete,
                                onClick = throttle(vm.viewModelScope.launchAsFn {
                                    mainVm.dialogFlow.waitResult(
                                        title = "删除规则组",
                                        text = "删除当前所选规则组?",
                                        error = true,
                                    )
                                    val keys = selectedDataSet.mapNotNull { g -> g.groupKey }
                                    vm.isSelectedModeFlow.value = false
                                    if (keys.size == app.groups.size) {
                                        updateSubscription(
                                            subs.copy(
                                                apps = subs.apps.filter { a -> a.id != appId }
                                            )
                                        )
                                        DbSet.subsConfigDao.deleteAppConfig(subsItemId, appId)
                                    } else {
                                        updateSubscription(
                                            subs.copy(
                                                apps = subs.apps.toMutableList().apply {
                                                    set(
                                                        indexOfFirst { a -> a.id == appId },
                                                        app.copy(groups = app.groups.filterNot { g ->
                                                            keys.contains(
                                                                g.key
                                                            )
                                                        })
                                                    )
                                                }
                                            )
                                        )
                                        DbSet.subsConfigDao.batchDeleteAppGroupConfig(
                                            subsItemId,
                                            appId,
                                            keys
                                        )
                                    }
                                    toast("删除成功")
                                })
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
                                vm.selectedDataSetFlow.value = app.groups.map {
                                    it.toGroupState(
                                        subsId = subsItemId,
                                        appId = appId,
                                    )
                                }.toSet()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(text = "反选")
                            },
                            onClick = {
                                expanded = false
                                val newSelectedIds = app.groups.map {
                                    it.toGroupState(
                                        subsId = subsItemId,
                                        appId = appId,
                                    )
                                }.toSet() - selectedDataSet
                                vm.selectedDataSetFlow.value = newSelectedIds
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
                        UpsertRuleGroupPageDestination(
                            subsId = subsItemId,
                            groupKey = null,
                            appId = appId
                        )
                    )
                },
                content = {
                    PerfIcon(
                        imageVector = PerfIcon.Add,
                    )
                }
            )
        }
    }) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(app.groups, { it.key }) { group ->
                val category = groupToCategoryMap[group]
                val subsConfig = subsConfigs.find { it.groupKey == group.key }
                val categoryConfig = categoryConfigs.find {
                    it.categoryKey == category?.key
                }
                RuleGroupCard(
                    modifier = Modifier.animateListItem(this),
                    subs = subs,
                    appId = appId,
                    group = group,
                    category = category,
                    subsConfig = subsConfig,
                    categoryConfig = categoryConfig,
                    focusGroupFlow = vm.focusGroupFlow,
                    isSelectedMode = isSelectedMode,
                    isSelected = selectedDataSet.any { it.groupKey == group.key },
                    onLongClick = {
                        if (app.groups.size > 1) {
                            vm.isSelectedModeFlow.value = true
                            vm.selectedDataSetFlow.value = setOf(
                                group.toGroupState(subsItemId, appId)
                            )
                        }
                    },
                    onSelectedChange = {
                        vm.selectedDataSetFlow.value = selectedDataSet.switchItem(
                            group.toGroupState(
                                subsItemId,
                                appId,
                            )
                        )
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
