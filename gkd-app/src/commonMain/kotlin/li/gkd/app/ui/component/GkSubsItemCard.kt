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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.resources.Res
import li.gkd.app.resources.author_description
import li.gkd.app.resources.disabled
import li.gkd.app.resources.enabled
import li.gkd.app.resources.file_missing
import li.gkd.app.resources.loading_progress
import li.gkd.app.resources.not_selected
import li.gkd.app.resources.selected
import li.gkd.app.resources.selection_deselect
import li.gkd.app.resources.selection_mode_enter
import li.gkd.app.resources.selection_select
import li.gkd.app.resources.subscription_details_view
import li.gkd.app.resources.subscription_id_description
import li.gkd.app.resources.subscription_numbered_name
import li.gkd.app.resources.subscription_order_description
import li.gkd.app.resources.subscription_switch_paused_description
import li.gkd.app.resources.subscription_version_description
import li.gkd.app.resources.update_error_detail
import li.gkd.app.resources.update_time_description
import li.gkd.app.resources.version_prefixed
import org.jetbrains.compose.resources.stringResource

data class SubscriptionCardData(
    val id: Long, val enabled: Boolean, val name: String?, val version: Int = 0,
    val author: String? = null, val globalGroups: Int = 0, val apps: Int = 0,
    val appGroups: Int = 0, val updatedAt: String, val appName: String,
    val loadError: String? = null, val refreshError: String? = null,
)

@Composable
fun GkSubsItemCard(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource,
    data: SubscriptionCardData,
    matchingEnabled: Boolean,
    index: Int,
    isSelectedMode: Boolean,
    isSelected: Boolean,
    selectionEnabled: Boolean = true,
    handlesLongPress: Boolean = true,
    onSelect: () -> Unit,
    refreshing: Boolean,
    onOpen: () -> Unit,
    onCheckedChange: ((Boolean) -> Unit),
    onSelectedChange: (() -> Unit)? = null,
) {
    val selectedLabel = stringResource(Res.string.selected)
    val notSelectedLabel = stringResource(Res.string.not_selected)
    val pausedLabel = stringResource(Res.string.subscription_switch_paused_description)
    val enabledLabel = stringResource(Res.string.enabled)
    val disabledLabel = stringResource(Res.string.disabled)
    val deselectLabel = stringResource(Res.string.selection_deselect)
    val selectLabel = stringResource(Res.string.selection_select)
    val detailsLabel = stringResource(Res.string.subscription_details_view)
    val selectionModeLabel = stringResource(Res.string.selection_mode_enter)
    val authorLabel = stringResource(Res.string.author_description, data.author.orEmpty())
    val versionLabel = stringResource(Res.string.subscription_version_description, data.version)
    val updatedLabel = stringResource(Res.string.update_time_description, data.updatedAt)
    val orderLabel =
        stringResource(Res.string.subscription_order_description, index, data.name.orEmpty())
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
                    if (isSelected) selectedLabel else notSelectedLabel
                } else if (!matchingEnabled) {
                    pausedLabel
                } else {
                    if (data.enabled) enabledLabel else disabledLabel
                }
                if (isSelectedMode) {
                    selected = isSelected
                    role = Role.Checkbox
                }
                this.onClick(
                    label = if (isSelectedMode) {
                        if (isSelected) deselectLabel else selectLabel
                    } else detailsLabel,
                    action = null,
                )
                if (selectionEnabled) {
                    this.onLongClick(
                        label = if (isSelectedMode) selectLabel else selectionModeLabel,
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
                if (data.name != null) {
                    Text(
                        modifier = Modifier.semantics {
                            contentDescription = orderLabel
                        },
                        text = stringResource(
                            Res.string.subscription_numbered_name,
                            index,
                            data.name.orEmpty()
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    GkRuleStats(
                        GkRuleStatsData(
                            globalGroups = data.globalGroups,
                            apps = data.apps,
                            appGroups = data.appGroups,
                        )
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (data.id >= 0) {
                            if (data.author != null) {
                                Text(
                                    modifier = Modifier.semantics {
                                        contentDescription = authorLabel
                                    },
                                    text = data.author,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Text(
                                modifier = Modifier.semantics {
                                    contentDescription = versionLabel
                                },
                                text = stringResource(Res.string.version_prefixed, data.version),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        } else {
                            Text(
                                modifier = Modifier.clearAndSetSemantics {},
                                text = data.appName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        val timeStr = data.updatedAt
                        Text(
                            modifier = Modifier.semantics {
                                contentDescription = updatedLabel
                            },
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(Res.string.subscription_id_description, data.id),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val color = if (data.loadError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    }
                    Text(
                        text = data.loadError
                            ?: if (refreshing) stringResource(Res.string.loading_progress) else stringResource(
                                Res.string.file_missing
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
                if (data.refreshError != null) {
                    Text(
                        text = stringResource(
                            Res.string.update_error_detail,
                            data.refreshError.orEmpty()
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            if (isSelectedMode) {
                GkCheckbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    enabled = selectionEnabled,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                )
            } else {
                GkSwitch(
                    key = data.id,
                    checked = matchingEnabled && data.enabled,
                    enabled = matchingEnabled,
                    onCheckedChange = onCheckedChange,
                )
            }
        }
    }
}
