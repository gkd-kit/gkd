package li.gkd.app.ui.subscription

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import li.gkd.app.ui.component.gkTopAppBarScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import li.gkd.app.resources.*
import li.gkd.app.rule.CategorySetting
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkAlertDialog
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
import li.gkd.app.ui.navigation.CategoryEditorRoute
import li.gkd.app.ui.navigation.SubsCategoryGroupRoute
import li.gkd.app.ui.navigation.SubsCategoryRoute
import li.gkd.app.ui.platform.GkBackHandler
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.ToastUtils
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubsCategoryPage(
    route: SubsCategoryRoute,
) {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel { SubsCategoryViewModel(route) }
    var showHelp by remember { mutableStateOf(false) }
    if (showHelp) CategoryHelpDialog { showHelp = false }
    val busy by vm.busyFlow.collectAsStateWithLifecycle()
    val selection = vm.selection
    val selectionSnapshot by selection.state.collectAsStateWithLifecycle()
    GkBackHandler(selectionSnapshot.active) { selection.clear() }
    GkSubscriptionPageContent(vm.uiState, mainVm.navigator::pop) { state ->
        val subs = state.subscription
        val scroll = rememberListScrollState()
        scroll.ResetOnListChange(
            state.categories,
            key = { it.category.key },
            leadingItemKey = if (state.uncategorizedApps.isNotEmpty()) "uncategorized" else null,
        )
        val keys =
            remember(state.categories) { state.categories.mapTo(mutableSetOf()) { it.category.key } }
        val selected = selectionSnapshot.selectedKeys intersect keys
        LaunchedEffect(keys) { selection.retain(keys) }
        fun setSelected(setting: CategorySetting) {
            val selectedKeys = selected
            vm.scope.launchUi {
                vm.runAction {
                    vm.setSettings(state, selectedKeys, setting)
                    ToastUtils.show(getString(Res.string.update_success))
                }
            }
        }
        GkScaffold(
            modifier = Modifier.gkTopAppBarScroll(scroll.scrollBehavior),
            topBar = {
                GkMultiSelectionTopAppBar(
                    selectedMode = selectionSnapshot.active,
                    selectedCount = selected.size,
                    onExitSelection = selection::clear,
                    onNavigateBack = mainVm.navigator::pop,
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
                            GkMultiSelectionActions(selectionSnapshot.selectedKeys, keys, selection::selectAll, selection::invert, enabled = !busy) { dismiss ->
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
                                            vm.scope.launchUi {
                                                vm.runAction {
                                                    if (!mainVm.dialogRequests.confirm(
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
                                                    ToastUtils.show(getString(Res.string.delete_success))
                                                }
                                            }
                                        })
                                }
                            }
                        } else {
                            GkIconButton(
                                imageVector = GkIcons.Info,
                                contentDescription = stringResource(Res.string.category_help),
                                onClick = { showHelp = true })
                        }
                    },
                )
            },
            floatingActionButton = {
                if (subs.isLocal) {
                    GkAnimatedFloatingActionButton(
                        visible = !selectionSnapshot.active,
                        onClick = { mainVm.navigator.navigate(CategoryEditorRoute(subs.id)) },
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
                if (state.uncategorizedApps.isNotEmpty()) {
                    item("uncategorized") {
                        GkRuleListItem(
                            onClick = { mainVm.navigator.navigate(SubsCategoryGroupRoute(subs.id, null)) },
                            selectable = false,
                            trailingInfo = {
                                GkRuleStats(
                                    GkRuleStatsData(
                                        apps = state.uncategorizedApps.size,
                                        appGroups = state.uncategorizedApps.sumOf { it.groups.size },
                                        enabledAppGroups = state.uncategorizedEnabledGroupCount,
                                    )
                                )
                            },
                        ) {
                            Text(
                                stringResource(Res.string.rule_category_uncategorized),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
                items(state.categories, key = { it.category.key }) { summary ->
                    CategoryItemCard(
                        summary = summary,
                        selectedMode = selectionSnapshot.active,
                        selected = summary.category.key in selected,
                        selectionEnabled = !busy,
                        onLongClick = { selection.select(summary.category.key) },
                        onSelect = { selection.toggle(summary.category.key) },
                        onOpen = {
                            mainVm.navigator.navigate(
                                SubsCategoryGroupRoute(
                                    subs.id,
                                    summary.category.key
                                )
                            )
                        },
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    if (state.categories.isEmpty() && state.uncategorizedApps.isEmpty()) GkEmptyState(stringResource(Res.string.categories_empty))
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
            val description = categorySettingDescription(summary.setting, summary.category.enable)
            GkTooltipIconButtonBox(contentDescription = description) {
                CategorySettingIcons(
                    setting = summary.setting,
                    subscriptionDefault = summary.category.enable,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clearAndSetSemantics { contentDescription = description },
                )
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

@Composable
private fun categorySettingDescription(setting: CategorySetting, subscriptionDefault: Boolean?): String =
    stringResource(
        if (setting == CategorySetting.FollowSubscription) {
            when (subscriptionDefault) {
                true -> Res.string.category_follow_subscription_enabled
                false -> Res.string.category_follow_subscription_disabled
                null -> Res.string.category_follow_subscription_unspecified
            }
        } else setting.descriptionResource
    )

@Composable
private fun CategorySettingIcons(
    setting: CategorySetting,
    subscriptionDefault: Boolean?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GkIcon(
            imageVector = setting.icon,
            modifier = Modifier.size(20.dp),
            contentDescription = null,
            tint = if (setting == CategorySetting.Enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (setting == CategorySetting.FollowSubscription && subscriptionDefault != null) {
            GkIcon(
                imageVector = if (subscriptionDefault) GkIcons.CheckCircle else GkIcons.RemoveCircleOutline,
                modifier = Modifier.size(20.dp),
                contentDescription = null,
                tint = if (subscriptionDefault) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryHelpDialog(onDismissRequest: () -> Unit) {
    GkAlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.category_help)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(Res.string.category_icon_legend),
                    style = MaterialTheme.typography.titleSmall,
                )
                CategoryHelpItem(CategorySetting.FollowSubscription, true)
                CategoryHelpItem(CategorySetting.FollowSubscription, false)
                CategoryHelpItem(CategorySetting.FollowSubscription, null)
                CategoryHelpItem(CategorySetting.Enabled)
                CategoryHelpItem(CategorySetting.Disabled)
                CategoryHelpItem(CategorySetting.GroupDefault)
                Text(stringResource(Res.string.category_help_description))
                Text(
                    stringResource(Res.string.category_help_state_note),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.action_understood))
            }
        },
    )
}

@Composable
private fun CategoryHelpItem(setting: CategorySetting, subscriptionDefault: Boolean? = null) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategorySettingIcons(setting, subscriptionDefault)
        Text(categorySettingDescription(setting, subscriptionDefault), Modifier.weight(1f))
    }
}
