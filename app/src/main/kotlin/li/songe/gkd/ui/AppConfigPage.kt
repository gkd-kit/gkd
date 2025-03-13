package li.songe.gkd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.AnimationConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import li.songe.gkd.data.ResolvedAppGroup
import li.songe.gkd.data.ResolvedGroup
import li.songe.gkd.ui.component.AnimationFloatingActionButton
import li.songe.gkd.ui.component.AppNameText
import li.songe.gkd.ui.component.BatchActionButtonGroup
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.RuleGroupCard
import li.songe.gkd.ui.component.ShowGroupState
import li.songe.gkd.ui.component.toGroupState
import li.songe.gkd.ui.icon.BackCloseIcon
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.menuPadding
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.ui.style.titleItemPadding
import li.songe.gkd.util.LOCAL_SUBS_ID
import li.songe.gkd.util.LocalMainViewModel
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.RuleSortOption
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.getUpDownTransform
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.switchItem
import li.songe.gkd.util.throttle

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AppConfigPage(appId: String) {
    val mainVm = LocalMainViewModel.current
    val navController = LocalNavController.current
    val vm = viewModel<AppConfigVm>()
    val ruleSortType by vm.ruleSortTypeFlow.collectAsState()
    val appInfoCache by appInfoCacheFlow.collectAsState()
    val appInfo = appInfoCache[appId]
    val globalGroups by vm.globalGroupsFlow.collectAsState()
    val appGroups by vm.appGroupsFlow.collectAsState()
    val firstLoading by vm.linkLoad.firstLoadingFlow.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()
    var isFirstVisit by remember { mutableStateOf(true) }
    LaunchedEffect(globalGroups.size, appGroups.size, ruleSortType.value) {
        if (isFirstVisit) {
            isFirstVisit = false
        } else {
            listState.scrollToItem(0)
        }
    }
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
                        appInfo = appInfo,
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
                                        vm.selectedDataSetFlow.value =
                                            (globalGroups + appGroups).map { it.toGroupState(appId) }
                                                .toSet()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(text = "反选")
                                    },
                                    onClick = {
                                        expanded = false
                                        val newSelectedIds =
                                            (globalGroups + appGroups).map { it.toGroupState(appId) }
                                                .toSet() - selectedDataSet
                                        vm.selectedDataSetFlow.value = newSelectedIds
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
                                                onClick = {
                                                    storeFlow.update { it.copy(appRuleSortType = s.value) }
                                                }
                                            )
                                        },
                                        onClick = {
                                            storeFlow.update { it.copy(appRuleSortType = s.value) }
                                        },
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
        val onLongClick: (ResolvedGroup) -> Unit = { g ->
            if (globalGroups.size + appGroups.size > 1 && !isSelectedMode) {
                vm.isSelectedModeFlow.value = true
                vm.selectedDataSetFlow.value = setOf(g.toGroupState(appId))
            }
        }
        val onSelectedChange: (ResolvedGroup) -> Unit = { g ->
            vm.selectedDataSetFlow.value = selectedDataSet.switchItem(g.toGroupState(appId))
        }
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState,
        ) {
            itemsIndexed(globalGroups) { i, g ->
                TitleGroupCard(globalGroups, i, isSelectedMode) {
                    RuleGroupCard(
                        subs = g.subscription,
                        appId = appId,
                        group = g.group,
                        subsConfig = g.config,
                        category = null,
                        categoryConfig = null,
                        showBottom = g !== globalGroups.last(),
                        onLongClick = { onLongClick(g) },
                        isSelectedMode = isSelectedMode,
                        isSelected = selectedDataSet.any { it.equalResolvedGroup(g) },
                        onSelectedChange = { onSelectedChange(g) }
                    )
                }
            }
            item {
                if (globalGroups.isNotEmpty() && appGroups.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            itemsIndexed(appGroups) { i, g ->
                TitleGroupCard(appGroups, i, isSelectedMode) {
                    RuleGroupCard(
                        subs = g.subscription,
                        appId = appId,
                        group = g.group,
                        subsConfig = g.config,
                        category = g.category,
                        categoryConfig = g.categoryConfig,
                        showBottom = g !== appGroups.last(),
                        onLongClick = { onLongClick(g) },
                        isSelectedMode = isSelectedMode,
                        isSelected = selectedDataSet.any { it.equalResolvedGroup(g) },
                        onSelectedChange = { onSelectedChange(g) }
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (globalGroups.size + appGroups.size == 0 && !firstLoading) {
                    EmptyText(text = "暂无规则")
                } else {
                    // 避免被 floatingActionButton 遮挡
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }
}

@Composable
private fun LazyItemScope.TitleGroupCard(
    groups: List<ResolvedGroup>,
    i: Int,
    isSelectedMode: Boolean,
    content: @Composable () -> Unit
) {
    val lastGroup = groups.getOrNull(i - 1)
    val g = groups[i]
    if (g.subsItem.id != lastGroup?.subsItem?.id) {
        val navController = LocalNavController.current
        Text(
            text = g.subscription.name,
            modifier = Modifier
                .titleItemPadding(showTop = i > 0)
                .run {
                    if (g is ResolvedAppGroup && !isSelectedMode) {
                        clickable(onClick = throttle {
                            navController.toDestinationsNavigator()
                                .navigate(SubsAppGroupListPageDestination(g.subsItem.id, g.app.id))
                        })
                    } else {
                        this
                    }
                }
                .fillMaxWidth(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        content()
    } else {
        content()
    }
}
