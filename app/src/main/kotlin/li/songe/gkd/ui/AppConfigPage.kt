package li.songe.gkd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import li.songe.gkd.data.ExcludeData
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.stringify
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.destinations.AppItemPageDestination
import li.songe.gkd.ui.destinations.GlobalRulePageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.navigate
import li.songe.gkd.util.ruleSummaryFlow
import li.songe.gkd.util.toast

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun AppConfigPage(appId: String) {
    val navController = LocalNavController.current
    val vm = hiltViewModel<AppConfigVm>()
    val appInfoCache by appInfoCacheFlow.collectAsState()
    val appInfo = appInfoCache[appId]
    val ruleSummary by ruleSummaryFlow.collectAsState()
    val globalGroups = ruleSummary.globalGroups
    val appGroups = ruleSummary.appIdToAllGroups[appId] ?: emptyList()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                IconButton(onClick = {
                    navController.popBackStack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
            }, title = {
                Text(
                    text = appInfo?.name ?: appId,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }, actions = {})
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    navController.navigate(AppItemPageDestination(-2, appId))
                },
                content = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                    )
                }
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.padding(contentPadding)
        ) {
            items(globalGroups) { g ->
                val excludeData = remember(g.config?.exclude) {
                    ExcludeData.parse(g.config?.exclude)
                }
                val checked = getChecked(excludeData, g.group, appId, appInfo)
                AppGroupCard(g.group, checked ?: false, onClick = {
                    navController.navigate(GlobalRulePageDestination(g.subsItem.id, g.group.key))
                }) { newChecked ->
                    if (checked == null) {
                        toast("内置禁用,不可修改")
                        return@AppGroupCard
                    }
                    vm.viewModelScope.launchTry {
                        DbSet.subsConfigDao.insert(
                            (g.config ?: SubsConfig(
                                type = SubsConfig.GlobalGroupType,
                                subsItemId = g.subsItem.id,
                                groupKey = g.group.key,
                            )).copy(
                                exclude = excludeData.copy(
                                    appIds = excludeData.appIds.toMutableMap().apply {
                                        set(appId, !newChecked)
                                    }
                                ).stringify()
                            )
                        )
                    }
                }
            }
            items(appGroups) { g ->
                AppGroupCard(g.group, g.enable, onClick = {
                    navController.navigate(
                        AppItemPageDestination(
                            g.subsItem.id,
                            appId,
                            g.group.key,
                        )
                    )
                }) {
                    vm.viewModelScope.launchTry {
                        DbSet.subsConfigDao.insert(
                            g.config?.copy(enable = it) ?: SubsConfig(
                                type = SubsConfig.AppGroupType,
                                subsItemId = g.subsItem.id,
                                appId = appId,
                                groupKey = g.group.key,
                                enable = it
                            )
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(40.dp))
                if (globalGroups.size + appGroups.size == 0) {
                    Text(
                        text = "暂无规则",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else {
                    // 避免被 floatingActionButton 遮挡
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
private fun AppGroupCard(
    group: RawSubscription.RawGroupProps,
    enable: Boolean,
    onClick: () -> Unit,
    onCheckedChange: ((Boolean) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(10.dp, 6.dp),
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
                modifier = Modifier.fillMaxWidth()
            )
            if (group.valid) {
                if (!group.desc.isNullOrBlank()) {
                    Text(
                        text = group.desc!!,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        text = "暂无描述",
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 14.sp,
                        color = LocalContentColor.current.copy(alpha = 0.5f)
                    )
                }
            } else {
                Text(
                    text = "非法选择器",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(checked = enable, modifier = Modifier, onCheckedChange = onCheckedChange)
    }
}