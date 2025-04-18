package li.songe.gkd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.ramcosta.composedestinations.generated.destinations.ActionLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.SubsAppGroupListPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.ui.component.AnimationFloatingActionButton
import li.songe.gkd.ui.component.AppNameText
import li.songe.gkd.ui.component.BatchActionButtonGroup
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.RuleGroupCard
import li.songe.gkd.ui.component.ShowGroupState
import li.songe.gkd.ui.component.animateListItem
import li.songe.gkd.ui.component.toGroupState
import li.songe.gkd.ui.icon.BackCloseIcon
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.menuPadding
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.LIST_PLACEHOLDER_KEY
import li.songe.gkd.util.LOCAL_SUBS_ID
import li.songe.gkd.util.LocalMainViewModel
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.RuleSortOption
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.copyText
import li.songe.gkd.util.getUpDownTransform
import li.songe.gkd.util.json
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.switchItem
import li.songe.gkd.util.throttle
import li.songe.json5.encodeToJson5String
import java.util.Objects

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AppConfigPage(appId: String) {
    val mainVm = LocalMainViewModel.current
    val navController = LocalNavController.current
    val vm = viewModel<AppConfigVm>()
    val ruleSortType by vm.ruleSortTypeFlow.collectAsState()
    val groupSize by vm.groupSizeFlow.collectAsState()
    val firstLoading by vm.linkLoad.firstLoadingFlow.collectAsState()
    val resetKey = Objects.hash(groupSize > 0, ruleSortType.value)
    val scrollBehavior = key(resetKey) { TopAppBarDefaults.enterAlwaysScrollBehavior() }
    val listState = key(resetKey) { rememberLazyListState() }

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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                IconButton(onClick = throttle {
                    if (isSelectedMode) {
                        vm.isSelectedModeFlow.value = false
                    } else {
                        navController.popBackStack()
                    }
                }) {
                    BackCloseIcon(backOrClose = !isSelectedMode)
                }
            }, title = {
                if (isSelectedMode) {
                    Text(
                        text = if (selectedDataSet.isNotEmpty()) selectedDataSet.size.toString() else "",
                    )
                } else {
                    AppNameText(
                        appId = appId
                    )
                }
            }, actions = {
                var expanded by remember { mutableStateOf(false) }
                AnimatedContent(
                    targetState = isSelectedMode,
                    transitionSpec = { getUpDownTransform() },
                    contentAlignment = Alignment.TopEnd,
                ) {
                    Row {
                        if (it) {
                            IconButton(
                                enabled = selectedDataSet.any { it.appId != null },
                                onClick = throttle(vm.viewModelScope.launchAsFn(Dispatchers.Default) {
                                    val selectGroups = mutableListOf<RawSubscription.RawAppGroup>()
                                    vm.subsPairsFlow.value.forEach { (entry, groups) ->
                                        groups.forEach { g ->
                                            if (g is RawSubscription.RawAppGroup && selectedDataSet.any { entry.subsItem.id == it.subsId && g.key == it.groupKey }) {
                                                selectGroups.add(g)
                                            }
                                        }
                                    }
                                    val a = RawSubscription.RawApp(
                                        id = appId,
                                        name = appInfoCacheFlow.value[appId]?.name,
                                        groups = selectGroups,
                                    )
                                    val str = json.encodeToJson5String(a)
                                    copyText(str)
                                })
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = null,
                                    tint = animateColorAsState(LocalContentColor.current).value,
                                )
                            }
                            BatchActionButtonGroup(vm, selectedDataSet)
                            IconButton(onClick = {
                                expanded = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = null,
                                )
                            }
                        } else {
                            IconButton(onClick = throttle {
                                navController.toDestinationsNavigator()
                                    .navigate(ActionLogPageDestination(appId = appId))
                            }) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                )
                            }
                            IconButton(onClick = {
                                expanded = true
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart)
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
                                        vm.selectAll()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(text = "反选")
                                    },
                                    onClick = {
                                        expanded = false
                                        vm.invertSelect()
                                    }
                                )
                            } else {
                                Text(
                                    text = "排序",
                                    modifier = Modifier.menuPadding(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                RuleSortOption.allSubObject.forEach { s ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(s.label)
                                        },
                                        trailingIcon = {
                                            RadioButton(
                                                selected = ruleSortType == s,
                                                onClick = throttle {
                                                    storeFlow.update { it.copy(appRuleSortType = s.value) }
                                                }
                                            )
                                        },
                                        onClick = throttle {
                                            storeFlow.update { it.copy(appRuleSortType = s.value) }
                                        },
                                    )
                                }
                                Text(
                                    text = "筛选",
                                    modifier = Modifier.menuPadding(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text("显示禁用规则")
                                    },
                                    trailingIcon = {
                                        val appShowInnerDisable by vm.appShowInnerDisableFlow.collectAsState()
                                        Checkbox(
                                            checked = appShowInnerDisable,
                                            onCheckedChange = throttle<Boolean> {
                                                storeFlow.update { s -> s.copy(appShowInnerDisable = !s.appShowInnerDisable) }
                                            }
                                        )
                                    },
                                    onClick = throttle {
                                        storeFlow.update { s -> s.copy(appShowInnerDisable = !s.appShowInnerDisable) }
                                    },
                                )
                            }
                        }
                    }
                }
            })
        },
        floatingActionButton = {
            AnimationFloatingActionButton(
                visible = !isSelectedMode,
                onClick = throttle(mainVm.viewModelScope.launchAsFn {
                    navController.toDestinationsNavigator()
                        .navigate(SubsAppGroupListPageDestination(LOCAL_SUBS_ID, appId))
                    delay(AnimationConstants.DefaultDurationMillis + 150L)
                    mainVm.ruleGroupState.editOrAddGroupFlow.value = ShowGroupState(
                        subsId = LOCAL_SUBS_ID,
                        appId = appId,
                        groupKey = null
                    )
                }),
                content = {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                    )
                }
            )
        },
    ) { contentPadding ->
        val globalSubsConfigs by vm.globalSubsConfigsFlow.collectAsState()
        val categoryConfigs by vm.categoryConfigsFlow.collectAsState()
        val appSubsConfigs by vm.appSubsConfigsFlow.collectAsState()
        val subsPairs by vm.subsPairsFlow.collectAsState()
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState,
        ) {
            subsPairs.forEach { (entry, groups) ->
                val subsId = entry.subsItem.id
                stickyHeader(entry.subsItem.id) {
                    Text(
                        text = entry.subscription.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                items(groups, { Triple(subsId, it.groupType, it.key) }) { group ->
                    val subsConfig = when (group) {
                        is RawSubscription.RawAppGroup -> appSubsConfigs
                        is RawSubscription.RawGlobalGroup -> globalSubsConfigs
                    }.find { it.subsId == entry.subsItem.id && it.groupKey == group.key }
                    val category = when (group) {
                        is RawSubscription.RawAppGroup -> entry.subscription.groupToCategoryMap[group]
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
                            vm.isSelectedModeFlow.value = true
                            vm.selectedDataSetFlow.value = setOf(
                                group.toGroupState(
                                    subsId = subsId,
                                    appId = appId,
                                )
                            )
                        }
                    }
                    val onSelectedChange = {
                        vm.selectedDataSetFlow.value =
                            selectedDataSet.switchItem(
                                group.toGroupState(
                                    subsId = subsId,
                                    appId = appId,
                                )
                            )
                    }
                    RuleGroupCard(
                        modifier = Modifier.animateListItem(this),
                        subs = entry.subscription,
                        appId = appId,
                        group = group,
                        subsConfig = subsConfig,
                        category = category,
                        categoryConfig = categoryConfig,
                        showBottom = true,
                        onLongClick = onLongClick,
                        isSelectedMode = isSelectedMode,
                        isSelected = isSelected,
                        onSelectedChange = onSelectedChange
                    )
                }
            }
            item(LIST_PLACEHOLDER_KEY) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (groupSize == 0 && !firstLoading) {
                    EmptyText(text = "暂无规则")
                } else {
                    // 避免被 floatingActionButton 遮挡
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }
}
