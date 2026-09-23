package li.gkd.app.feature.subscription

import li.gkd.app.ui.component.GkPageBottomSpaceDefaults
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.MainViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import li.gkd.app.text.UiStrings
import li.gkd.app.META
import li.gkd.app.core.state.Loadable
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.mtimeStr
import li.gkd.app.data.subscription.SubscriptionRepository
import li.gkd.app.feature.log.ActionLogRoute
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.db.LOCAL_SUBS_ID
import li.gkd.db.SubsItem
import li.gkd.app.ui.share.DeletionTarget
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.share.message
import li.gkd.app.data.subscription.SubscriptionState
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.util.TimeUtils.formatTimeAgo
import li.gkd.app.util.ToastUtils.toast
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkModalBottomSheet
import li.gkd.app.ui.component.GkRetainedSheet
import li.gkd.app.ui.component.SheetRequest

private data class SubsSheetSnapshot(
    val item: SubsItem,
    val subscription: RawSubscription?,
    val loading: Boolean,
)

class SubsSheetState {
    private val subsIdFlow = MutableStateFlow<SheetRequest<Long>?>(null)

    fun show(subsId: Long) {
        subsIdFlow.value = SheetRequest(subsId)
    }

    private fun dismiss(request: SheetRequest<Long>) {
        if (subsIdFlow.value === request) subsIdFlow.value = null
    }

    fun dismissForDeletion(targets: Set<DeletionTarget>) {
        val request = subsIdFlow.value ?: return
        if (DeletionTarget.Subscription(request.key) in targets) dismiss(request)
    }

    @Composable
    fun Render() {
        val requested by subsIdFlow.collectAsStateWithLifecycle()
        val subsItems by SubscriptionState.subsItemsFlow.collectAsStateWithLifecycle()
        val subscriptions by SubscriptionRepository.snapshotFlow.collectAsStateWithLifecycle()
        val loading by SubscriptionRepository.updating.collectAsStateWithLifecycle()
        val item = subsItems.find { it.id == requested?.key }
        val data = subscriptions.value
        val subscription = data?.subscriptions?.get(requested?.key)
        GkRetainedSheet(
            request = requested,
            snapshot = if (item != null && subscriptions is Loadable.Ready) SubsSheetSnapshot(item, subscription, loading) else null,
            missing = requested != null && subscriptions is Loadable.Ready && subscription == null &&
                data?.loadErrors?.containsKey(requested?.key) != true && data?.updateErrors?.containsKey(requested?.key) != true,
            onDismissRequest = ::dismiss,
        ) { _, displayed, sheetState, dismiss ->
            val subsItem = displayed.item
            val subscription = displayed.subscription
            val mainVm = MainViewModel.requireCurrent()
            val scope = mainVm.scope
            val scrollState = rememberScrollState()
            val sheetGesturesEnabled by remember {
                derivedStateOf { scrollState.value == 0 }
            }
            GkModalBottomSheet(
                onDismissRequest = dismiss,
                sheetState = sheetState,
                sheetGesturesEnabled = sheetGesturesEnabled,
            ) {
                val showName = subscription?.name ?: UiStrings.subscription_id_description(subsItem.id)
                val childModifier = remember {
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = itemHorizontalPadding, vertical = 8.dp)
                }
                Column(
                    modifier = Modifier
                        .verticalScroll(
                            state = scrollState,
                        )
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = showName,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = childModifier
                    )
                    if (subscription != null) {
                        val timeStr = formatTimeAgo(subsItem.mtime)
                        val timeTooltipState = rememberTooltipState()
                        val timeTooltipScope = rememberCoroutineScope()
                        Row(
                            modifier = childModifier.semantics(mergeDescendants = true) {
                                contentDescription =
                                    UiStrings.subscription_metadata_description(
                                        if (subsItem.isLocal) META.appName else subscription.author ?: UiStrings.unknown,
                                        subscription.version,
                                        timeStr,
                                    )
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (subsItem.isLocal) META.appName else subscription.author ?: UiStrings.unknown,
                                modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.labelMedium,
                                color = when {
                                    subsItem.isLocal -> MaterialTheme.colorScheme.secondary
                                    subscription.author == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = UiStrings.version_prefixed(subscription.version),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                softWrap = false,
                            )
                            TooltipBox(
                                tooltip = { PlainTooltip { Text(text = subsItem.mtimeStr) } },
                                state = timeTooltipState,
                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Above
                                ),
                            ) {
                                Text(
                                    text = timeStr,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .clickable {
                                            timeTooltipScope.launch { timeTooltipState.show() }
                                        }
                                        .semantics {
                                            contentDescription = UiStrings.update_time_description(subsItem.mtimeStr)
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                        if (subscription.globalGroups.isNotEmpty() || subsItem.isLocal) {
                            SubsSheetItem(
                                onClickLabel = UiStrings.global_rules_view_list,
                                onClick = throttle {
                                    dismiss()
                                    mainVm.navigatePage(SubsGlobalGroupListRoute(subsItem.id))
                                },
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = UiStrings.global_rules,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = if (subscription.globalGroups.isNotEmpty()) UiStrings.subscription_global_rules_count(subscription.globalGroups.size) else UiStrings.none_available,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.let {
                                            if (subscription.globalGroups.isEmpty()) {
                                                it.copy(alpha = 0.5f)
                                            } else {
                                                it
                                            }
                                        },
                                    )
                                }
                                GkIcon(
                                    imageVector = GkIcons.KeyboardArrowRight,
                                )
                            }
                        }
                        if (subscription.appGroups.isNotEmpty() || subsItem.isLocal) {
                            SubsSheetItem(
                                onClickLabel = UiStrings.app_rules_view_list,
                                onClick = throttle {
                                    dismiss()
                                    mainVm.navigatePage(SubsAppListRoute(subsItem.id))
                                },
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = UiStrings.app_rules,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = if (subscription.appGroups.isNotEmpty()) UiStrings.subscription_apps_rules_count(subscription.apps.size, subscription.appGroups.size) else UiStrings.none_available,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.let {
                                            if (subscription.appGroups.isEmpty()) {
                                                it.copy(alpha = 0.5f)
                                            } else {
                                                it
                                            }
                                        },
                                    )
                                }
                                GkIcon(
                                    imageVector = GkIcons.KeyboardArrowRight,
                                )
                            }

                        }
                        if (subscription.categories.isNotEmpty() || subsItem.isLocal) {
                            SubsSheetItem(
                                onClickLabel = UiStrings.rule_categories_view_list,
                                onClick = throttle {
                                    dismiss()
                                    mainVm.navigatePage(SubsCategoryRoute(subsItem.id))
                                },
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = UiStrings.rule_categories,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = if (subscription.categories.isNotEmpty()) UiStrings.subscription_categories_count(subscription.categories.size) else UiStrings.none_available,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.let {
                                            if (subscription.categories.isEmpty()) {
                                                it.copy(alpha = 0.5f)
                                            } else {
                                                it
                                            }
                                        },
                                    )
                                }
                                GkIcon(
                                    imageVector = GkIcons.KeyboardArrowRight,
                                )
                            }
                        }
                        val updateUrl = subsItem.updateUrl
                        if (!subsItem.isLocal && updateUrl != null) {
                            SubsSheetItem(
                                onClickLabel = UiStrings.subscription_link_edit,
                                onClick = throttle {
                                    if (SubscriptionRepository.isBusy) {
                                        toast(UiStrings.subscription_refresh_wait_compact)
                                        return@throttle
                                    }
                                    scope.launchUi {
                                        val url = mainVm.subsLinkDialog.request(
                                            initialValue = updateUrl,
                                        )
                                                ?: return@launchUi
                                        SubscriptionRepository.addOrModifyRemote(url, subsItem).message?.let {
                                            toast(it)
                                        }
                                    }
                                },
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = UiStrings.subscription_link,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = updateUrl,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                        softWrap = false,
                                        overflow = TextOverflow.MiddleEllipsis,
                                        modifier = Modifier
                                            .clearAndSetSemantics {}
                                            .clickable(onClickLabel = UiStrings.subscription_link_view, onClick = {
                                                mainVm.openUrl(updateUrl)
                                            })
                                    )
                                }
                                GkIcon(
                                    imageVector = GkIcons.Edit,
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            GkPageBottomSpace()
                            if (displayed.loading) {
                                CircularProgressIndicator()
                            } else {
                                Text(
                                    text = UiStrings.file_load_failed_or_missing,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                TextButton(onClick = throttle {
                                    scope.launchUi {
                                        SubscriptionRepository.refresh().message?.let { toast(it) }
                                    }
                                }) {
                                    Text(text = UiStrings.action_reload)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = childModifier,
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (!subsItem.isLocal && subscription?.supportUri != null) {
                            GkIconButton(
                                imageVector = GkIcons.HelpOutline,
                                onClick = throttle {
                                    mainVm.openUrl(subscription.supportUri)
                                },
                            )
                        }
                        GkIconButton(imageVector = GkIcons.History, onClick = throttle {
                            dismiss()
                            mainVm.navigatePage(ActionLogRoute(subsId = subsItem.id))
                        })
                        if (subsItem.id != LOCAL_SUBS_ID) {
                            GkIconButton(
                                imageVector = GkIcons.Delete,
                                onClick = throttle {
                                    mainVm.confirmDelete(
                                        title = UiStrings.subscription_delete,
                                        text = UiStrings.delete_named_confirmation(subscription?.name ?: subsItem.id),
                                        targets = { setOf(DeletionTarget.Subscription(subsItem.id)) },
                                        dismiss = dismiss,
                                    ) {
                                        val result = SubscriptionRepository.delete(subsItem.id)
                                        result.message?.let { toast(it) }
                                    }
                                },
                            )
                        }
                    }
                    GkPageBottomSpace(height = GkPageBottomSpaceDefaults.CompactHeight)
                }
            }
        }
    }
}

@Composable
private fun SubsSheetItem(
    onClickLabel: String,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = itemHorizontalPadding, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = onClickLabel, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
