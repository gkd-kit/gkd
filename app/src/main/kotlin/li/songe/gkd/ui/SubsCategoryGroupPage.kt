package li.songe.gkd.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import li.songe.gkd.data.CategoryConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.AppNameText
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.MenuGroupCard
import li.songe.gkd.ui.component.MenuItemCheckbox
import li.songe.gkd.ui.component.MenuItemRadioButton
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.RuleGroupCard
import li.songe.gkd.ui.component.TowLineText
import li.songe.gkd.ui.component.useListScrollState
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.icon.ResetSettings
import li.songe.gkd.ui.icon.ToggleMid
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.share.asMutableState
import li.songe.gkd.ui.share.noRippleClickable
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.iconTextSize
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.AppGroupOption
import li.songe.gkd.util.AppSortOption
import li.songe.gkd.util.EnableGroupOption
import li.songe.gkd.util.findOption
import li.songe.gkd.util.getCategoryEnable
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubscription

@Serializable
data class SubsCategoryGroupRoute(val subsId: Long, val categoryKey: Int) : NavKey

@Composable
fun SubsCategoryGroupPage(route: SubsCategoryGroupRoute) {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel { SubsCategoryGroupVm(route) }
    val subs = vm.subsFlow.collectAsState().value
    val apps = vm.appsFlow.collectAsState().value
    val category = vm.categoryFlow.collectAsState().value
    val subsConfigs = vm.subsConfigsFlow.collectAsState().value
    val categoryConfig = vm.categoryConfigFlow.collectAsState().value
    val scrollKey = rememberSaveable { mutableIntStateOf(0) }
    val groupSize = apps.sumOf { it.groups.size }
    val (scrollBehavior, listState) = useListScrollState(scrollKey, groupSize)
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            PerfIconButton(
                imageVector = PerfIcon.ArrowBack,
                onClick = mainVm::popPage,
            )
        }, title = {
            val modifier = Modifier.noRippleClickable(onClick = { scrollKey.intValue++ })
            val desc = category.desc
            if (desc != null) {
                TowLineText(
                    title = category.name,
                    subtitle = desc,
                    modifier = modifier,
                )
            } else {
                TowLineText(
                    title = subs.name,
                    subtitle = category.name,
                    modifier = modifier,
                )
            }
        }, actions = {
            PerfIconButton(
                imageVector = when (getCategoryEnable(category, categoryConfig)) {
                    false -> PerfIcon.ToggleOff
                    null -> ToggleMid
                    true -> PerfIcon.ToggleOn
                },
                onClick = throttle(
                    vm.viewModelScope.launchAsFn {
                        val newValue = when (getCategoryEnable(category, categoryConfig)) {
                            false -> null
                            null -> true
                            true -> false
                        }
                        val option = EnableGroupOption.objects.findOption(newValue)
                        DbSet.categoryConfigDao.insert(
                            (categoryConfig ?: CategoryConfig(
                                enable = option.value,
                                subsId = subs.id,
                                categoryKey = category.key
                            )).copy(enable = option.value)
                        )
                        toast(option.label)
                    },
                ),
            )
            val resetAll = suspend {
                mainVm.dialogFlow.waitResult(
                    title = "重置开关",
                    text = "重置当前类别下所有规则开关为默认值？\n重置后规则可由类别批量控制开关",
                )
                val updatedList = DbSet.subsConfigDao.batchResetAppGroupEnable(
                    subs.id,
                    apps.flatMap { a -> a.groups }
                        .map { g -> g to subs.getAppByGroup(g) },
                )
                if (updatedList.isNotEmpty()) {
                    toast("重置 ${updatedList.size} 规则")
                } else {
                    toast("无可重置规则")
                }
            }
            if (subs.isLocal) {
                var expanded by remember { mutableStateOf(false) }
                PerfIconButton(imageVector = PerfIcon.MoreVert, onClick = { expanded = true })
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart)
                ) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        if (groupSize > 0) {
                            DropdownMenuItem(
                                leadingIcon = { PerfIcon(imageVector = ResetSettings) },
                                text = { Text(text = "重置") },
                                onClick = throttle(vm.viewModelScope.launchAsFn {
                                    expanded = false
                                    resetAll()
                                })
                            )
                        }
                        DropdownMenuItem(
                            leadingIcon = { PerfIcon(imageVector = PerfIcon.Edit) },
                            text = { Text(text = "编辑") },
                            onClick = {
                                expanded = false
                                vm.showEditCategoryFlow.value = true
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { PerfIcon(imageVector = PerfIcon.Delete) },
                            text = { Text(text = "删除") },
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.error,
                                leadingIconColor = MaterialTheme.colorScheme.error,
                            ),
                            onClick = throttle(mainVm.viewModelScope.launchAsFn {
                                expanded = false
                                mainVm.dialogFlow.waitResult(
                                    title = "删除类别",
                                    text = "确定删除 ${category.name} ?",
                                    error = true,
                                )
                                mainVm.popPage()
                                delay(500)
                                updateSubscription(
                                    subs.copy(categories = subs.categories.toMutableList().apply {
                                        removeIf { it.key == category.key }
                                    })
                                )
                                DbSet.categoryConfigDao.deleteByCategoryKey(
                                    subs.id,
                                    category.key
                                )
                                toast("删除成功")
                            })
                        )
                    }
                }
            } else if (!subs.isLocal && groupSize > 0) {
                PerfIconButton(
                    imageVector = ResetSettings,
                    onClick = throttle(vm.viewModelScope.launchAsFn { resetAll() }),
                )
            }
            var sortExpanded by remember { mutableStateOf(false) }
            PerfIconButton(
                imageVector = PerfIcon.Sort,
                onClick = {
                    sortExpanded = true
                },
            )
            Box(
                modifier = Modifier.wrapContentSize(Alignment.TopStart)
            ) {
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
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
                    MenuGroupCard(title = "分组") {
                        var appGroupType by vm.appGroupTypeFlow.asMutableState()
                        AppGroupOption.allObjects.forEach { option ->
                            val newValue = option.invert(appGroupType)
                            MenuItemCheckbox(
                                enabled = newValue != 0,
                                text = option.label,
                                checked = option.include(appGroupType),
                                onClick = { appGroupType = newValue },
                            )
                        }
                    }
                    MenuGroupCard(title = "筛选") {
                        MenuItemCheckbox(
                            text = "白名单",
                            stateFlow = vm.showBlockAppFlow,
                        )
                    }
                }
            }
        })
    }) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            apps.forEach { app ->
                stickyHeader(app.id) {
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 8.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable(onClick = throttle {
                                mainVm.navigatePage(
                                    SubsAppGroupListRoute(
                                        subsItemId = subs.id,
                                        appId = app.id,
                                    )
                                )
                            })
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppNameText(
                            modifier = Modifier.weight(1f),
                            appId = app.id,
                            fallbackName = app.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        PerfIcon(
                            imageVector = PerfIcon.KeyboardArrowRight,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.iconTextSize(),
                        )
                    }
                }
                items(app.groups, { app.id to it.key }) { group ->
                    RuleGroupCard(
                        subs = subs,
                        appId = app.id,
                        group = group,
                        subsConfig = subsConfigs.find { c -> c.appId == app.id && c.groupKey == group.key },
                        categoryConfig = categoryConfig,
                    )
                }
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (apps.isEmpty()) {
                    EmptyText(text = if (vm.showAllAppFlow.collectAsState().value) "暂无数据" else "暂无数据，或修改筛选")
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }

    if (vm.showEditCategoryFlow.collectAsState().value) {
        UpsertCategoryDialog(
            subs = subs,
            category = category,
        ) {
            vm.showEditCategoryFlow.value = false
        }
    }
}
