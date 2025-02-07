package li.songe.gkd.ui.component

import androidx.activity.compose.LocalActivity
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import li.songe.gkd.META
import li.songe.gkd.MainActivity
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsItem
import li.songe.gkd.ui.home.HomeVm
import li.songe.gkd.util.formatTimeAgo
import li.songe.gkd.util.map
import li.songe.gkd.util.subsLoadErrorsFlow
import li.songe.gkd.util.subsRefreshErrorsFlow
import li.songe.gkd.util.throttle
import li.songe.gkd.util.updateSubsMutex


@Composable
fun SubsItemCard(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource,
    subsItem: SubsItem,
    subscription: RawSubscription?,
    index: Int,
    vm: HomeVm,
    isSelectedMode: Boolean,
    isSelected: Boolean,
    onCheckedChange: ((Boolean) -> Unit),
    onSelectedChange: (() -> Unit)? = null,
) {
    val context = LocalActivity.current as MainActivity
    val subsLoadError by remember(subsItem.id) {
        subsLoadErrorsFlow.map(vm.viewModelScope) { it[subsItem.id] }
    }.collectAsState()
    val subsRefreshError by remember(subsItem.id) {
        subsRefreshErrorsFlow.map(vm.viewModelScope) { it[subsItem.id] }
    }.collectAsState()
    val subsRefreshing by updateSubsMutex.state.collectAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    val onClick = {
        if (!dragged) {
            if (isSelectedMode) {
                onSelectedChange?.invoke()
            } else if (!updateSubsMutex.mutex.isLocked) {
                context.mainVm.sheetSubsIdFlow.value = subsItem.id
            }
        }
    }
    Card(
        onClick = onClick,
        modifier = modifier
            .padding(16.dp, 2.dp),
        shape = MaterialTheme.shapes.small,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
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
                        text = "$index. ${subscription.name}",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = subscription.numText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (subscription.groupsSize == 0) {
                            LocalContentColor.current.copy(alpha = 0.5f)
                        } else {
                            LocalContentColor.current
                        }
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (subsItem.id >= 0) {
                            if (subscription.author != null) {
                                Text(
                                    text = subscription.author,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Text(
                                text = "v" + (subscription.version.toString()),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        } else {
                            Text(
                                text = META.appName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Text(
                            text = formatTimeAgo(subsItem.mtime),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                } else {
                    Text(
                        text = "id=${subsItem.id}",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val color = if (subsLoadError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    }
                    Text(
                        text = subsLoadError?.message
                            ?: if (subsRefreshing) "加载中..." else "文件不存在",
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
                if (subsRefreshError != null) {
                    Text(
                        text = "更新错误: ${subsRefreshError?.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            key(subsItem.id) {
                Switch(
                    checked = subsItem.enable,
                    enabled = !isSelectedMode,
                    onCheckedChange = if (isSelectedMode) null else throttle(fn = onCheckedChange),
                )
            }
        }
    }
}























