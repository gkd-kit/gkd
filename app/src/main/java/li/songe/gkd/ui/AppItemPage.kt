package li.songe.gkd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.AlertDialog
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.SimpleTopAppBar
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.launchAsFn

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun AppItemPage(
    subsItemId: Long,
    appId: String,
    focusGroupKey: Int? = null, // 背景/边框高亮一下
) {
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val vm = hiltViewModel<AppItemVm>()
    val subsItem by vm.subsItemFlow.collectAsState()
    val subsConfigs by vm.subsConfigsFlow.collectAsState()
    val subsApp by vm.subsAppFlow.collectAsState()
    val appInfoCache by appInfoCacheFlow.collectAsState()

    var showGroupItem: SubscriptionRaw.GroupRaw? by remember { mutableStateOf(null) }


    Scaffold(topBar = {
        SimpleTopAppBar(
            onClickIcon = { navController.popBackStack() },
            title = if (subsItem == null) "订阅文件缺失" else (appInfoCache[subsApp?.id]?.name
                ?: subsApp?.name ?: subsApp?.id ?: "")
        )
    }, content = { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
            }
            subsApp?.groups?.let { groupsVal ->
                itemsIndexed(groupsVal, { i, g -> i.toString() + g.key }) { _, group ->
                    Row(
                        modifier = Modifier
                            .background(
                                if (group.key == focusGroupKey) Color(0x500a95ff) else Color.Transparent
                            )
                            .clickable { showGroupItem = group }
                            .padding(10.dp, 6.dp)
                            .fillMaxWidth()
                            .height(45.dp),
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
                                text = group.name ?: "-",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (group.valid) {
                                Text(
                                    text = group.desc ?: "-",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = "规则组损坏",
                                    color = Color.Red,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        val subsConfig = subsConfigs.find { it.groupKey == group.key }
                        Switch(checked = (subsConfig?.enable ?: group.enable) ?: true,
                            modifier = Modifier,
                            onCheckedChange = scope.launchAsFn { enable ->
                                val newItem = (subsConfig?.copy(enable = enable) ?: SubsConfig(
                                    type = SubsConfig.GroupType,
                                    subsItemId = subsItemId,
                                    appId = appId,
                                    groupKey = group.key,
                                    enable = enable
                                ))
                                DbSet.subsConfigDao.insert(newItem)
                            })
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    })


    showGroupItem?.let { showGroupItemVal ->
        AlertDialog(onDismissRequest = { showGroupItem = null }, title = {
            Text(text = showGroupItemVal.name ?: "-")
        }, text = {
            Text(text = showGroupItemVal.desc ?: "-")
        }, buttons = { })
    }
}

