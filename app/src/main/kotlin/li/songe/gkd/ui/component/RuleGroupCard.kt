package li.songe.gkd.ui.component

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import li.songe.gkd.MainActivity
import li.songe.gkd.appScope
import li.songe.gkd.data.CategoryConfig
import li.songe.gkd.data.ExcludeData
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.getChecked
import li.songe.gkd.ui.style.itemFlagPadding
import li.songe.gkd.ui.style.itemHorizontalPadding
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.getGroupEnable
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle


@Composable
fun RuleGroupCard(
    modifier: Modifier = Modifier,
    subs: RawSubscription,
    appId: String?,
    group: RawSubscription.RawGroupProps,
    subsConfig: SubsConfig?,
    category: RawSubscription.RawCategory?,
    categoryConfig: CategoryConfig?,
    highlighted: Boolean,
) {
    val context = LocalActivity.current as MainActivity

    val inGlobalAppPage = appId != null && group is RawSubscription.RawGlobalGroup

    val (checked, excludeData) = if (inGlobalAppPage) {
        val appInfo = appInfoCacheFlow.collectAsState().value[appId]
        val excludeData = remember(subsConfig?.exclude) {
            ExcludeData.parse(subsConfig?.exclude)
        }
        getChecked(excludeData, group, appId, appInfo) to excludeData
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
                    subsItemId = subs.id,
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
                    subsItemId = subs.id,
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
                subsItemId = subs.id,
                groupKey = group.key,
                enable = newChecked
            ))
        }
        DbSet.subsConfigDao.insert(newConfig)
    }
    val onClick = context.mainVm.viewModelScope.launchAsFn(Dispatchers.Default) {
        group.cacheStr // load cache
        context.mainVm.ruleGroupState.showGroupFlow.value = ShowGroupState(
            subsId = subs.id,
            appId = if (group is RawSubscription.RawAppGroup) appId else null,
            groupKey = group.key,
            subsConfig = subsConfig,
            pageAppId = appId,
        )
    }
    Row(
        modifier = modifier
            .let {
                if (highlighted) {
                    it.background(MaterialTheme.colorScheme.inversePrimary)
                } else {
                    it
                }
            }
            .clickable(onClick = throttle(onClick))
            .itemFlagPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = group.name,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
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
            if (!group.valid) {
                InnerDisableSwitch(valid = false)
            } else if (checked != null) {
                // 避免在 LazyColumn 滑动中出现 Switch 切换动画
                Switch(
                    checked = checked,
                    onCheckedChange = throttle(onCheckedChange)
                )
            } else {
                InnerDisableSwitch()
            }
            CardFlagBar(
                visible = if (inGlobalAppPage) {
                    excludeData != null && excludeData.appIds.contains(appId)
                } else {
                    subsConfig?.enable != null
                }
            )
        }
    }
}


@Composable
fun InnerDisableSwitch(
    valid: Boolean = true,
) {
    val context = LocalActivity.current as MainActivity
    val onClick = {
        if (valid) {
            context.mainVm.dialogFlow.updateDialogOptions(
                title = "内置禁用",
                text = "此规则组已经在其 apps 字段中配置对当前应用的禁用, 因此无法手动开启规则组\n\n提示: 这种情况一般在此全局规则无法适配/跳过适配/单独适配当前应用时出现",
            )
        } else {
            context.mainVm.dialogFlow.updateDialogOptions(
                title = "非法规则",
                text = "规则存在错误, 无法启用",
            )
        }
    }
    Switch(
        checked = false,
        enabled = false,
        onCheckedChange = null,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = throttle(onClick)
        )
    )
}

@Composable
fun CardFlagBar(visible: Boolean) {
    Row(
        modifier = Modifier
            .width(itemHorizontalPadding)
            .height(20.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        AnimatedVisibility(
            visible = visible,
        ) {
            Spacer(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.tertiary)
                    .fillMaxHeight()
                    .width(2.dp)
            )
        }
    }
}
