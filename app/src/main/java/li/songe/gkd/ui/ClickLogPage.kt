package li.songe.gkd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.coroutines.Dispatchers
import li.songe.gkd.data.ClickLog
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.SimpleTopAppBar
import li.songe.gkd.ui.destinations.AppItemPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.format
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.navigate

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
    var showDeleteDlg by remember {
        mutableStateOf(false)
    }

    Scaffold(topBar = {
        SimpleTopAppBar(onClickIcon = { navController.popBackStack() },
            title = "点击记录" + if (clickLogCount <= 0) "" else ("-$clickLogCount"),
            actions = {
                if (clickLogs.isNotEmpty()) {
                    IconButton(onClick = { showDeleteDlg = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            })
    }, content = { contentPadding ->
        if (clickLogs.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.padding(contentPadding),
            ) {
                item {
                    Spacer(modifier = Modifier.height(5.dp))
                }
                items(clickLogs, { triggerLog -> triggerLog.id }) { triggerLog ->
                    Column(modifier = Modifier
                        .clickable {
                            previewClickLog = triggerLog
                        }
                        .fillMaxWidth()
                        .padding(10.dp)) {
                        Row {
                            Text(
                                text = triggerLog.id.format("MM-dd HH:mm:ss"),
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = appInfoCache[triggerLog.appId]?.name ?: triggerLog.appId
                                ?: ""
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
                            find { r -> r.key == triggerLog.ruleKey }
                                ?: getOrNull(triggerLog.ruleIndex)
                        }
                        if (rule?.name != null) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = rule.name)
                        }
                    }
                    Divider()
                }
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(30.dp))
                Text(text = "暂无记录")

            }
        }
    })

    previewClickLog?.let { previewTriggerLogVal ->
        Dialog(onDismissRequest = { previewClickLog = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column {
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
                        .padding(16.dp))
                    Text(
                        text = "删除",
                        modifier = Modifier
                            .clickable(onClick = scope.launchAsFn {
                                previewClickLog = null
                                DbSet.clickLogDao.delete(previewTriggerLogVal)
                            })
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

        }
    }

    if (showDeleteDlg) {
        AlertDialog(onDismissRequest = { showDeleteDlg = false },
            title = { Text(text = "是否删除全部点击记录?") },
            confirmButton = {
                TextButton(onClick = scope.launchAsFn(Dispatchers.IO) {
                    showDeleteDlg = false
                    DbSet.clickLogDao.deleteAll()
                }) {
                    Text(text = "是", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDlg = false
                }) {
                    Text(text = "否")
                }
            })
    }

}