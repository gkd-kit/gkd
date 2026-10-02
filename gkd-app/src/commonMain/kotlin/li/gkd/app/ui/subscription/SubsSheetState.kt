package li.gkd.app.ui.subscription

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_reload
import li.gkd.app.resources.app_rules
import li.gkd.app.resources.app_rules_view_list
import li.gkd.app.resources.delete_named_confirmation
import li.gkd.app.resources.file_load_failed_or_missing
import li.gkd.app.resources.global_rules
import li.gkd.app.resources.global_rules_view_list
import li.gkd.app.resources.none_available
import li.gkd.app.resources.rule_categories
import li.gkd.app.resources.rule_categories_view_list
import li.gkd.app.resources.subscription_apps_rules_count
import li.gkd.app.resources.subscription_categories_count
import li.gkd.app.resources.subscription_delete
import li.gkd.app.resources.subscription_global_rules_count
import li.gkd.app.resources.subscription_id_description
import li.gkd.app.resources.subscription_link
import li.gkd.app.resources.subscription_link_edit
import li.gkd.app.resources.subscription_link_view
import li.gkd.app.resources.subscription_metadata_description
import li.gkd.app.resources.subscription_refresh_wait_compact
import li.gkd.app.resources.unknown
import li.gkd.app.resources.update_time_description
import li.gkd.app.resources.version_prefixed
import li.gkd.app.state.Loadable
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.time.format
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkModalBottomSheet
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkPageBottomSpaceDefaults
import li.gkd.app.ui.component.GkRetainedSheet
import li.gkd.app.ui.component.SheetRequest
import li.gkd.app.ui.navigation.ActionLogRoute
import li.gkd.app.ui.navigation.AppRoute
import li.gkd.app.ui.navigation.SubsAppListRoute
import li.gkd.app.ui.navigation.SubsCategoryRoute
import li.gkd.app.ui.navigation.SubsGlobalGroupListRoute
import li.gkd.app.ui.share.DeletionTarget
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.text.formatTimeAgo
import li.gkd.app.ui.text.getSync
import li.gkd.app.ui.text.subscriptionMessage
import li.gkd.db.Db
import li.gkd.db.LOCAL_SUBS_ID
import li.gkd.db.SubsItem
import org.jetbrains.compose.resources.stringResource

private data class SubsSheetSnapshot(
    val item: SubsItem,
    val subscription: RawSubscription?,
    val loading: Boolean,
)

class SubsSheetHost(
    val appName: String,
    val navigate: (AppRoute) -> Unit,
    val openUrl: (String) -> Unit,
    val requestUrl: suspend (String) -> String?,
    val toast: (String) -> Unit,
    val confirmDelete: (String, String, () -> Set<DeletionTarget>, () -> Unit, suspend () -> Unit) -> Unit,
)

class SubsSheetState(
    private val hostProvider: () -> SubsSheetHost
) {
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
        val subsItems by remember { Db.subsItemDao.query() }.collectAsStateWithLifecycle(emptyList())
        val subscriptions by SubscriptionRepository.snapshotFlow.collectAsStateWithLifecycle()
        val loading by SubscriptionRepository.updating.collectAsStateWithLifecycle()
        val item = subsItems.find { it.id == requested?.key }
        val data = subscriptions.value
        val subscription = data?.subscriptions?.get(requested?.key)
        GkRetainedSheet(
            request = requested,
            snapshot = if (item != null && subscriptions is Loadable.Ready) SubsSheetSnapshot(
                item,
                subscription,
                loading
            ) else null,
            missing = requested != null && subscriptions is Loadable.Ready && subscription == null &&
                    data?.loadErrors?.containsKey(requested?.key) != true && data?.updateErrors?.containsKey(
                requested?.key
            ) != true,
            onDismissRequest = ::dismiss,
        ) { _, displayed, sheetState, dismiss ->
            val subsItem = displayed.item
            val subscription = displayed.subscription
            val host = hostProvider()

            val toast = host.toast
            val scope = rememberCoroutineScope()
            fun launchAction(block: suspend () -> Unit) = scope.launch {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toast(e.subscriptionMessage())
                }
            }

            val scrollState = rememberScrollState()
            val sheetGesturesEnabled by remember {
                derivedStateOf { scrollState.value == 0 }
            }
            GkModalBottomSheet(
                onDismissRequest = dismiss,
                sheetState = sheetState,
                sheetGesturesEnabled = sheetGesturesEnabled,
            ) {
                val showName = subscription?.name ?: stringResource(
                    Res.string.subscription_id_description,
                    subsItem.id
                )
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
                        val author = if (subsItem.isLocal) host.appName else subscription.author
                            ?: stringResource(Res.string.unknown)
                        val metadataDescription = stringResource(
                            Res.string.subscription_metadata_description,
                            author, subscription.version, timeStr,
                        )
                        val timeDescription = stringResource(
                            Res.string.update_time_description,
                            subsItem.mtime.format("yyyy-MM-dd HH:mm:ss"),
                        )
                        val timeTooltipState = rememberTooltipState()
                        val timeTooltipScope = rememberCoroutineScope()
                        Row(
                            modifier = childModifier.semantics(mergeDescendants = true) {
                                contentDescription = metadataDescription
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (subsItem.isLocal) host.appName else subscription.author
                                    ?: stringResource(Res.string.unknown),
                                modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.labelMedium,
                                color = when {
                                    subsItem.isLocal -> MaterialTheme.colorScheme.secondary
                                    subscription.author == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = 0.5f
                                    )

                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(
                                    Res.string.version_prefixed,
                                    subscription.version
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                            TooltipBox(
                                tooltip = { PlainTooltip { Text(text = subsItem.mtime.format("yyyy-MM-dd HH:mm:ss")) } },
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
                                            contentDescription = timeDescription
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
                                onClickLabel = stringResource(Res.string.global_rules_view_list),
                                onClick = click@{
                                    dismiss()
                                    host.navigate(SubsGlobalGroupListRoute(subsItem.id))
                                },
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = stringResource(Res.string.global_rules),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = if (subscription.globalGroups.isNotEmpty()) stringResource(
                                            Res.string.subscription_global_rules_count,
                                            subscription.globalGroups.size
                                        ) else stringResource(Res.string.none_available),
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
                                onClickLabel = stringResource(Res.string.app_rules_view_list),
                                onClick = click@{
                                    dismiss()
                                    host.navigate(SubsAppListRoute(subsItem.id))
                                },
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = stringResource(Res.string.app_rules),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = if (subscription.appGroups.isNotEmpty()) stringResource(
                                            Res.string.subscription_apps_rules_count,
                                            subscription.apps.size,
                                            subscription.appGroups.size
                                        ) else stringResource(Res.string.none_available),
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
                                onClickLabel = stringResource(Res.string.rule_categories_view_list),
                                onClick = click@{
                                    dismiss()
                                    host.navigate(SubsCategoryRoute(subsItem.id))
                                },
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = stringResource(Res.string.rule_categories),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        text = if (subscription.categories.isNotEmpty()) stringResource(
                                            Res.string.subscription_categories_count,
                                            subscription.categories.size
                                        ) else stringResource(Res.string.none_available),
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
                                onClickLabel = stringResource(Res.string.subscription_link_edit),
                                onClick = click@{
                                    if (SubscriptionRepository.isBusy) {
                                        toast(Res.string.subscription_refresh_wait_compact.getSync())
                                        return@click
                                    }
                                    launchAction {
                                        val url = host.requestUrl(
                                            updateUrl,
                                        )
                                            ?: return@launchAction
                                        SubscriptionRepository.addOrModifyRemote(url, subsItem)
                                            .message()
                                            ?.let {
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
                                        text = stringResource(Res.string.subscription_link),
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
                                            .clickable(
                                                onClickLabel = stringResource(Res.string.subscription_link_view),
                                                onClick = {
                                                    host.openUrl(updateUrl)
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
                                    text = stringResource(Res.string.file_load_failed_or_missing),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                TextButton(onClick = click@{
                                    launchAction {
                                        SubscriptionRepository.refresh().message()
                                            ?.let { toast(it) }
                                    }
                                }) {
                                    Text(text = stringResource(Res.string.action_reload))
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
                                onClick = click@{
                                    host.openUrl(subscription.supportUri)
                                },
                            )
                        }
                        GkIconButton(imageVector = GkIcons.History, onClick = click@{
                            dismiss()
                            host.navigate(ActionLogRoute(subsId = subsItem.id))
                        })
                        if (subsItem.id != LOCAL_SUBS_ID) {
                            GkIconButton(
                                imageVector = GkIcons.Delete,
                                onClick = click@{
                                    host.confirmDelete(
                                        Res.string.subscription_delete.getSync(),
                                        Res.string.delete_named_confirmation.getSync(
                                            subscription?.name ?: subsItem.id
                                        ),
                                        { setOf(DeletionTarget.Subscription(subsItem.id)) },
                                        dismiss,
                                    ) {
                                        val result = SubscriptionRepository.delete(subsItem.id)
                                        result.message()?.let { toast(it) }
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
