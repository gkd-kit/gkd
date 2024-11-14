package li.songe.gkd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.Dispatchers
import li.songe.gkd.MainActivity
import li.songe.gkd.data.CategoryConfig
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.TowLineText
import li.songe.gkd.ui.component.updateDialogOptions
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.itemPadding
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.EnableGroupOption
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.findOption
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast
import li.songe.gkd.util.updateSubscription

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun CategoryPage(subsItemId: Long) {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current

    val vm = viewModel<CategoryVm>()
    val subsItem by vm.subsItemFlow.collectAsState()
    val subsRaw by vm.subsRawFlow.collectAsState()
    val categoryConfigs by vm.categoryConfigsFlow.collectAsState()
    val editable = subsItem != null && subsItemId < 0

    var showAddDlg by remember {
        mutableStateOf(false)
    }
    val (editNameCategory, setEditNameCategory) = remember {
        mutableStateOf<RawSubscription.RawCategory?>(null)
    }

    val categories = subsRaw?.categories ?: emptyList()
    val categoriesGroups = subsRaw?.categoryToGroupsMap ?: emptyMap()
    val categoriesApps = subsRaw?.categoryToAppMap ?: emptyMap()

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
                title = subsRaw?.name ?: subsItemId.toString(),
                subTitle = "规则类别"
            )
        }, actions = {
            IconButton(onClick = throttle {
                context.mainVm.dialogFlow.updateDialogOptions(
                    title = "开关优先级",
                    text = "规则手动配置 > 分类手动配置 > 分类默认 > 规则默认\n\n重置开关: 移除规则手动配置",
                )
            }) {
                Icon(Icons.Outlined.Info, contentDescription = null)
            }
        })
    }, floatingActionButton = {
        if (editable) {
            FloatingActionButton(onClick = { showAddDlg = true }) {
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
                var selectedExpanded by remember { mutableStateOf(false) }
                Row(modifier = Modifier
                    .clickable { selectedExpanded = true }
                    .itemPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    val groups = categoriesGroups[category] ?: emptyList()
                    val size = groups.size
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (size > 0) {
                            val appSize = categoriesApps[category]?.size ?: 0
                            Text(
                                text = "${appSize}应用/${size}规则组",
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
                    var expanded by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier.wrapContentSize(Alignment.TopStart)
                    ) {
                        IconButton(onClick = {
                            expanded = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null,
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(text = {
                                Text(text = "重置开关")
                            }, onClick = {
                                expanded = false
                                vm.viewModelScope.launchTry(Dispatchers.IO) {
                                    val updatedList = DbSet.subsConfigDao.batchResetAppGroupEnable(
                                        subsItemId,
                                        groups
                                    )
                                    if (updatedList.isNotEmpty()) {
                                        toast("成功重置 ${updatedList.size} 规则组开关")
                                    } else {
                                        toast("无可重置规则组")
                                    }
                                }
                            })
                            if (editable) {
                                DropdownMenuItem(text = {
                                    Text(text = "编辑")
                                }, onClick = {
                                    expanded = false
                                    setEditNameCategory(category)
                                })
                                DropdownMenuItem(text = {
                                    Text(text = "删除", color = MaterialTheme.colorScheme.error)
                                }, onClick = {
                                    expanded = false
                                    vm.viewModelScope.launchTry {
                                        context.mainVm.dialogFlow.waitResult(
                                            title = "删除类别",
                                            text = "确定删除 ${category.name} ?",
                                            error = true,
                                        )
                                        subsItem?.apply {
                                            updateSubscription(subsRaw!!.copy(categories = subsRaw!!.categories.filter { c -> c.key != category.key }))
                                            DbSet.subsItemDao.update(copy(mtime = System.currentTimeMillis()))
                                        }
                                        DbSet.categoryConfigDao.deleteByCategoryKey(
                                            subsItemId, category.key
                                        )
                                        toast("删除成功")
                                    }
                                })
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val categoryConfig =
                            categoryConfigs.find { c -> c.categoryKey == category.key }
                        val enable =
                            if (categoryConfig != null) categoryConfig.enable else category.enable
                        Text(
                            text = EnableGroupOption.allSubObject.findOption(enable).label,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Icon(
                            imageVector = Icons.Default.UnfoldMore, contentDescription = null
                        )
                        DropdownMenu(expanded = selectedExpanded,
                            onDismissRequest = { selectedExpanded = false }) {
                            EnableGroupOption.allSubObject.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(text = option.label)
                                    },
                                    onClick = {
                                        selectedExpanded = false
                                        if (option.value != enable) {
                                            vm.viewModelScope.launchTry(Dispatchers.IO) {
                                                DbSet.categoryConfigDao.insert(
                                                    (categoryConfig ?: CategoryConfig(
                                                        enable = option.value,
                                                        subsItemId = subsItemId,
                                                        categoryKey = category.key
                                                    )).copy(enable = option.value)
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (categories.isEmpty()) {
                    EmptyText(text = "暂无类别")
                } else if (editable) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }

    val subsRawVal = subsRaw
    if (editNameCategory != null && subsRawVal != null) {
        var source by remember {
            mutableStateOf(editNameCategory.name)
        }
        AlertDialog(title = { Text(text = "编辑类别") }, text = {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = "请输入类别名称") },
                singleLine = true
            )
        }, onDismissRequest = {
            if (source.isEmpty()) {
                setEditNameCategory(null)
            }
        }, dismissButton = {
            TextButton(onClick = { setEditNameCategory(null) }) {
                Text(text = "取消")
            }
        }, confirmButton = {
            TextButton(enabled = source.isNotBlank() && source != editNameCategory.name, onClick = {
                if (categories.any { c -> c.key != editNameCategory.key && c.name == source }) {
                    toast("不可添加同名类别")
                    return@TextButton
                }
                vm.viewModelScope.launchTry(Dispatchers.IO) {
                    subsItem?.apply {
                        updateSubscription(
                            subsRawVal.copy(categories = categories.toMutableList().apply {
                                val i =
                                    categories.indexOfFirst { c -> c.key == editNameCategory.key }
                                if (i >= 0) {
                                    set(i, editNameCategory.copy(name = source))
                                }
                            })
                        )
                        DbSet.subsItemDao.update(copy(mtime = System.currentTimeMillis()))
                    }
                    toast("修改成功")
                    setEditNameCategory(null)
                }
            }) {
                Text(text = "确认")
            }
        })
    }
    if (showAddDlg && subsRawVal != null) {
        var source by remember {
            mutableStateOf("")
        }
        AlertDialog(title = { Text(text = "添加类别") }, text = {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = "请输入类别名称") },
                singleLine = true
            )
        }, onDismissRequest = {
            if (source.isEmpty()) {
                showAddDlg = false
            }
        }, dismissButton = {
            TextButton(onClick = { showAddDlg = false }) {
                Text(text = "取消")
            }
        }, confirmButton = {
            TextButton(enabled = source.isNotEmpty(), onClick = {
                if (categories.any { c -> c.name == source }) {
                    toast("不可添加同名类别")
                    return@TextButton
                }
                showAddDlg = false
                vm.viewModelScope.launchTry(Dispatchers.IO) {
                    subsItem?.apply {
                        updateSubscription(
                            subsRawVal.copy(categories = categories.toMutableList().apply {
                                add(RawSubscription.RawCategory(key = (categories.maxOfOrNull { c -> c.key }
                                    ?: -1) + 1, name = source, enable = null))
                            })
                        )
                        DbSet.subsItemDao.update(copy(mtime = System.currentTimeMillis()))
                        toast("添加成功")
                    }
                }
            }) {
                Text(text = "确认")
            }
        })
    }
}