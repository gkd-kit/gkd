package li.gkd.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.text.UiStrings
import li.gkd.app.META
import li.gkd.app.data.RawSubscription
import li.gkd.db.SubsItem
import li.gkd.app.util.TimeUtils.formatTimeAgo
import li.gkd.app.util.TimeUtils.throttle


@Composable
fun GkSubsItemCard(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource,
    subsItem: SubsItem,
    matchingEnabled: Boolean,
    subscription: RawSubscription?,
    index: Int,
    isSelectedMode: Boolean,
    isSelected: Boolean,
    selectionEnabled: Boolean = true,
    handlesLongPress: Boolean = true,
    onSelect: () -> Unit,
    loadError: Exception?,
    refreshError: Exception?,
    refreshing: Boolean,
    onOpen: () -> Unit,
    onCheckedChange: ((Boolean) -> Unit),
    onSelectedChange: (() -> Unit)? = null,
) {
    val dragged by interactionSource.collectIsDraggedAsState()
    val onClick = {
        if (!dragged) {
            if (isSelectedMode) {
                if (selectionEnabled) onSelectedChange?.invoke()
            } else if (!refreshing) {
                onOpen()
            }
        }
    }
    val containerColor = animateColorAsState(
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        tween()
    )
    Card(
        modifier = modifier
            .padding(16.dp, 4.dp)
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = !isSelectedMode || selectionEnabled,
                onClick = onClick,
                onLongClick = if (handlesLongPress && selectionEnabled) onSelect else null,
            )
            .semantics {
                stateDescription = if (isSelectedMode) {
                    if (isSelected) UiStrings.selected else UiStrings.not_selected
                } else if (!matchingEnabled) {
                    UiStrings.subscription_switch_paused_description
                } else {
                    if (subsItem.enable) UiStrings.enabled else UiStrings.disabled
                }
                if (isSelectedMode) {
                    selected = isSelected
                    role = Role.Checkbox
                }
                this.onClick(
                    label = if (isSelectedMode) {
                        if (isSelected) UiStrings.selection_deselect else UiStrings.selection_select
                    } else UiStrings.subscription_details_view,
                    action = null,
                )
                if (selectionEnabled) {
                    this.onLongClick(
                        label = if (isSelectedMode) UiStrings.selection_select else UiStrings.selection_mode_enter,
                    ) {
                        onSelect()
                        true
                    }
                }
            },
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = containerColor.value
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (subscription != null) {
                    Text(
                        modifier = Modifier.semantics {
                            contentDescription = UiStrings.subscription_order_description(index, subscription.name)
                        },
                        text = UiStrings.subscription_numbered_name(index, subscription.name),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    GkRuleStats(GkRuleStatsData(
                        globalGroups = subscription.globalGroups.size,
                        apps = subscription.apps.size,
                        appGroups = subscription.appGroups.size,
                    ))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (subsItem.id >= 0) {
                            if (subscription.author != null) {
                                Text(
                                    modifier = Modifier.semantics {
                                        contentDescription = UiStrings.author_description(subscription.author)
                                    },
                                    text = subscription.author,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Text(
                                modifier = Modifier.semantics {
                                    contentDescription = UiStrings.subscription_version_description(subscription.version)
                                },
                                text = UiStrings.version_prefixed(subscription.version),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        } else {
                            Text(
                                modifier = Modifier.clearAndSetSemantics {},
                                text = META.appName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        val timeStr = formatTimeAgo(subsItem.mtime)
                        Text(
                            modifier = Modifier.semantics {
                                contentDescription = UiStrings.update_time_description(timeStr)
                            },
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                } else {
                    Text(
                        text = UiStrings.subscription_id_description(subsItem.id),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val color = if (loadError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    }
                    Text(
                        text = loadError?.message
                            ?: if (refreshing) UiStrings.loading_progress else UiStrings.file_missing,
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
                if (refreshError != null) {
                    Text(
                        text = UiStrings.update_error_detail(refreshError.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            if (isSelectedMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    enabled = selectionEnabled,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                )
            } else {
                GkSwitch(
                    key = subsItem.id,
                    checked = matchingEnabled && subsItem.enable,
                    enabled = matchingEnabled,
                    onCheckedChange = throttle(fn = onCheckedChange),
                )
            }
        }
    }
}
