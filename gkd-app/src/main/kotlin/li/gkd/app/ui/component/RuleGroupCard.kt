package li.gkd.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import li.gkd.app.data.CategoryConfig
import li.gkd.app.data.ExcludeData
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.SubsConfig
import li.gkd.app.ui.getGlobalGroupChecked
import li.gkd.app.ui.icon.ResetSettings
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.util.getGroupEnable
import li.gkd.app.util.throttle
import java.util.Objects


@Composable
fun RuleGroupCard(
    modifier: Modifier = Modifier,
    subs: RawSubscription,
    appId: String?,
    group: RawSubscription.RawGroupProps,
    subsConfig: SubsConfig?,
    categoryConfig: CategoryConfig?,
    switchEnabled: Boolean = true,
    onOpen: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    focusGroup: Triple<Long, String?, Int>? = null,
    onFocusHandled: () -> Unit = {},
    isSelectedMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    onSelectedChange: () -> Unit = {},
) {
    val category = subs.getCategory(group.name)

    val inGlobalAppPage = appId != null && group is RawSubscription.RawGlobalGroup

    var highlighted by remember { mutableStateOf(false) }
    if (focusGroup != null) {
        if (subs.id == focusGroup.first && group.key == focusGroup.third && if (group is RawSubscription.RawAppGroup) appId == focusGroup.second else focusGroup.second == null) {
            LaunchedEffect(isSelectedMode) {
                if (isSelectedMode) {
                    highlighted = false
                    onFocusHandled()
                    return@LaunchedEffect
                }
                delay(300)
                var i = 0
                highlighted = true
                while (isActive && i < 4) {
                    delay(400)
                    highlighted = !highlighted
                    i++
                }
                highlighted = false
                onFocusHandled()
            }
        }
    }
    val excludeData = remember(subsConfig?.exclude) {
        ExcludeData.parse(subsConfig?.exclude)
    }
    val checked = if (inGlobalAppPage) {
        getGlobalGroupChecked(
            subs,
            excludeData,
            group,
            appId,
        )
    } else {
        getGroupEnable(
            group,
            subsConfig,
            category,
            categoryConfig,
        )
    }
    val onClick = if (isSelectedMode) onSelectedChange else throttle(onOpen)
    val containerColor = animateColorAsState(
        if (isSelected || highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        tween()
    )
    Card(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = "打开规则详情弹窗",
                onLongClickLabel = "进入多选模式"
            ),
        shape = MaterialTheme.shapes.extraSmall,
        colors = CardDefaults.cardColors(
            containerColor = containerColor.value
        ),
    ) {
        val canRest = if (inGlobalAppPage) {
            excludeData.appIds.contains(appId)
        } else {
            subsConfig?.enable != null
        }
        val hasExcludeActivity = if (inGlobalAppPage) {
            checked != null && excludeData.activityIds.any { it.first == appId }
        } else {
            excludeData.activityIds.isNotEmpty()
        }
        Box {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .weight(1f),
                ) {
                    GroupNameText(
                        modifier = Modifier.fillMaxWidth(),
                        text = group.name,
                        style = MaterialTheme.typography.bodyLarge,
                        isGlobal = group is RawSubscription.RawGlobalGroup,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (group.valid) {
                        if (!group.desc.isNullOrBlank()) {
                            Text(
                                text = group.desc!!,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            text = group.errorDesc ?: "未知错误",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                val percent = usePercentAnimatable(!isSelectedMode)
                val switchModifier = Modifier
                    .noRippleClickable(onClick = {})
                    .padding(8.dp)
                    .graphicsLayer(
                        alpha = 0.5f + (1 - 0.5f) * percent.value,
                    )
                if (!group.valid) {
                    InnerDisableSwitch(
                        modifier = switchModifier,
                        valid = false,
                        isSelectedMode = isSelectedMode,
                    )
                } else if (checked != null) {
                    PerfSwitch(
                        key = Objects.hash(subs.id, appId, group.key),
                        modifier = switchModifier.minimumInteractiveComponentSize(),
                        checked = checked,
                        enabled = switchEnabled,
                        onCheckedChange = if (isSelectedMode) null else onCheckedChange,
                        thumbContent = if (canRest) ({
                            PerfIcon(
                                imageVector = ResetSettings,
                                modifier = Modifier.size(8.dp)
                            )
                        }) else null,
                    )
                } else {
                    InnerDisableSwitch(
                        modifier = switchModifier,
                        isSelectedMode = isSelectedMode,
                    )
                }
            }
            if (hasExcludeActivity) {
                PerfIcon(
                    imageVector = PerfIcon.Block,
                    contentDescription = "此规则已排除部分页面",
                    tint = if (isSelectedMode) {
                        LocalContentColor.current.copy(alpha = 0.5f)
                    } else {
                        LocalContentColor.current
                    },
                    modifier = Modifier
                        .padding(top = 4.dp, end = 4.dp)
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                )
            }
        }
    }
}

fun getActualGroupChecked(
    subs: RawSubscription,
    group: RawSubscription.RawGroupProps,
    appId: String?,
    subsConfig: SubsConfig?,
    categoryConfig: CategoryConfig?,
): Boolean {
    if (!group.valid) return false
    val inGlobalAppPage = appId != null && group is RawSubscription.RawGlobalGroup
    return if (inGlobalAppPage) {
        getGlobalGroupChecked(
            subs,
            ExcludeData.parse(subsConfig?.exclude),
            group,
            appId,
        )
    } else {
        getGroupEnable(
            group,
            subsConfig,
            subs.getCategory(group.name),
            categoryConfig,
        )
    } ?: false
}


@Composable
fun BatchActionButtonGroup(
    onDisable: () -> Unit,
    onEnable: () -> Unit,
    onReset: () -> Unit,
) {
    PerfIconButton(
        imageVector = PerfIcon.ToggleOff,
        contentDescription = "批量关闭规则",
        onClick = throttle(onDisable),
    )
    PerfIconButton(
        imageVector = PerfIcon.ToggleOn,
        contentDescription = "批量打开规则",
        onClick = throttle(onEnable),
    )
    PerfIconButton(
        imageVector = ResetSettings,
        contentDescription = "批量重置规则开关",
        onClick = throttle(onReset),
    )
}
