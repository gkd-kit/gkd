package li.songe.gkd.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.songe.gkd.appScope
import li.songe.gkd.data.CategoryConfig
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.FullscreenDialog
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.TowLineText
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.component.updateDialogOptions
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.icon.ResetSettings
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.EnableGroupOption
import li.songe.gkd.util.findOption
import li.songe.gkd.util.getCategoryEnable
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toToggleableState
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubscription

@Serializable
data class SubsCategoryRoute(val subsItemId: Long) : NavKey

@Composable
fun SubsCategoryPage(@Suppress("unused") route: SubsCategoryRoute) {
    val mainVm = LocalMainViewModel.current

    val vm = viewModel { SubsCategoryVm(route) }
    val subs = vm.subsRawFlow.collectAsState().value
    val categoryConfigMap = vm.categoryConfigMapFlow.collectAsState().value

    val categories = subs.categories

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            PerfIconButton(
                imageVector = PerfIcon.ArrowBack,
                onClick = {
                    mainVm.popPage()
                },
            )
        }, title = {
            TowLineText(
                title = subs.name,
                subtitle = "规则类别"
            )
        }, actions = {
            PerfIconButton(imageVector = PerfIcon.Info, onClick = throttle {
                mainVm.dialogFlow.updateDialogOptions(
                    title = "类别说明",
                    text = arrayOf(
                        "类别会捕获以当前类别开头的所有应用规则组, 因此可调整类别开关(分类手动配置)来批量开关规则组",
                        "规则组开关优先级为:\n规则手动配置 > 分类手动配置 > 分类默认 > 规则默认",
                        "因此如果手动开关了规则组(规则手动配置), 则该规则组不会被批量开关, 可通过点击类别-重置规则组开关, 来移除类别下所有规则手动配置",
                    ).joinToString("\n\n"),
                )
            })
        })
    }, floatingActionButton = {
        if (subs.isLocal) {
            FloatingActionButton(onClick = { vm.showAddCategoryFlow.value = true }) {
                PerfIcon(
                    imageVector = PerfIcon.Add,
                )
            }
        }
    }) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding)
        ) {
            items(categories, { it.key }) { category ->
                CategoryItemCard(
                    vm = vm,
                    subs = subs,
                    category = category,
                    categoryConfig = categoryConfigMap[category.key],
                )
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (categories.isEmpty()) {
                    EmptyText(text = "暂无类别")
                }
            }
        }
    }

    val editCategory by vm.editCategoryFlow.collectAsState()
    if (editCategory != null) {
        AddOrEditCategoryDialog(
            subs = subs,
            category = editCategory,
        ) {
            vm.editCategoryFlow.value = null
        }
    }
    val showAddCategory by vm.showAddCategoryFlow.collectAsState()
    if (showAddCategory) {
        AddOrEditCategoryDialog(
            subs = subs,
            category = null,
        ) {
            vm.showAddCategoryFlow.value = false
        }
    }
}

@Composable
private fun CategoryItemCard(
    vm: SubsCategoryVm,
    subs: RawSubscription,
    category: RawSubscription.RawCategory,
    categoryConfig: CategoryConfig?,
) {
    val groups = subs.categoryToGroupsMap[category] ?: emptyList()
    var expanded by remember { mutableStateOf(false) }
    val onClick = {
        if (groups.isNotEmpty() || subs.isLocal) {
            expanded = true
        }
    }
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.padding(
            horizontal = 8.dp,
            vertical = 2.dp,
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!category.desc.isNullOrBlank())
                    Text(
                        text = category.desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                else if (groups.isNotEmpty()) {
                    val appSize = subs.getCategoryApps(category.key).size
                    Text(
                        text = "${appSize}应用/${groups.size}规则组",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "暂无规则",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            CategoryMenu(
                vm = vm,
                subs = subs,
                category = category,
                expanded = expanded,
                onCheckedChange = { expanded = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            val enable = getCategoryEnable(category, categoryConfig)
            TriStateCheckbox(
                state = EnableGroupOption.objects.findOption(enable).toToggleableState(),
                onClick = throttle(appScope.launchAsFn {
                    val option = when (enable) {
                        false -> EnableGroupOption.FollowSubs
                        null -> EnableGroupOption.AllEnable
                        true -> EnableGroupOption.AllDisable
                    }
                    DbSet.categoryConfigDao.insert(
                        (categoryConfig ?: CategoryConfig(
                            enable = option.value,
                            subsId = subs.id,
                            categoryKey = category.key
                        )).copy(enable = option.value)
                    )
                    toast(option.label)
                })
            )
        }
    }
}

@Composable
private fun CategoryMenu(
    vm: SubsCategoryVm,
    subs: RawSubscription,
    category: RawSubscription.RawCategory,
    expanded: Boolean,
    onCheckedChange: ((Boolean) -> Unit),
) {
    val mainVm = LocalMainViewModel.current
    val groups = subs.categoryToGroupsMap[category] ?: emptyList()
    Box(
        modifier = Modifier.wrapContentSize(Alignment.TopStart)
    ) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onCheckedChange(false) }
        ) {
            if (groups.isNotEmpty()) {
                DropdownMenuItem(
                    leadingIcon = {
                        PerfIcon(
                            imageVector = ResetSettings,
                        )
                    },
                    text = { Text(text = "重置规则组开关") },
                    onClick = throttle(vm.viewModelScope.launchAsFn {
                        onCheckedChange(false)
                        val updatedList = DbSet.subsConfigDao.batchResetAppGroupEnable(
                            subs.id,
                            subs.categoryToGroupsMap[category] ?: emptyList(),
                        )
                        if (updatedList.isNotEmpty()) {
                            toast("成功重置 ${updatedList.size} 规则组开关")
                        } else {
                            toast("无可重置规则组")
                        }
                    })
                )
            }
            if (subs.isLocal) {
                DropdownMenuItem(
                    leadingIcon = {
                        PerfIcon(
                            imageVector = PerfIcon.Edit,
                        )
                    },
                    text = { Text(text = "编辑") },
                    onClick = {
                        onCheckedChange(false)
                        vm.editCategoryFlow.value = category
                    }
                )
                DropdownMenuItem(
                    text = { Text(text = "删除", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        PerfIcon(
                            imageVector = PerfIcon.Delete,
                        )
                    },
                    onClick = throttle(vm.viewModelScope.launchAsFn {
                        onCheckedChange(false)
                        mainVm.dialogFlow.waitResult(
                            title = "删除类别",
                            text = "确定删除 ${category.name} ?",
                            error = true,
                        )
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
    }
}

@Composable
private fun AddOrEditCategoryDialog(
    subs: RawSubscription,
    category: RawSubscription.RawCategory?,
    onDismissRequest: () -> Unit,
) {
    var nameValue by remember { mutableStateOf(category?.name ?: "") }
    var descValue by remember { mutableStateOf(category?.desc ?: "") }
    val onClick = appScope.launchAsFn {
        if (category != null) {
            onDismissRequest()
            val changed = category.name != nameValue || (category.desc ?: "") != descValue
            if (changed) {
                updateSubscription(
                    subs.copy(categories = subs.categories.toMutableList().apply {
                        set(
                            indexOfFirst { c -> c.key == category.key },
                            category.copy(name = nameValue, desc = descValue)
                        )
                    })
                )
                toast("更新成功")
            } else {
                toast("未修改")
            }
        } else {
            if (subs.categories.any { c -> c.name == nameValue }) {
                error("不可添加同名类别")
            }
            onDismissRequest()
            updateSubscription(
                subs.copy(categories = subs.categories.toMutableList().apply {
                    val c = RawSubscription.RawCategory(
                        key = (subs.categories.maxOfOrNull { c -> c.key } ?: -1) + 1,
                        enable = null,
                        name = nameValue,
                        desc = descValue,
                    )
                    add(c)
                })
            )
            toast("添加成功")
        }
    }
    FullscreenDialog(onDismissRequest = onDismissRequest) {
        Scaffold(
            topBar = {
                PerfTopAppBar(
                    navigationIcon = {
                        PerfIconButton(
                            imageVector = PerfIcon.Close,
                            onClick = throttle(onDismissRequest),
                        )
                    },
                    title = { Text(text = if (category == null) "添加类别" else "编辑类别") },
                    actions = {
                        PerfIconButton(
                            imageVector = PerfIcon.Save,
                            enabled = nameValue.isNotEmpty(),
                            onClick = throttle(onClick),
                        )
                    }
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
            ) {
                OutlinedTextField(
                    label = { Text("类别名称") },
                    value = nameValue,
                    onValueChange = { nameValue = it.trim() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .autoFocus(),
                    placeholder = { Text(text = "请输入类别名称") },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    label = { Text("类别描述") },
                    value = descValue,
                    onValueChange = { descValue = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = "请输入类别描述") },
                    singleLine = true,
                )
            }
        }
    }
}
