package li.gkd.app.ui.subscription

import li.gkd.app.META
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import li.gkd.app.error.messageResource
import li.gkd.app.error.BusinessError
import li.gkd.app.error.ErrorMessage
import li.gkd.app.resources.*
import li.gkd.app.state.Loadable
import li.gkd.app.record.TriggerSummary
import li.gkd.app.record.summarizeTriggers
import li.gkd.app.subscription.RawSubscription
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.subscription.SubscriptionException
import li.gkd.app.subscription.SubscriptionFailureReason
import li.gkd.app.time.format
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkLazyCopyableText
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkModalBottomSheet
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkPageBottomSpaceDefaults
import li.gkd.app.ui.component.GkRetainedSheet
import li.gkd.app.ui.component.GkTriggerCount
import li.gkd.app.ui.component.SheetRequest
import li.gkd.app.ui.navigation.ActionLogRoute
import li.gkd.app.ui.navigation.AppNavigator
import li.gkd.app.ui.navigation.ConfirmDeletion
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.navigation.SubsAppListRoute
import li.gkd.app.ui.navigation.SubsCategoryRoute
import li.gkd.app.ui.navigation.SubsGlobalGroupListRoute
import li.gkd.app.ui.share.DeletionTarget
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.text.formatTimeAgo
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.copyText
import li.gkd.db.Db
import li.gkd.db.SubscriptionIds
import li.gkd.db.SubsItem
import org.jetbrains.compose.resources.stringResource

private data class SubsSheetSnapshot(
    val item: SubsItem,
    val subscription: RawSubscription?,
    val loading: Boolean,
    val loadError: Exception?,
    val updateError: Exception?,
    val triggerSummary: Loadable<TriggerSummary>,
)

class SubsSheetState(
    private val navigator: AppNavigator,
    private val openUrl: (String) -> Unit,
    private val requestUrl: suspend (String) -> String?,
    private val confirmDelete: ConfirmDeletion,
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
        val subsId = requested?.key
        val triggerSummaryFlow = remember(subsId) {
            if (subsId == null) flowOf<Loadable<TriggerSummary>>(Loadable.Loading)
            else Db.ruleTriggerStatsDao.observe(subsId = subsId)
                .map<List<li.gkd.db.RuleTriggerStats>, Loadable<TriggerSummary>> { records ->
                    Loadable.Ready(records.summarizeTriggers())
                }
                .catch { emit(Loadable.Failure(it)) }
        }
        val triggerSummary = key(requested) {
            triggerSummaryFlow.collectAsStateWithLifecycle(Loadable.Loading).value
        }
        GkRetainedSheet(
            request = requested,
            snapshot = if (item != null && subscriptions is Loadable.Ready) SubsSheetSnapshot(
                item,
                subscription,
                loading,
                data?.loadErrors?.get(item.id),
                data?.updateErrors?.get(item.id),
                triggerSummary,
            ) else null,
            missing = requested != null && subscriptions is Loadable.Ready && subscription == null &&
                    data?.loadErrors?.containsKey(requested?.key) != true && data?.updateErrors?.containsKey(
                requested?.key
            ) != true,
            onDismissRequest = ::dismiss,
        ) { _, displayed, sheetState, dismiss ->
            val subsItem = displayed.item
            val subscription = displayed.subscription
            val scope = rememberCoroutineScope()
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
                        modifier = childModifier,
                    )
                    AnimatedSheetSection(displayed.loadError) { error ->
                        SubsErrorCard(
                            title = stringResource(Res.string.subscription_load_failed),
                            error = error,
                        ) {
                            if (displayed.loading) {
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            } else {
                                GkIconButton(
                                    imageVector = GkIcons.Autorenew,
                                    contentDescription = stringResource(Res.string.action_reload),
                                    onClick = {
                                        scope.launchUi {
                                            SubscriptionRepository.refresh(subsItem.id).message()
                                                ?.let { ToastUtils.show(it) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    AnimatedSheetSection(displayed.updateError) { error ->
                        SubsErrorCard(
                            title = stringResource(Res.string.subscription_update_failed),
                            error = error,
                        ) {
                            GkIconButton(
                                imageVector = GkIcons.Close,
                                contentDescription = stringResource(Res.string.subscription_update_error_dismiss),
                                onClick = {
                                    scope.launchUi {
                                        SubscriptionRepository.dismissUpdateError(subsItem.id, error)
                                    }
                                },
                            )
                        }
                    }
                    AnimatedSheetSection(subscription) { subscription ->
                        val timeStr = formatTimeAgo(subsItem.mtime)
                        val author = if (subsItem.isLocal) META.appName else subscription.author
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
                            modifier = childModifier,
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {
                                    contentDescription = metadataDescription
                                },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = author,
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
                            when (val stats = displayed.triggerSummary) {
                                is Loadable.Ready -> GkTriggerCount(
                                    count = stats.value.count,
                                    lastTriggerTime = stats.value.lastTime,
                                )
                                is Loadable.Loading -> Unit
                                is Loadable.Failure -> Text(
                                    text = stringResource(Res.string.data_load_failed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (subscription.globalGroups.isNotEmpty() || subsItem.isLocal) {
                            SubsSheetItem(
                                onClickLabel = stringResource(Res.string.global_rules_view_list),
                                onClick = click@{
                                    dismiss()
                                    navigator.navigate(SubsGlobalGroupListRoute(subsItem.id))
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
                                    navigator.navigate(SubsAppListRoute(subsItem.id))
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
                        val categoryCount = subscription.categories.size + if (subscription.appGroups.any {
                                subscription.getCategory(it.name) == null
                            }) 1 else 0
                        if (categoryCount > 0 || subsItem.isLocal) {
                            SubsSheetItem(
                                onClickLabel = stringResource(Res.string.rule_categories_view_list),
                                onClick = click@{
                                    dismiss()
                                    navigator.navigate(SubsCategoryRoute(subsItem.id))
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
                                        text = if (categoryCount > 0) stringResource(
                                            Res.string.subscription_categories_count,
                                            categoryCount
                                        ) else stringResource(Res.string.none_available),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.let {
                                            if (categoryCount == 0) {
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
                    }

                    Row(
                        modifier = childModifier,
                        horizontalArrangement = Arrangement.End
                    ) {
                        val updateUrl = subsItem.updateUrl
                        if (!subsItem.isLocal && updateUrl != null) {
                            GkIconButton(
                                imageVector = GkIcons.LinkDiagonal,
                                contentDescription = stringResource(Res.string.subscription_link),
                                onClick = click@{
                                    scope.launchUi {
                                        val url = requestUrl(
                                            updateUrl,
                                        )
                                            ?: return@launchUi
                                        SubscriptionRepository.addOrModifyRemote(url, subsItem.id)
                                            .message()
                                            ?.let {
                                                ToastUtils.show(it)
                                            }
                                    }
                                },
                            )
                        }
                        if (!subsItem.isLocal && subscription?.supportUri != null) {
                            GkIconButton(
                                imageVector = GkIcons.HelpOutline,
                                onClick = click@{
                                    openUrl(subscription.supportUri)
                                },
                            )
                        }
                        GkIconButton(imageVector = GkIcons.History, onClick = click@{
                            dismiss()
                            navigator.navigate(ActionLogRoute(subsId = subsItem.id))
                        })
                        if (subsItem.id != SubscriptionIds.LOCAL) {
                            GkIconButton(
                                imageVector = GkIcons.Delete,
                                onClick = click@{
                                    confirmDelete(
                                        Res.string.subscription_delete.getSync(),
                                        Res.string.delete_named_confirmation.getSync(
                                            subscription?.name ?: subsItem.id
                                        ),
                                        { setOf(DeletionTarget.Subscription(subsItem.id)) },
                                        dismiss,
                                    ) {
                                        val result = SubscriptionRepository.delete(subsItem.id)
                                        result.message()?.let { ToastUtils.show(it) }
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
private fun ColumnScope.SubsErrorCard(
    title: String,
    error: Exception,
    action: @Composable () -> Unit,
) {
    var showDetails by remember(error) { mutableStateOf(false) }
    val message = (error as? BusinessError)?.errorMessage
    val structured = message as? ErrorMessage.WithDetail
    val category = when ((error as? SubscriptionException)?.reason) {
        SubscriptionFailureReason.SubscriptionUpdateUrlRequestFailed ->
            stringResource(Res.string.subscription_error_network)
        SubscriptionFailureReason.TextParseFailed ->
            stringResource(Res.string.subscription_error_parse)
        else -> structured?.message?.messageResource()
    }
    val heading = if (category != null) {
        stringResource(Res.string.subscription_error_heading, title, category)
    } else title
    val detail = (structured?.detail ?: message)?.messageResource() ?: error.messageResource()
    if (showDetails) {
        val fullMessage = if (structured != null) message.messageResource() else detail
        GkAlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(title) },
            text = {
                GkLazyCopyableText(
                    text = AnnotatedString(fullMessage),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    onCopy = ::copyText,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text(stringResource(Res.string.action_close))
                }
            },
        )
    }
    Spacer(Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = itemHorizontalPadding)
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                onClickLabel = stringResource(Res.string.subscription_error_details),
                onClick = { showDetails = true },
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail.replace('\n', ' ').replace('\r', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            action()
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun <T : Any> AnimatedSheetSection(
    value: T?,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    var retained by remember { mutableStateOf(value) }
    if (value != null) {
        SideEffect { retained = value }
    }
    AnimatedVisibility(
        visible = value != null,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
    ) {
        val displayed = value ?: retained
        if (displayed != null) {
            Column(Modifier.fillMaxWidth()) {
                content(displayed)
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
