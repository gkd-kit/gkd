package li.gkd.app.ui.subscription

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.model.showActivityId
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_log_activity_unknown
import li.gkd.app.resources.action_clear
import li.gkd.app.resources.action_log_filter_app
import li.gkd.app.resources.action_log_filter_remove
import li.gkd.app.resources.action_log_filter_selected
import li.gkd.app.resources.action_log_open_app
import li.gkd.app.resources.action_log_title
import li.gkd.app.resources.app_search_hint
import li.gkd.app.resources.apps_no_matches
import li.gkd.app.resources.data_load_failed
import li.gkd.app.resources.loading_progress
import li.gkd.app.resources.search_clear
import li.gkd.app.resources.search_close
import li.gkd.app.resources.search_open
import li.gkd.app.resources.page_exclusion_add_current
import li.gkd.app.resources.page_exclusion_remove
import li.gkd.app.resources.rule_actions
import li.gkd.app.resources.rule_enable
import li.gkd.app.resources.rule_enable_in_app
import li.gkd.app.resources.rule_group_key_description
import li.gkd.app.resources.rule_index_description
import li.gkd.app.resources.rule_key_prefix
import li.gkd.app.resources.rule_missing
import li.gkd.app.resources.rule_view
import li.gkd.app.resources.subscription_id_description
import li.gkd.app.resources.subscription_missing
import li.gkd.app.resources.update_success
import li.gkd.app.resources.version_prefixed
import li.gkd.app.rule.RuleSetting
import li.gkd.app.state.Loadable
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkAppBarTextField
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkGroupNameText
import li.gkd.app.ui.component.GkFilterIconButton
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkLogTimeText
import li.gkd.app.ui.component.GkLogTimeline
import li.gkd.app.ui.component.GkModalBottomSheet
import li.gkd.app.ui.component.GkRuleSettingsContent
import li.gkd.app.ui.component.GkRuleSettingsSheet
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.GkTooltipIconButtonBox
import li.gkd.app.ui.component.GkTwoLineText
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.component.gkLogTimelineRail
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkPageBottomSpaceDefaults
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.component.rememberRuleControlEnvironment
import li.gkd.app.ui.image.GkAppIcon
import li.gkd.app.ui.icon.GkSearchCloseIconButton
import li.gkd.app.ui.navigation.ActionLogRoute
import li.gkd.app.ui.navigation.AppConfigRoute
import li.gkd.app.ui.navigation.SubsAppGroupListRoute
import li.gkd.app.ui.navigation.SubsGlobalGroupListRoute
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.ui.text.getSync
import li.gkd.app.util.ToastUtils
import li.gkd.db.ActionLog
import li.gkd.db.RuleGroupType
import org.jetbrains.compose.resources.stringResource

@Composable
fun ActionLogPage(
    route: ActionLogRoute,
) {
    val mainVm = MainViewModel.requireCurrent()
    val subscriptions by SubscriptionRepository.snapshotFlow.collectAsStateWithLifecycle()
    val subsId = route.subsId
    val appId = route.appId
    val appScoped = subsId == null && appId != null
    val vm = viewModel { ActionLogViewModel(route) }
    val filterAppIds by vm.filterAppIds.collectAsStateWithLifecycle()
    var showAppFilter by remember { mutableStateOf(false) }
    val dialogState by vm.dialogStateFlow.collectAsStateWithLifecycle()
    val scope = vm.scope
    val list = vm.pagingDataFlow.collectAsLazyPagingItems()
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnChange(filterAppIds, list.itemCount > 0)
    GkScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = {
                            mainVm.navigator.pop()
                        },
                    )
                },
                title = {
                    val title = stringResource(Res.string.action_log_title)
                    val titleModifier = Modifier.noRippleClickable {
                        pageScrollState.resetScroll()
                    }
                    if (subsId != null) {
                        GkTwoLineText(
                            title = subscriptions.value?.subscriptions?.get(subsId)?.name
                                ?: subsId.toString(),
                            subtitle = title,
                            modifier = titleModifier,
                        )
                    } else if (appId != null) {
                        GkTwoLineText(
                            title = title,
                            subtitle = appId,
                            showApp = true,
                            appContent = { id, fallback -> GkAppNameText(id, fallback) },
                            modifier = titleModifier,
                        )
                    } else {
                        Text(
                            text = title,
                            modifier = titleModifier,
                        )
                    }
                },
                actions = {
                    if (appId == null) {
                        GkFilterIconButton(
                            filtered = filterAppIds.isNotEmpty(),
                            contentDescription = stringResource(Res.string.action_log_filter_app),
                            onClick = {
                                vm.setFilterQuery("")
                                showAppFilter = true
                            },
                        )
                    }
                },
            )
        },
        content = { contentPadding ->
            Column(Modifier.scaffoldPadding(contentPadding)) {
                AnimatedVisibility(
                    visible = filterAppIds.isNotEmpty(),
                    enter = expandVertically(tween(300), expandFrom = Alignment.Top) + fadeIn(tween(300)),
                    exit = shrinkVertically(tween(300), shrinkTowards = Alignment.Top) + fadeOut(tween(300)),
                ) {
                    val catalog by AppInfoRepository.state.collectAsStateWithLifecycle()
                    LazyRow(
                        // Keep the row's touch-target height while its last chip fades out.
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                            if (filterAppIds.isEmpty()) hideFromAccessibility()
                        },
                        contentPadding = PaddingValues(horizontal = itemHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filterAppIds.toList(), key = { it }) { id ->
                            val name = catalog.snapshot?.apps?.get(id)?.name ?: id
                            InputChip(
                                selected = true,
                                onClick = {
                                    vm.setFilterQuery("")
                                    showAppFilter = true
                                },
                                label = { GkAppNameText(appId = id) },
                                trailingIcon = {
                                    val description = stringResource(Res.string.action_log_filter_remove, name)
                                    GkTooltipIconButtonBox(contentDescription = description) {
                                        IconButton(
                                            modifier = Modifier.size(24.dp),
                                            onClick = { vm.removeFilterApp(id) },
                                        ) {
                                            GkIcon(
                                                imageVector = GkIcons.Close,
                                                modifier = Modifier.size(InputChipDefaults.IconSize),
                                                contentDescription = description,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.widthIn(max = 260.dp).animateListItem(),
                            )
                        }
                    }
                }
                GkLogTimeline(
                    appLabel = { rememberRuleControlEnvironment().apps[it]?.name ?: it },
                    appIcon = { GkAppIcon(it, 24.dp) },
                    appName = { id, modifier ->
                        GkAppNameText(
                            id,
                            modifier = modifier,
                            style = MaterialTheme.typography.titleSmall
                        )
                    },
                    onOpenApp = { mainVm.navigator.navigate(AppConfigRoute(it)) },
                    items = list,
                    listState = listState,
                    key = { it.actionLog.id },
                    appId = { it.actionLog.appId },
                    time = { it.actionLog.ctime },
                    modifier = Modifier.weight(1f),
                    showAppHeaders = !appScoped && filterAppIds.size != 1,
                    contextChanged = { previous, current ->
                        !sameActionLogContext(previous.actionLog, current.actionLog)
                    },
                    contextHeader = { item, previous ->
                        ActionLogContextHeader(
                            item = item,
                            previousLog = previous?.actionLog,
                            includeSubscriptionName = subsId == null,
                        )
                    },
                ) { entry ->
                    ActionLogEntry(
                        modifier = Modifier.animateListItem(),
                        item = entry,
                        onClick = { vm.showActionLog(entry.actionLog) },
                    )
                }
            }
        })

    if (showAppFilter) {
        val apps by vm.filterApps.collectAsStateWithLifecycle()
        val query by vm.filterQuery.collectAsStateWithLifecycle()
        ActionLogAppFilterSheet(
            apps = apps,
            query = query,
            selectedAppIds = filterAppIds,
            onQueryChange = vm::setFilterQuery,
            onToggleApp = vm::toggleFilterApp,
            onClear = vm::clearAppFilter,
            onDismissRequest = { showAppFilter = false },
        )
    }

    dialogState?.let { state ->
        ActionLogDialog(
            state = state,
            onDismissRequest = vm::dismissActionLog,
            showAppContext = appId == null,
            onOpenApp = {
                vm.dismissActionLog()
                mainVm.navigator.navigate(AppConfigRoute(state.actionLog.appId))
            },
            onOpenRule = {
                vm.dismissActionLog()
                val actionLog = state.actionLog
                if (actionLog.groupType == RuleGroupType.App) {
                    mainVm.navigator.navigate(
                        SubsAppGroupListRoute(
                            actionLog.subsId, actionLog.appId, actionLog.groupKey
                        )
                    )
                } else if (actionLog.groupType == RuleGroupType.Global) {
                    mainVm.navigator.navigate(
                        SubsGlobalGroupListRoute(
                            actionLog.subsId, actionLog.groupKey
                        )
                    )
                }
            },
            onSettingChange = { setting ->
                val request = vm.prepareSwitch(state)
                scope.launchUi {
                    vm.applySwitch(request, setting)?.failureMessage()
                        ?.let { ToastUtils.show(it) }
                }
            },
            onToggleActivityExclusion = {
                scope.launchUi {
                    vm.updateActivityExclusion(state)
                    ToastUtils.show(Res.string.update_success.getSync())
                }
            },
        )
    }
}

@Composable
private fun ActionLogAppFilterSheet(
    apps: Loadable<List<ActionLogAppOption>>,
    query: String,
    selectedAppIds: Set<String>,
    onQueryChange: (String) -> Unit,
    onToggleApp: (String) -> Unit,
    onClear: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    var showSearch by remember { mutableStateOf(false) }
    var listHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    fun closeSearch() {
        showSearch = false
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    GkModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 840.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = showSearch,
                    modifier = Modifier.weight(1f).height(48.dp),
                    transitionSpec = { (fadeIn(tween(150)) togetherWith fadeOut(tween(150))).using(null) },
                    contentAlignment = Alignment.CenterStart,
                ) { searching ->
                    val contentModifier = Modifier.fillMaxWidth().semantics {
                        if (searching != showSearch) hideFromAccessibility()
                    }
                    if (searching) {
                        GkAppBarTextField(
                            value = query,
                            onValueChange = {
                                // Ignore text callbacks from the field fading out after search closes.
                                if (showSearch) onQueryChange(it)
                            },
                            hint = stringResource(Res.string.app_search_hint),
                            modifier = contentModifier.autoFocus(immediateFocus = true),
                        )
                    } else {
                        Text(
                            stringResource(Res.string.action_log_filter_app),
                            modifier = contentModifier,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                GkSearchCloseIconButton(
                    isSearchOpen = showSearch,
                    contentDescription = stringResource(
                        if (!showSearch) Res.string.search_open
                        else if (query.isNotEmpty()) Res.string.search_clear
                        else Res.string.search_close,
                    ),
                    onClick = {
                        if (!showSearch) showSearch = true
                        else if (query.isNotEmpty()) onQueryChange("")
                        else closeSearch()
                    },
                )
            }
            AnimatedContent(
                targetState = apps,
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth()
                    .then(if (showSearch) Modifier.height(listHeight) else Modifier)
                    .onSizeChanged {
                        if (!showSearch) listHeight = with(density) { it.height.toDp() }
                    },
                transitionSpec = { (fadeIn(tween(150)) togetherWith fadeOut(tween(150))).using(null) },
            ) { displayedApps ->
                val contentModifier = Modifier.fillMaxWidth().semantics {
                    if (displayedApps != apps) hideFromAccessibility()
                }
                if (displayedApps is Loadable.Ready && displayedApps.value.isEmpty()) {
                    Column(contentModifier) {
                        GkEmptyState(text = stringResource(Res.string.apps_no_matches))
                        GkPageBottomSpace(GkPageBottomSpaceDefaults.CompactHeight)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(84.dp * density.fontScale),
                        modifier = contentModifier,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (displayedApps) {
                            Loadable.Loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(stringResource(Res.string.loading_progress), Modifier.padding(16.dp))
                            }
                            is Loadable.Failure -> item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    stringResource(Res.string.data_load_failed),
                                    Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            is Loadable.Ready -> items(displayedApps.value, key = { it.id }) { app ->
                                ActionLogAppFilterOption(
                                    appId = app.id,
                                    name = app.name,
                                    selected = app.id in selectedAppIds,
                                    onClick = { onToggleApp(app.id) },
                                )
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            GkPageBottomSpace(GkPageBottomSpaceDefaults.CompactHeight)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (selectedAppIds.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.action_log_filter_selected, selectedAppIds.size.toString()),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onClear) {
                        Text(stringResource(Res.string.action_clear))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionLogAppFilterOption(
    appId: String,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .toggleable(selected, role = Role.Checkbox, onValueChange = { onClick() }),
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                GkAppIcon(appId, 32.dp)
                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    softWrap = false, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
            }
            if (selected) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    GkIcon(
                        imageVector = GkIcons.Check,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

private fun sameActionLogContext(first: ActionLog, second: ActionLog): Boolean =
    first.activityId == second.activityId &&
            first.subsId == second.subsId &&
            first.subsVersion == second.subsVersion

@Composable
private fun ActionLogContextHeader(
    item: ActionLogListItem,
    previousLog: ActionLog?,
    includeSubscriptionName: Boolean,
) {
    val activityChanged = previousLog == null || previousLog.activityId != item.actionLog.activityId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .gkLogTimelineRail(MaterialTheme.colorScheme.outlineVariant)
            .padding(
                start = 20.dp, end = itemHorizontalPadding,
                top = if (previousLog == null) 4.dp else 8.dp, bottom = 4.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (activityChanged) {
                GkIcon(
                    imageVector = GkIcons.Layers,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = null,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        ActionLogContextText(
            item = item,
            showActivity = activityChanged,
            showSubscriptionLine = previousLog == null ||
                    previousLog.subsId != item.actionLog.subsId ||
                    previousLog.subsVersion != item.actionLog.subsVersion,
            includeSubscriptionName = includeSubscriptionName,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionLogContextText(
    item: ActionLogListItem,
    showActivity: Boolean,
    showSubscriptionLine: Boolean,
    includeSubscriptionName: Boolean,
    modifier: Modifier = Modifier,
) {
    val actionLog = item.actionLog
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (showActivity) {
            Text(
                text = actionLog.showActivityId
                    ?: stringResource(Res.string.action_log_activity_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        if (showSubscriptionLine) {
            Text(
                text = if (includeSubscriptionName) {
                    listOf(
                        item.subscription?.name ?: stringResource(
                            Res.string.subscription_id_description,
                            actionLog.subsId
                        ),
                        stringResource(Res.string.version_prefixed, actionLog.subsVersion),
                    ).joinToString(" · ")
                } else {
                    stringResource(Res.string.version_prefixed, actionLog.subsVersion)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionLogEntry(
    modifier: Modifier = Modifier,
    item: ActionLogListItem,
    onClick: () -> Unit,
) {
    val (actionLog, group, rule) = item
    val ruleName = rule?.name?.takeIf { it.isNotBlank() } ?: if ((group?.rules?.size ?: 0) > 1) {
        val key = actionLog.ruleKey?.let { stringResource(Res.string.rule_key_prefix, it) } ?: ""
        stringResource(Res.string.rule_index_description, key, actionLog.ruleIndex)
    } else {
        null
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .gkLogTimelineRail(MaterialTheme.colorScheme.outlineVariant)
            .clickable(onClick = onClick)
            .padding(start = 56.dp, end = itemHorizontalPadding, top = 6.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            GkGroupNameText(
                isGlobal = actionLog.groupType == RuleGroupType.Global,
                text = group?.name
                    ?: if (item.subscriptionResolved) stringResource(Res.string.rule_missing)
                    else stringResource(
                        Res.string.rule_group_key_description,
                        actionLog.groupKey.toString()
                    ),
                color = when {
                    group != null -> Color.Unspecified
                    item.subscriptionResolved -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (ruleName != null) {
                Text(
                    text = ruleName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        GkLogTimeText(actionLog.ctime)
    }
}

@Composable
private fun ActionLogDialog(
    state: ActionLogDialogState,
    onDismissRequest: () -> Unit,
    showAppContext: Boolean,
    onOpenApp: () -> Unit,
    onOpenRule: () -> Unit,
    onSettingChange: (RuleSetting) -> Unit,
    onToggleActivityExclusion: () -> Unit,
) {
    val actionLog = state.actionLog

    val environment = rememberRuleControlEnvironment()
    GkRuleSettingsSheet(
        title = state.group?.name ?: stringResource(Res.string.rule_actions),
        subtitle = if (showAppContext) environment.apps[actionLog.appId]?.name
            ?: actionLog.appId else null,
        onDismissRequest = onDismissRequest,
    ) {
        val configs = state.configs
        if (state.loadState is Loadable.Failure) {
            Text(
                stringResource(Res.string.data_load_failed),
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error,
            )
        } else if (state.loadState is Loadable.Loading) {
            Text(stringResource(Res.string.loading_progress), Modifier.padding(16.dp))
        } else if (state.subscription == null || state.group == null) {
            Text(
                stringResource(if (state.subscription == null) Res.string.subscription_missing else Res.string.rule_missing),
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.subscription != null && state.group != null && configs != null) {
            val control =
                environment.resolve(state.subscription, state.group, actionLog.appId, configs)
            GkRuleSettingsContent(
                control, onSettingChange,
                title = if (state.group is RawSubscription.RawGlobalGroup) stringResource(Res.string.rule_enable_in_app) else stringResource(
                    Res.string.rule_enable
                )
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (showAppContext) {
                ItemText(text = stringResource(Res.string.action_log_open_app), onClick = onOpenApp)
            }
            if (state.subscription != null && state.group != null) {
                ItemText(text = stringResource(Res.string.rule_view), onClick = onOpenRule)
            }
            if (actionLog.activityId != null && state.subscription != null && state.group != null && configs != null) {
                ItemText(
                    text = if (state.activityDisabled) stringResource(Res.string.page_exclusion_remove) else stringResource(
                        Res.string.page_exclusion_add_current
                    ),
                    onClick = onToggleActivityExclusion,
                )
            }
        }
    }
}

@Composable
private fun ItemText(
    text: String,
    color: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val modifier = Modifier
        .clickable(onClick = onClick)
        .fillMaxWidth()
        .padding(16.dp)
    Text(
        modifier = modifier,
        text = text,
        color = color,
    )
}
