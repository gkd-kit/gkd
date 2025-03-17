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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import li.songe.gkd.appScope
import li.songe.gkd.data.CategoryConfig
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.CardFlagBar
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.TowLineText
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.component.updateDialogOptions
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.icon.ResetSettings
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.EnableGroupOption
import li.songe.gkd.util.LIST_PLACEHOLDER_KEY
import li.songe.gkd.util.LocalMainViewModel
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.findOption
import li.songe.gkd.util.getCategoryEnable
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toToggleableState
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubscription

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun SubsCategoryPage(subsItemId: Long) {
    val mainVm = LocalMainViewModel.current
    val navController = LocalNavController.current

    val vm = viewModel<SubsCategoryVm>()
    val subs = vm.subsRawFlow.collectAsState().value
    val categoryConfigMap = vm.categoryConfigMapFlow.collectAsState().value

    val categories = subs?.categories ?: emptyList()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            IconButton(onClick = {
                navController.popBackStack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                )
            }
        }, title = {
            TowLineText(
                title = subs?.name ?: subsItemId.toString(),
                subtitle = "规则类别"
            )
        }, actions = {
            IconButton(onClick = throttle {
                mainVm.dialogFlow.updateDialogOptions(
                    title = "类别说明",
                    text = arrayOf(
                        "类别会捕获以当前类别开头的所有应用规则组, 因此可调整类别开关(分类手动配置)来批量开关规则组",
                        "规则组开关优先级为:\n规则手动配置 > 分类手动配置 > 分类默认 > 规则默认",
                        "因此如果手动开关了规则组(规则手动配置), 则该规则组不会被批量开关, 可通过点击类别-重置开关, 来移除类别下所有规则手动配置",
                    ).joinToString("\n\n"),
                )
            }) {
                Icon(Icons.Outlined.Info, contentDescription = null)
            }
        })
    }, floatingActionButton = {
        if (subs != null && subs.isLocal) {
            FloatingActionButton(onClick = { vm.showAddCategoryFlow.value = true }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "add",
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
                    subs = subs!!,
                    category = category,
                    categoryConfig = categoryConfigMap[category.key],
                    showBottom = categories.last() != category
                )
            }
            item(LIST_PLACEHOLDER_KEY) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (categories.isEmpty()) {
                    EmptyText(text = "暂无类别")
                } else if (subs != null && subs.isLocal) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }

    val editCategory by vm.editCategoryFlow.collectAsState()
    if (subs != null && editCategory != null) {
        AddOrEditCategoryDialog(
            subs = subs,
            category = editCategory,
        ) {
            vm.editCategoryFlow.value = null
        }
    }
    val showAddCategory by vm.showAddCategoryFlow.collectAsState()
    if (subs != null && showAddCategory) {
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
    showBottom: Boolean,
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
            start = 8.dp,
            end = 8.dp,
            bottom = if (showBottom) 4.dp else 0.dp
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (groups.isNotEmpty()) {
                    val appSize = subs.categoryToAppMap[category]?.size ?: 0
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
                state = EnableGroupOption.allSubObject.findOption(enable).toToggleableState(),
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
            CardFlagBar(visible = enable != null, width = 8.dp)
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
                        Icon(
                            imageVector = ResetSettings,
                            contentDescription = null
                        )
                    },
                    text = { Text(text = "重置开关") },
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
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null
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
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
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
    var value by remember {
        mutableStateOf(category?.name ?: "")
    }
    val onClick = appScope.launchAsFn {
        if (category != null) {
            onDismissRequest()
            updateSubscription(
                subs.copy(categories = subs.categories.toMutableList().apply {
                    set(
                        indexOfFirst { c -> c.key == category.key },
                        category.copy(name = value)
                    )
                })
            )
            toast("更新成功")
        } else {
            if (subs.categories.any { c -> c.name == value }) {
                error("不可添加同名类别")
            }
            onDismissRequest()
            updateSubscription(
                subs.copy(categories = subs.categories.toMutableList().apply {
                    add(RawSubscription.RawCategory(key = (subs.categories.maxOfOrNull { c -> c.key }
                        ?: -1) + 1, name = value, enable = null))
                })
            )
            toast("添加成功")
        }
    }
    AlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(text = if (category == null) "添加类别" else "编辑类别") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.trim() },
                modifier = Modifier.autoFocus(),
                placeholder = { Text(text = "请输入类别名称") },
                singleLine = true
            )
        },
        onDismissRequest = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "取消")
            }
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotEmpty(),
                onClick = throttle(onClick)
            ) {
                Text(text = "确认")
            }
        }
    )

}
