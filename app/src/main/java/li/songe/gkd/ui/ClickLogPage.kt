package li.songe.gkd.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import li.songe.gkd.util.navigate
import li.songe.gkd.data.ClickLog
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.SimpleTopAppBar
import li.songe.gkd.ui.destinations.AppItemPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.format
import li.songe.gkd.util.launchAsFn

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun ClickLogPage() {
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current

    val vm = hiltViewModel<ClickLogVm>()
    val clickLogs by vm.clickLogsFlow.collectAsState()
    val clickLogCount by vm.clickLogCountFlow.collectAsState()
    val appInfoCache by appInfoCacheFlow.collectAsState()

    var previewClickLog by remember {
        mutableStateOf<ClickLog?>(null)
    }

    Scaffold(topBar = {
        SimpleTopAppBar(
            onClickIcon = { navController.popBackStack() },
            title = "点击记录" + if (clickLogCount <= 0) "" else ("-$clickLogCount")
        )
    }, content = { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(10.dp, 0.dp, 10.dp, 0.dp)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(5.dp))
            }
            items(clickLogs, { triggerLog -> triggerLog.id }) { triggerLog ->
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, Color.Black))
                    .clickable {
                        previewClickLog = triggerLog
                    }) {
                    Row {
                        Text(
                            text = triggerLog.id.format("yyyy-MM-dd HH:mm:ss"),
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = appInfoCache[triggerLog.appId]?.name ?: triggerLog.appId ?: ""
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = triggerLog.activityId ?: "")
                    val group = vm.getGroup(triggerLog)
                    if (group?.name != null) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = group.name)
                    }
                    val rule = group?.rules?.run {
                        find { r -> r.key == triggerLog.ruleKey } ?: getOrNull(triggerLog.ruleIndex)
                    }
                    if (rule?.name != null) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = rule.name)
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    })

    previewClickLog?.let { previewTriggerLogVal ->
        Dialog(onDismissRequest = { previewClickLog = null }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .width(250.dp)
                    .background(Color.White)
                    .padding(8.dp)
            ) {
                Text(text = "查看规则组", modifier = Modifier
                    .clickable {
                        previewTriggerLogVal.appId ?: return@clickable
                        navController.navigate(
                            AppItemPageDestination(
                                previewTriggerLogVal.subsId,
                                previewTriggerLogVal.appId,
                                previewTriggerLogVal.groupKey
                            )
                        )
                        previewClickLog = null
                    }
                    .fillMaxWidth()
                    .padding(10.dp))
                Text(text = "删除", modifier = Modifier
                    .clickable(onClick = scope.launchAsFn {
                        previewClickLog = null
                        DbSet.clickLogDb
                            .clickLogDao()
                            .delete(previewTriggerLogVal)
                    })
                    .fillMaxWidth()
                    .padding(10.dp))
            }
        }
    }

}