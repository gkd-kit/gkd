package li.gkd.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.db.CategoryConfig
import li.gkd.app.data.RawSubscription
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.component.FullscreenDialog
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.PerfTriStateSwitch
import li.gkd.app.ui.component.SubscriptionPageContent
import li.gkd.app.ui.component.TowLineText
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.Loadable
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.getCategoryEnable
import li.gkd.app.util.launchTry
import li.gkd.app.util.throttle
import li.gkd.app.util.toast

@Serializable
data class SubsCategoryRoute(val subsItemId: Long) : NavKey

@Composable
fun SubsCategoryPage(@Suppress("unused") route: SubsCategoryRoute) {
    val mainVm = LocalMainViewModel.current

    val vm = viewModel { SubsCategoryVm(route) }
    SubscriptionPageContent(vm.uiState) { state ->
        val scope = vm.scope
        val showAddCategory by vm.showAddCategoryDialogFlow.collectAsStateWithLifecycle()
        val subs = state.subscription
        val categoryConfigMap = state.categoryConfigMap.value.orEmpty()
        val switchEnabled = state.categoryConfigMap is Loadable.Ready

        val categories = subs.categories

        val pageScrollState = rememberListScrollState()
        val scrollBehavior = pageScrollState.scrollBehavior
        val listState = pageScrollState.listState
        Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                PerfIconButton(
                    imageVector = PerfIcon.ArrowBack,
                    onClick = mainVm::popPage,
                )
            }, title = {
                TowLineText(
                    title = subs.name,
                    subtitle = "规则类别",
                    modifier = Modifier.noRippleClickable(onClick = pageScrollState::resetScroll)
                )
            }, actions = {
                PerfIconButton(
                    imageVector = PerfIcon.Info,
                    onClick = throttle {
                        scope.launchTry {
                            mainVm.dialogRequests.showMessage(
                                title = "类别说明",
                                text = arrayOf(
                                    "类别会捕获以当前类别开头的所有应用规则, 因此可调整类别开关(分类手动配置)来批量开关规则",
                                    "规则开关优先级为:\n规则手动配置 > 分类手动配置 > 分类默认 > 规则默认",
                                    "因此如果手动开关了规则(规则手动配置), 则该规则不会被批量开关, 可通过点击类别-重置规则开关, 来移除类别下所有规则手动配置",
                                ).joinToString("\n\n"),
                            )
                        }
                    },
                )
            })
        }, floatingActionButton = {
            if (subs.isLocal) {
                FloatingActionButton(onClick = { vm.setAddCategoryDialogVisible(true) }) {
                    PerfIcon(
                        imageVector = PerfIcon.Add,
                    )
                }
            }
        }) { contentPadding ->
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(categories, { it.key }) { category ->
                    CategoryItemCard(
                        subs = subs,
                        category = category,
                        categoryConfig = categoryConfigMap[category.key],
                        switchEnabled = switchEnabled,
                        onOpen = {
                            mainVm.navigatePage(
                                SubsCategoryGroupRoute(
                                    subsId = subs.id,
                                    categoryKey = category.key,
                                ),
                            )
                        },
                        onEnabledChange = { enabled ->
                            scope.launchTry {
                                toast(vm.setCategoryEnabled(category, enabled))
                            }
                        },
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

        if (showAddCategory) {
            UpsertCategoryDialog(
                category = null,
                onDismissRequest = { vm.setAddCategoryDialogVisible(false) },
                onSave = { name, description ->
                    scope.launchTry {
                        toast(vm.addCategory(name, description))
                        vm.setAddCategoryDialogVisible(false)
                    }
                },
            )
        }
    }
}

@Composable
private fun CategoryItemCard(
    subs: RawSubscription,
    category: RawSubscription.RawCategory,
    categoryConfig: CategoryConfig?,
    switchEnabled: Boolean,
    onOpen: () -> Unit,
    onEnabledChange: (Boolean?) -> Unit,
) {
    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.padding(
            horizontal = 8.dp,
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .weight(1f)
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                val desc = subs.getCategoryCompatDesc(category.key)
                if (desc != null) {
                    Text(
                        text = desc,
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
            PerfTriStateSwitch(
                modifier = Modifier
                    .pointerInput(Unit) { detectTapGestures { } } // 防止误触边界
                    .padding(8.dp),
                checked = getCategoryEnable(category, categoryConfig),
                enabled = switchEnabled,
                onCheckedChange = throttle(onEnabledChange),
            )
        }
    }
}

@Composable
fun UpsertCategoryDialog(
    category: RawSubscription.RawCategory?,
    onDismissRequest: () -> Unit,
    onSave: (name: String, description: String) -> Unit,
) {
    var nameValue by remember { mutableStateOf(category?.name ?: "") }
    var descValue by remember { mutableStateOf(category?.desc ?: "") }
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
                            onClick = throttle { onSave(nameValue, descValue) },
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
