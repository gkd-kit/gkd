package li.gkd.app.feature.subscription

import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.ui.component.GkMultiSelectionTopAppBar
import li.gkd.app.ui.component.GkMultiSelectionActions
import li.gkd.app.ui.component.GkBatchActionMenuItem
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.util.ToastUtils.toast

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import li.gkd.app.domain.rule.CategorySetting
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.component.GkAnimatedFloatingActionButton
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkRuleListItem
import li.gkd.app.ui.component.GkRuleStats
import li.gkd.app.ui.component.GkRuleStatsData
import li.gkd.app.ui.component.GkSubscriptionPageContent
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.GkTooltipIconButtonBox
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.style.scaffoldPadding

@Serializable
data class SubsCategoryRoute(val subsItemId: Long) : NavKey

@Composable
fun SubsCategoryPage(route: SubsCategoryRoute) {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { SubsCategoryVm(route) }
    val busy by vm.busyFlow.collectAsStateWithLifecycle()
    val selection = rememberMultiSelectionState<Int>()
    BackHandler(selection.active) { selection.clear() }
    GkSubscriptionPageContent(vm.uiState) { state ->
        val subs = state.subscription
        val scroll = rememberListScrollState()
        val keys = remember(state.categories) { state.categories.mapTo(mutableSetOf()) { it.category.key } }
        val selected = selection.selectedKeys intersect keys
        LaunchedEffect(keys) { selection.retain(keys) }
        fun setSelected(setting: CategorySetting) {
            val selectedKeys = selected
            vm.scope.launchUi {
                vm.runAction {
                    vm.setSettings(state, selectedKeys, setting)
                    toast(UiStrings.update_success)
                }
            }
        }
        Scaffold(
            modifier = Modifier.nestedScroll(scroll.scrollBehavior.nestedScrollConnection),
            topBar = {
                GkMultiSelectionTopAppBar(
                    selectedMode = selection.active,
                    selectedCount = selected.size,
                    onExitSelection = selection::clear,
                    onNavigateBack = mainVm::popPage,
                    onTitleClick = scroll::resetScroll,
                    scrollBehavior = scroll.scrollBehavior,
                    title = {
                        GkTwoLineText(
                            title = subs.name,
                            subtitle = UiStrings.rule_categories,
                        )
                    },
                    actions = { selectedMode ->
                        if (selectedMode) {
                            GkMultiSelectionActions(selection, keys, enabled = !busy) { dismiss ->
                                GkBatchActionMenuItem(UiStrings.category_follow_subscription, dismiss,
                                    { setSelected(CategorySetting.FollowSubscription) })
                                GkBatchActionMenuItem(UiStrings.action_turn_on, dismiss,
                                    { setSelected(CategorySetting.Enabled) })
                                GkBatchActionMenuItem(UiStrings.action_close, dismiss,
                                    { setSelected(CategorySetting.Disabled) })
                                GkBatchActionMenuItem(UiStrings.category_use_group_default, dismiss,
                                    { setSelected(CategorySetting.GroupDefault) })
                                if (subs.isLocal) {
                                    GkBatchActionMenuItem(UiStrings.action_delete, dismiss, {
                                        val selectedKeys = selected
                                        vm.scope.launchUi {
                                            vm.runAction {
                                                if (!mainVm.dialogRequests.confirm(
                                                    title = UiStrings.category_delete,
                                                    text = UiStrings.categories_delete_confirmation(selectedKeys.size),
                                                    error = true,
                                                )) return@runAction
                                                vm.deleteCategories(state, selectedKeys)
                                                selection.removeDeleted(selectedKeys)
                                                toast(UiStrings.delete_success)
                                            }
                                        }
                                    })
                                }
                            }
                        } else {
                        GkIconButton(imageVector = GkIcons.Info,
                            contentDescription = UiStrings.category_help,
                            onClick = {
                                vm.scope.launchUi {
                                    mainVm.dialogRequests.showMessage(
                                        title = UiStrings.category_help,
                                        text = UiStrings.category_help_description,
                                    )
                                }
                            })
                        }
                    },
                )
            },
            floatingActionButton = {
                if (subs.isLocal) {
                    GkAnimatedFloatingActionButton(
                        visible = !selection.active,
                        onClick = { mainVm.navigatePage(CategoryEditorRoute(subs.id)) },
                        imageVector = GkIcons.Add,
                        contentDescription = UiStrings.category_add,
                    )
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.scaffoldPadding(padding),
                state = scroll.listState,
            ) {
                items(state.categories, key = { it.category.key }) { summary ->
                    CategoryItemCard(
                        summary = summary,
                        selectedMode = selection.active,
                        selected = summary.category.key in selected,
                        selectionEnabled = !busy,
                        onLongClick = { selection.select(summary.category.key) },
                        onSelect = { selection.toggle(summary.category.key) },
                        onOpen = { mainVm.navigatePage(SubsCategoryGroupRoute(subs.id, summary.category.key)) },
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    if (state.categories.isEmpty()) GkEmptyState(UiStrings.categories_empty)
                    GkPageBottomSpace()
                }
            }
        }

    }
}

@Composable
private fun CategoryItemCard(
    summary: CategorySummary,
    selectedMode: Boolean,
    selected: Boolean,
    selectionEnabled: Boolean,
    onLongClick: () -> Unit,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    GkRuleListItem(onClick = onOpen, selectedMode = selectedMode, selected = selected,
        selectionEnabled = selectionEnabled, onLongClick = onLongClick, onSelect = onSelect, trailing = {
        GkTooltipIconButtonBox(contentDescription = summary.setting.label) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                GkIcon(
                    imageVector = summary.setting.icon,
                    modifier = Modifier.size(20.dp),
                    contentDescription = summary.setting.label,
                    tint = if (summary.setting == CategorySetting.FollowSubscription)
                        MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }) {
        Text(summary.category.name, style = MaterialTheme.typography.bodyLarge,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        summary.category.desc?.trim()?.takeIf { it.isNotEmpty() && it != summary.category.name.trim() }?.let { description ->
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
        }
        GkRuleStats(GkRuleStatsData(apps = summary.appCount, appGroups = summary.groupCount,
            enabledAppGroups = summary.enabledGroupCount, showZeroCounts = true))
    }
}
