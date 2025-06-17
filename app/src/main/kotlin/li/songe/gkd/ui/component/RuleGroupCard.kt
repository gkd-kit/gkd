package li.songe.gkd.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import li.songe.gkd.appScope
import li.songe.gkd.data.CategoryConfig
import li.songe.gkd.data.ExcludeData
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.getGlobalGroupChecked
import li.songe.gkd.ui.icon.ResetSettings
import li.songe.gkd.ui.local.LocalMainViewModel
import li.songe.gkd.util.getGroupEnable
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast


@Composable
fun RuleGroupCard(
    modifier: Modifier = Modifier,
    subs: RawSubscription,
    appId: String?,
    group: RawSubscription.RawGroupProps,
    subsConfig: SubsConfig?,
    category: RawSubscription.RawCategory?,
    categoryConfig: CategoryConfig?,
    showBottom: Boolean,
    focusGroupFlow: MutableStateFlow<Triple<Long, String?, Int>?>? = null,
    isSelectedMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    onSelectedChange: () -> Unit = {},
) {
    val mainVm = LocalMainViewModel.current

    val inGlobalAppPage = appId != null && group is RawSubscription.RawGlobalGroup

    var highlighted by remember { mutableStateOf(false) }
    if (focusGroupFlow != null) {
        val focusGroup by focusGroupFlow.collectAsState()
        if (subs.id == focusGroup?.first && appId == focusGroup?.second && group.key == focusGroup?.third) {
            LaunchedEffect(isSelectedMode) {
                if (isSelectedMode) {
                    highlighted = false
                    focusGroupFlow.value = null
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
                focusGroupFlow.value = null
            }
        }
    }

    val (checked, excludeData) = if (inGlobalAppPage) {
        val excludeData = remember(subsConfig?.exclude) {
            ExcludeData.parse(subsConfig?.exclude)
        }
        getGlobalGroupChecked(subs, excludeData, group, appId) to excludeData
    } else {
        getGroupEnable(
            group,
            subsConfig,
            category,
            categoryConfig
        ) to null
    }
    val onCheckedChange = appScope.launchAsFn<Boolean> { newChecked ->
        val newConfig = if (appId != null) {
            if (group is RawSubscription.RawGlobalGroup) {
                // APP 汇总页面 - 全局规则
                val excludeData = ExcludeData.parse(subsConfig?.exclude)
                (subsConfig ?: SubsConfig(
                    type = SubsConfig.GlobalGroupType,
                    subsId = subs.id,
                    groupKey = group.key,
                )).copy(
                    exclude = excludeData.copy(
                        appIds = excludeData.appIds.toMutableMap().apply {
                            set(appId, !newChecked)
                        }
                    ).stringify()
                )
            } else {
                // 订阅详情页面 - APP 规则
                (subsConfig?.copy(enable = newChecked) ?: SubsConfig(
                    type = SubsConfig.AppGroupType,
                    subsId = subs.id,
                    appId = appId,
                    groupKey = group.key,
                    enable = newChecked
                ))
            }
        } else {
            // 订阅详情页面 - 全局规则
            group as RawSubscription.RawGlobalGroup
            (subsConfig?.copy(enable = newChecked) ?: SubsConfig(
                type = SubsConfig.GlobalGroupType,
                subsId = subs.id,
                groupKey = group.key,
                enable = newChecked
            ))
        }
        DbSet.subsConfigDao.insert(newConfig)
    }
    val onClick = if (isSelectedMode)
        (onSelectedChange)
    else throttle(mainVm.viewModelScope.launchAsFn(Dispatchers.Default) {
        group.cacheStr // load cache
        mainVm.ruleGroupState.showGroupFlow.value = ShowGroupState(
            subsId = subs.id,
            appId = if (group is RawSubscription.RawAppGroup) appId else null,
            groupKey = group.key,
            pageAppId = appId,
        )
    })
    val horizontal = 8.dp
    val vertical = 8.dp
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
            .padding(
                start = 8.dp,
                end = 8.dp,
                bottom = if (showBottom) 4.dp else 0.dp
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.extraSmall,
        colors = CardDefaults.cardColors(
            containerColor = containerColor.value
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = horizontal, top = vertical, bottom = vertical),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                GroupNameText(
                    modifier = Modifier.fillMaxWidth(),
                    text = group.name,
                    style = MaterialTheme.typography.bodyLarge,
                    isGlobal = group is RawSubscription.RawGlobalGroup,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    clickDisabled = isSelectedMode,
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
            Spacer(modifier = Modifier.width(8.dp))
            key(subs.id, appId, group.key) {
                val percent = usePercentAnimatable(!isSelectedMode)
                val switchModifier = Modifier.graphicsLayer(
                    alpha = 0.5f + (1 - 0.5f) * percent.value,
                )
                if (!group.valid) {
                    InnerDisableSwitch(
                        modifier = switchModifier,
                        valid = false,
                        isSelectedMode = isSelectedMode,
                    )
                } else if (checked != null) {
                    Switch(
                        modifier = switchModifier.minimumInteractiveComponentSize(),
                        checked = checked,
                        onCheckedChange = if (isSelectedMode) null else throttle(onCheckedChange)
                    )
                } else {
                    InnerDisableSwitch(
                        modifier = switchModifier,
                        isSelectedMode = isSelectedMode,
                    )
                }
                val visible = if (inGlobalAppPage) {
                    excludeData != null && excludeData.appIds.contains(appId)
                } else {
                    subsConfig?.enable != null
                }
                CardFlagBar(visible = visible, width = horizontal)
            }
        }
    }
}


@Composable
fun BatchActionButtonGroup(vm: ViewModel, selectedDataSet: Set<ShowGroupState>) {
    val mainVm = LocalMainViewModel.current
    IconButton(onClick = throttle(vm.viewModelScope.launchAsFn(Dispatchers.Default) {
        mainVm.dialogFlow.waitResult(
            title = "操作提示",
            text = "是否将所选规则全部关闭?\n\n注: 也可在「订阅-规则类别」操作"
        )
        val list = batchUpdateGroupEnable(selectedDataSet, false)
        if (list.isNotEmpty()) {
            toast("已关闭 ${list.size} 条规则")
        } else {
            toast("无规则被改变")
        }
    })) {
        Icon(
            imageVector = Icons.Outlined.ToggleOff,
            contentDescription = null,
        )
    }
    IconButton(onClick = throttle(vm.viewModelScope.launchAsFn(Dispatchers.Default) {
        mainVm.dialogFlow.waitResult(
            title = "操作提示",
            text = "是否将所选规则全部启用?\n\n注: 也可在「订阅-规则类别」操作"
        )
        val list = batchUpdateGroupEnable(selectedDataSet, true)
        if (list.isNotEmpty()) {
            toast("已启用 ${list.size} 条规则")
        } else {
            toast("无规则被改变")
        }
    })) {
        Icon(
            imageVector = Icons.Outlined.ToggleOn,
            contentDescription = null,
        )
    }
    IconButton(onClick = throttle(vm.viewModelScope.launchAsFn(Dispatchers.Default) {
        mainVm.dialogFlow.waitResult(
            title = "操作提示",
            text = "是否将所选规则重置开关至初始状态?\n\n注: 也可在「订阅-规则类别」操作"
        )
        val list = batchUpdateGroupEnable(selectedDataSet, null)
        if (list.isNotEmpty()) {
            toast("已重置 ${list.size} 条规则开关至初始状态")
        } else {
            toast("无规则被改变")
        }
    })) {
        Icon(
            imageVector = ResetSettings,
            contentDescription = null,
        )
    }
}
