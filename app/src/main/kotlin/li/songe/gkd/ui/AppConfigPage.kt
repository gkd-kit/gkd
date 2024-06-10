package li.songe.gkd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.coroutines.flow.update
import li.songe.gkd.data.ExcludeData
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.stringify
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.destinations.AppItemPageDestination
import li.songe.gkd.ui.destinations.GlobalRulePageDestination
import li.songe.gkd.ui.style.itemPadding
import li.songe.gkd.ui.style.menuPadding
import li.songe.gkd.util.LOCAL_SUBS_ID
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.RuleSortOption
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.navigate

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun AppConfigPage(appId: String) {
    val navController = LocalNavController.current
    val vm = hiltViewModel<AppConfigVm>()
    val ruleSortType by vm.ruleSortTypeFlow.collectAsState()
    val appInfoCache by appInfoCacheFlow.collectAsState()
    val appInfo = appInfoCache[appId]
    val globalGroups by vm.globalGroupsFlow.collectAsState()
    val appGroups by vm.appGroupsFlow.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var expanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var isFirstVisit by remember { mutableStateOf(true) }
    LaunchedEffect(globalGroups.size, appGroups.size, ruleSortType.value) {
        if (isFirstVisit) {
            isFirstVisit = false
        } else {
            listState.scrollToItem(0)
        }
    }
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
            }, actions = {
                IconButton(onClick = {
                    expanded = true
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                    )
                }
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart)
                ) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        Text(
                            text = "排序",
                            modifier = Modifier.menuPadding(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        RuleSortOption.allSubObject.forEach { s ->
                            DropdownMenuItem(
                                text = {
                                    Text(s.label)
                                },
                                trailingIcon = {
                                    RadioButton(
                                        selected = ruleSortType == s,
                                        onClick = {
                                            vm.ruleSortTypeFlow.update { s }
                                        }
                                    )
                                },
                                onClick = {
                                    vm.ruleSortTypeFlow.update { s }
                                },
                            )
                        }
                    }
                }
            })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    navController.navigate(AppItemPageDestination(LOCAL_SUBS_ID, appId))
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
            modifier = Modifier.padding(contentPadding),
            state = listState,
        ) {
            items(globalGroups) { g ->
                val excludeData = remember(g.config?.exclude) {
                    ExcludeData.parse(g.config?.exclude)
                }
                val checked = getChecked(excludeData, g.group, appId, appInfo)
                AppGroupCard(
                    group = g.group,
                    checked = checked,
                    onClick = {
                        navController.navigate(
                            GlobalRulePageDestination(
                                g.subsItem.id,
                                g.group.key
                            )
                        )
                    }
                ) { newChecked ->
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
            item {
                if (globalGroups.isNotEmpty() && appGroups.isNotEmpty()) {
                    HorizontalDivider()
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
    checked: Boolean?,
    onClick: () -> Unit,
    onCheckedChange: ((Boolean) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .itemPadding(),
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
                style = MaterialTheme.typography.bodyLarge
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
                } else {
                    Text(
                        text = "暂无描述",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            } else {
                Text(
                    text = "非法选择器",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        if (checked != null) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        } else {
            Switch(
                checked = false,
                enabled = false,
                onCheckedChange = onCheckedChange
            )
        }
    }
}