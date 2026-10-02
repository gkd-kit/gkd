package li.gkd.app.ui.subscription

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_close
import li.gkd.app.resources.action_delete
import li.gkd.app.resources.action_turn_on
import li.gkd.app.resources.categories_delete_confirmation
import li.gkd.app.resources.categories_empty
import li.gkd.app.resources.category_add
import li.gkd.app.resources.category_delete
import li.gkd.app.resources.category_follow_subscription
import li.gkd.app.resources.category_help
import li.gkd.app.resources.category_help_description
import li.gkd.app.resources.category_use_group_default
import li.gkd.app.resources.delete_success
import li.gkd.app.resources.rule_categories
import li.gkd.app.resources.update_success
import li.gkd.app.rule.CategorySetting
import li.gkd.app.ui.component.DialogRequests
import li.gkd.app.ui.component.GkAnimatedFloatingActionButton
import li.gkd.app.ui.component.GkBatchActionMenuItem
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkMultiSelectionActions
import li.gkd.app.ui.component.GkMultiSelectionTopAppBar
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkRuleListItem
import li.gkd.app.ui.component.GkRuleStats
import li.gkd.app.ui.component.GkRuleStatsData
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkSubscriptionPageContent
import li.gkd.app.ui.component.GkTooltipIconButtonBox
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.CategoryEditorRoute
import li.gkd.app.ui.navigation.GkBackHandler
import li.gkd.app.ui.navigation.SubsCategoryGroupRoute
import li.gkd.app.ui.navigation.SubsCategoryRoute
import li.gkd.app.ui.navigation.launchUi
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.style.scaffoldPadding
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubsCategoryPage(
    route: SubsCategoryRoute,
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    showToast: (String) -> Unit,
    dialogs: DialogRequests,
) {
    val vm = viewModel { SubsCategoryViewModel(route) }
    val busy by vm.busyFlow.collectAsStateWithLifecycle()
    val selection = rememberMultiSelectionState<Int>()
    GkBackHandler(selection.active) { selection.clear() }
    GkSubscriptionPageContent(vm.uiState, onBack) { state ->
        val subs = state.subscription
        val scroll = rememberListScrollState()
        val keys =
            remember(state.categories) { state.categories.mapTo(mutableSetOf()) { it.category.key } }
        val selected = selection.selectedKeys intersect keys
        LaunchedEffect(keys) { selection.retain(keys) }
        fun setSelected(setting: CategorySetting) {
            val selectedKeys = selected
            launchUi(vm.scope, showToast) {
                vm.runAction {
                    vm.setSettings(state, selectedKeys, setting)
                    showToast(getString(Res.string.update_success))
                }
            }
        }
        GkScaffold(
            modifier = Modifier.nestedScroll(scroll.scrollBehavior.nestedScrollConnection),
            topBar = {
                GkMultiSelectionTopAppBar(
                    selectedMode = selection.active,
                    selectedCount = selected.size,
                    onExitSelection = selection::clear,
                    onNavigateBack = onBack,
                    onTitleClick = scroll::resetScroll,
                    scrollBehavior = scroll.scrollBehavior,
                    title = {
                        GkTwoLineText(
                            title = subs.name,
                            subtitle = stringResource(Res.string.rule_categories),
                        )
                    },
                    actions = { selectedMode ->
                        if (selectedMode) {
                            GkMultiSelectionActions(selection, keys, enabled = !busy) { dismiss ->
                                GkBatchActionMenuItem(
                                    stringResource(Res.string.category_follow_subscription),
                                    dismiss,
                                    { setSelected(CategorySetting.FollowSubscription) })
                                GkBatchActionMenuItem(
                                    stringResource(Res.string.action_turn_on), dismiss,
                                    { setSelected(CategorySetting.Enabled) })
                                GkBatchActionMenuItem(
                                    stringResource(Res.string.action_close), dismiss,
                                    { setSelected(CategorySetting.Disabled) })
                                GkBatchActionMenuItem(
                                    stringResource(Res.string.category_use_group_default), dismiss,
                                    { setSelected(CategorySetting.GroupDefault) })
                                if (subs.isLocal) {
                                    GkBatchActionMenuItem(
                                        stringResource(Res.string.action_delete),
                                        dismiss,
                                        {
                                            val selectedKeys = selected
                                            launchUi(vm.scope, showToast) {
                                                vm.runAction {
                                                    if (!dialogs.confirm(
                                                            title = getString(Res.string.category_delete),
                                                            text = getString(
                                                                Res.string.categories_delete_confirmation,
                                                                selectedKeys.size.toString()
                                                            ),
                                                            error = true,
                                                        )
                                                    ) return@runAction
                                                    vm.deleteCategories(state, selectedKeys)
                                                    selection.removeDeleted(selectedKeys)
                                                    showToast(getString(Res.string.delete_success))
                                                }
                                            }
                                        })
                                }
                            }
                        } else {
                            GkIconButton(
                                imageVector = GkIcons.Info,
                                contentDescription = stringResource(Res.string.category_help),
                                onClick = {
                                    launchUi(vm.scope, showToast) {
                                        dialogs.showMessage(
                                            title = getString(Res.string.category_help),
                                            text = getString(Res.string.category_help_description),
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
                        onClick = { onNavigate(CategoryEditorRoute(subs.id)) },
                        imageVector = GkIcons.Add,
                        contentDescription = stringResource(Res.string.category_add),
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
                        onOpen = {
                            onNavigate(
                                SubsCategoryGroupRoute(
                                    subs.id,
                                    summary.category.key
                                )
                            )
                        },
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    if (state.categories.isEmpty()) GkEmptyState(stringResource(Res.string.categories_empty))
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
    GkRuleListItem(
        onClick = onOpen,
        selectedMode = selectedMode,
        selected = selected,
        selectionEnabled = selectionEnabled,
        onLongClick = onLongClick,
        onSelect = onSelect,
        trailing = {
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
        Text(
            summary.category.name, style = MaterialTheme.typography.bodyLarge,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        summary.category.desc?.trim()
            ?.takeIf { it.isNotEmpty() && it != summary.category.name.trim() }?.let { description ->
                Text(
                    description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }
        GkRuleStats(
            GkRuleStatsData(
                apps = summary.appCount, appGroups = summary.groupCount,
                enabledAppGroups = summary.enabledGroupCount, showZeroCounts = true
            )
        )
    }
}
