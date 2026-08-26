package li.gkd.app.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.ui.component.AppNameText
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.component.MenuGroupCard
import li.gkd.app.ui.component.MenuItemCheckbox
import li.gkd.app.ui.component.MenuItemRadioButton
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.RuleGroupCard
import li.gkd.app.ui.component.SubscriptionPageContent
import li.gkd.app.ui.component.TowLineText
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.icon.ResetSettings
import li.gkd.app.ui.icon.ToggleMid
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.Loadable
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.iconTextSize
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.store.storeFlow
import li.gkd.app.util.AppGroupOption
import li.gkd.app.util.AppSortOption
import li.gkd.app.util.findOption
import li.gkd.app.util.getCategoryEnable
import li.gkd.app.util.launchTry
import li.gkd.app.util.throttle
import li.gkd.app.util.toast

@Serializable
data class SubsCategoryGroupRoute(val subsId: Long, val categoryKey: Int) : NavKey

@Composable
fun SubsCategoryGroupPage(route: SubsCategoryGroupRoute) {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel { SubsCategoryGroupVm(route, mainVm) }
    SubscriptionPageContent(vm.uiState) { state ->
        val store by storeFlow.collectAsStateWithLifecycle()
        val scope = vm.scope
        val showEditCategory by vm.showEditCategoryDialogFlow.collectAsStateWithLifecycle()
        val subs = state.subscription
        val apps = state.apps
        val category = state.category
        val configs = state.configs.value
        val subsConfigs = configs?.subsConfigs.orEmpty()
        val categoryConfig = configs?.categoryConfig
        val switchEnabled = state.configs is Loadable.Ready
        val groupSize = apps.sumOf { it.groups.size }
        val pageScrollState = rememberListScrollState()
        val scrollBehavior = pageScrollState.scrollBehavior
        val listState = pageScrollState.listState
        pageScrollState.ResetOnChange(groupSize)
        Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                PerfIconButton(
                    imageVector = PerfIcon.ArrowBack,
                    onClick = mainVm::popPage,
                )
            }, title = {
                val modifier = Modifier.noRippleClickable(onClick = pageScrollState::resetScroll)
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
                    enabled = switchEnabled,
                    imageVector = when (getCategoryEnable(category, categoryConfig)) {
                        false -> PerfIcon.ToggleOff
                        null -> ToggleMid
                        true -> PerfIcon.ToggleOn
                    },
                    onClick = throttle {
                        scope.launchTry {
                            toast(vm.toggleCategoryEnabled())
                        }
                    },
                )
                val resetAll: () -> Unit = {
                    scope.launchTry {
                        if (!mainVm.dialogRequests.confirm(
                            title = "重置开关",
                            text = "重置当前类别下所有规则开关为默认值？\n重置后规则可由类别批量控制开关",
                        )) return@launchTry
                        val updatedSize = vm.resetAllRuleSwitches()
                        if (updatedSize > 0) {
                            toast("重置 $updatedSize 规则")
                        } else {
                            toast("无可重置规则")
                        }
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
                                    onClick = throttle {
                                        expanded = false
                                        resetAll()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                leadingIcon = { PerfIcon(imageVector = PerfIcon.Edit) },
                                text = { Text(text = "编辑") },
                                onClick = {
                                    expanded = false
                                    vm.setEditCategoryDialogVisible(true)
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { PerfIcon(imageVector = PerfIcon.Delete) },
                                text = { Text(text = "删除") },
                                colors = MenuDefaults.itemColors(
                                    textColor = MaterialTheme.colorScheme.error,
                                    leadingIconColor = MaterialTheme.colorScheme.error,
                                ),
                                onClick = throttle {
                                    expanded = false
                                    scope.launchTry {
                                        if (!mainVm.dialogRequests.confirm(
                                            title = "删除类别",
                                            text = "确定删除 ${category.name} ?",
                                            error = true,
                                        )) return@launchTry
                                        vm.deleteCategory()
                                        toast("删除成功")
                                        mainVm.popPage()
                                    }
                                },
                            )
                        }
                    }
                } else if (!subs.isLocal && groupSize > 0) {
                    PerfIconButton(
                        imageVector = ResetSettings,
                        onClick = throttle(resetAll),
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
                            AppSortOption.objects.forEach { option ->
                                MenuItemRadioButton(
                                    text = option.label,
                                    selected = AppSortOption.objects.findOption(store.subsCategorySort) == option,
                                    onClick = { vm.setSortType(option) },
                                )
                            }
                        }
                        MenuGroupCard(title = "分组") {
                            AppGroupOption.allObjects.forEach { option ->
                                val newValue = option.invert(store.subsCategoryGroupType)
                                MenuItemCheckbox(
                                    enabled = newValue != 0,
                                    text = option.label,
                                    checked = option.include(store.subsCategoryGroupType),
                                    onClick = { vm.setAppGroupType(newValue) },
                                )
                            }
                        }
                        MenuGroupCard(title = "筛选") {
                            MenuItemCheckbox(
                                text = "白名单",
                                checked = store.subsCategoryShowBlock,
                                onClick = vm::toggleShowBlockApps,
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
                        val subsConfig =
                            subsConfigs.find { c -> c.appId == app.id && c.groupKey == group.key }
                        RuleGroupCard(
                            subs = subs,
                            appId = app.id,
                            group = group,
                            subsConfig = subsConfig,
                            categoryConfig = categoryConfig,
                            switchEnabled = switchEnabled,
                            onOpen = {
                                mainVm.showRuleGroup(
                                    subscriptionId = subs.id,
                                    appId = app.id,
                                    group = group,
                                )
                            },
                            onCheckedChange = { enabled ->
                                scope.launchTry {
                                    vm.setGroupEnabled(app.id, group, subsConfig, enabled)
                                }
                            },
                        )
                    }
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    if (apps.isEmpty()) {
                        EmptyText(text = if (state.showAllApps) "暂无数据" else "暂无数据，或修改筛选")
                        Spacer(modifier = Modifier.height(EmptyHeight))
                    }
                }
            }
        }

        if (showEditCategory) {
            UpsertCategoryDialog(
                category = category,
                onDismissRequest = { vm.setEditCategoryDialogVisible(false) },
                onSave = { name, description ->
                    scope.launchTry {
                        toast(vm.updateCategory(name, description))
                        vm.setEditCategoryDialogVisible(false)
                    }
                },
            )
        }
    }
}
