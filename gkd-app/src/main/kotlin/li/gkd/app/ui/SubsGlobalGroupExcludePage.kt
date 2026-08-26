package li.gkd.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.MainActivity
import li.gkd.app.R
import li.gkd.app.a11y.launcherAppId
import li.gkd.app.data.ExcludeData
import li.gkd.app.data.RawSubscription
import li.gkd.app.store.blockMatchAppListFlow
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.component.AnimatedBooleanContent
import li.gkd.app.ui.component.AnimatedIconButton
import li.gkd.app.ui.component.AnimationFloatingActionButton
import li.gkd.app.ui.component.AppBarTextField
import li.gkd.app.ui.component.AppIcon
import li.gkd.app.ui.component.AppNameText
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.component.InnerDisableSwitch
import li.gkd.app.ui.component.MenuGroupCard
import li.gkd.app.ui.component.MenuItemCheckbox
import li.gkd.app.ui.component.MenuItemRadioButton
import li.gkd.app.ui.component.MultiTextField
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfSwitch
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.SubscriptionPageContent
import li.gkd.app.ui.component.TowLineText
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.component.isFullVisible
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.icon.BackCloseIcon
import li.gkd.app.ui.icon.ResetSettings
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.Loadable
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.itemPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.AppGroupOption
import li.gkd.app.util.AppSortOption
import li.gkd.app.util.findOption
import li.gkd.app.util.launchTry
import li.gkd.app.util.systemAppsFlow
import li.gkd.app.util.throttle
import li.gkd.app.util.toast

@Serializable
data class SubsGlobalGroupExcludeRoute(
    val subsItemId: Long,
    val groupKey: Int,
) : NavKey

@Composable
fun SubsGlobalGroupExcludePage(route: SubsGlobalGroupExcludeRoute) {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel { SubsGlobalGroupExcludeVm(route, mainVm) }
    val scope = vm.scope
    SubscriptionPageContent(vm.uiState) { state ->
        val subs = state.subscription
        val group = state.group
        val config = state.config.value
        val excludeData = config?.excludeData ?: ExcludeData.parse(null)
        val configReady = state.config is Loadable.Ready
        val showAppInfos = state.showAppInfos
        val searchStr by vm.searchStrFlow.collectAsStateWithLifecycle()
        val editable by vm.editableFlow.collectAsStateWithLifecycle()
        val excludeText by vm.excludeTextFlow.collectAsStateWithLifecycle()
        val store by storeFlow.collectAsStateWithLifecycle()
        val showAllApps = state.showAllApps
        val blockMatchAppList by blockMatchAppListFlow.collectAsStateWithLifecycle()
        val showSearchBar by vm.showSearchBarFlow.collectAsStateWithLifecycle()
        LaunchedEffect(key1 = showSearchBar, block = {
            if (!showSearchBar) {
                vm.setSearchText("")
            }
        })
        val pageScrollState = rememberListScrollState(canScroll = { !editable })
        val scrollBehavior = pageScrollState.scrollBehavior
        val listState = pageScrollState.listState
        pageScrollState.ResetOnChange(showAppInfos)

        BackHandler(editable, onBack = throttle {
            scope.launchTry {
                context.imeController.requestHide()
                if (vm.hasUnsavedChanges) {
                    if (!mainVm.dialogRequests.confirm(
                        title = "提示",
                        text = "当前内容未保存，是否放弃编辑？",
                    )) return@launchTry
                }
                vm.setEditable(false)
            }
        })

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                PerfTopAppBar(
                    scrollBehavior = scrollBehavior,
                    canScroll = !editable,
                    navigationIcon = {
                        IconButton(onClick = throttle {
                            scope.launchTry {
                                if (editable) {
                                    vm.setEditable(false)
                                    context.imeController.requestHide()
                                } else {
                                    context.imeController.hideAndAwait()
                                    mainVm.popPage()
                                }
                            }
                        }) {
                            BackCloseIcon(backOrClose = !editable)
                        }
                    },
                    title = {
                        if (showSearchBar) {
                            BackHandler {
                                if (!context.imeController.requestHide()) {
                                    vm.setSearchBarVisible(false)
                                }
                            }
                            AppBarTextField(
                                value = searchStr,
                                onValueChange = { newValue ->
                                    vm.setSearchText(newValue.trim())
                                },
                                hint = "请输入应用名称/ID",
                                modifier = Modifier.autoFocus(),
                            )
                        } else {
                            TowLineText(
                                title = group.name,
                                subtitle = "编辑禁用",
                                modifier = Modifier.noRippleClickable(onClick = pageScrollState::resetScroll)
                            )
                        }
                    },
                    actions = {
                        AnimatedBooleanContent(
                            targetState = editable,
                            contentAlignment = Alignment.TopEnd,
                            contentTrue = {
                                PerfIconButton(
                                    imageVector = PerfIcon.Save,
                                    onClick = throttle {
                                        scope.launchTry {
                                            if (vm.saveExcludeText()) {
                                                toast("更新成功")
                                            } else {
                                                toast("未修改")
                                            }
                                            context.imeController.requestHide()
                                            vm.setEditable(false)
                                        }
                                    },
                                )
                            },
                            contentFalse = {
                                Row {
                                    AnimatedIconButton(
                                        onClick = {
                                            if (showSearchBar) {
                                                if (searchStr.isEmpty()) {
                                                    vm.setSearchBarVisible(false)
                                                } else {
                                                    vm.setSearchText("")
                                                }
                                            } else {
                                                vm.setSearchBarVisible(true)
                                            }
                                        },
                                        id = R.drawable.ic_anim_search_close,
                                        atEnd = showSearchBar,
                                    )
                                    var expanded by remember { mutableStateOf(false) }
                                    PerfIconButton(
                                        imageVector = PerfIcon.Sort,
                                        onClick = {
                                            expanded = true
                                        },
                                    )
                                    Box(
                                        modifier = Modifier
                                            .wrapContentSize(Alignment.TopStart)
                                    ) {
                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false }
                                        ) {
                                            MenuGroupCard(inTop = true, title = "排序") {
                                                AppSortOption.objects.forEach { option ->
                                                    MenuItemRadioButton(
                                                        text = option.label,
                                                        selected = AppSortOption.objects.findOption(store.subsExcludeSort) == option,
                                                        onClick = { vm.setSortType(option) }
                                                    )
                                                }
                                            }
                                            MenuGroupCard(title = "分组") {
                                                AppGroupOption.normalObjects.forEach { option ->
                                                    val newValue = option.invert(store.subsExcludeAppGroupType)
                                                    MenuItemCheckbox(
                                                        enabled = newValue != 0,
                                                        text = option.label,
                                                        checked = option.include(store.subsExcludeAppGroupType),
                                                        onClick = { vm.setAppGroupType(newValue) },
                                                    )
                                                }
                                            }
                                            MenuGroupCard(title = "筛选") {
                                                MenuItemCheckbox(
                                                    text = "内置禁用",
                                                    checked = store.subsExcludeShowInnerDisabledApp,
                                                    onClick = vm::toggleShowInnerDisabledApps,
                                                )
                                                MenuItemCheckbox(
                                                    text = "白名单",
                                                    checked = store.subsExcludeShowBlockApp,
                                                    onClick = vm::toggleShowBlockApps,
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    })
            },
            floatingActionButton = {
                AnimationFloatingActionButton(
                    visible = configReady && !editable && scrollBehavior.isFullVisible,
                    onClick = {
                        vm.setEditable(!editable)
                    },
                    imageVector = PerfIcon.Edit,
                    contentDescription = "编辑禁用名单"
                )
            }
        ) { contentPadding ->
            if (editable) {
                MultiTextField(
                    modifier = Modifier.scaffoldPadding(contentPadding),
                    text = excludeText,
                    onTextChange = vm::setExcludeText,
                    immediateFocus = true,
                    placeholderText = tipText,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.scaffoldPadding(contentPadding),
                    state = listState,
                ) {
                    items(showAppInfos, { it.id }) { appInfo ->

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .itemPadding(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppIcon(appId = appInfo.id)
                            Column(
                                modifier = Modifier.weight(1f),
                            ) {
                                AppNameText(appInfo = appInfo)
                                Text(
                                    text = appInfo.id,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val blockMatch = blockMatchAppList.contains(appInfo.id)
                            if (blockMatch) {
                                PerfIcon(
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .size(20.dp),
                                    imageVector = PerfIcon.Block,
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            val checked = getGlobalGroupChecked(
                                subs,
                                excludeData,
                                group,
                                appInfo.id
                            )
                            if (checked != null) {
                                PerfSwitch(
                                    key = appInfo.id,
                                    checked = checked,
                                    enabled = configReady,
                                    onCheckedChange = { newChecked ->
                                        scope.launchTry {
                                            vm.setAppChecked(appInfo.id, newChecked)
                                        }
                                    },
                                    thumbContent = if (excludeData.appIds.contains(appInfo.id)) ({
                                        PerfIcon(
                                            imageVector = ResetSettings,
                                            modifier = Modifier.size(8.dp)
                                        )
                                    }) else null,
                                )
                            } else {
                                InnerDisableSwitch()
                            }
                        }
                    }
                    item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                        Spacer(modifier = Modifier.height(EmptyHeight))
                        if (showAppInfos.isEmpty() && searchStr.isNotEmpty()) {
                            EmptyText(text = if (showAllApps) "暂无搜索结果" else "暂无搜索结果，或修改筛选")
                            Spacer(modifier = Modifier.height(EmptyHeight / 2))
                        }
                    }
                }
            }
        }
    }
}

// null - 内置禁用
// true - 启用
// false - 禁用
fun getGlobalGroupChecked(
    subscription: RawSubscription,
    excludeData: ExcludeData,
    group: RawSubscription.RawGlobalGroup,
    appId: String,
): Boolean? {
    if (subscription.getGlobalGroupInnerDisabled(group, appId)) {
        return null
    }
    excludeData.appIds[appId]?.let { return !it }
    if (group.appIdEnable[appId] == true) return true
    if (appId == launcherAppId) {
        return group.matchLauncher ?: false
    }
    if (systemAppsFlow.value.contains(appId)) {
        return group.matchSystemApp ?: false
    }
    return group.matchAnyApp ?: true
}

private val tipText = """
以换行或英文逗号分割每条禁用
示例1-禁用单个页面
appId/activityId
示例2-禁用整个应用(移除/)
appId
示例3-开启此应用(前置!)
!appId
""".trimIndent()
