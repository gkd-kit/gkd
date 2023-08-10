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
import androidx.compose.foundation.lazy.itemsIndexed
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import li.songe.gkd.data.SubsItem
import li.songe.gkd.data.TriggerLog
import li.songe.gkd.data.getAppInfo
import li.songe.gkd.data.getAppName
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.SimpleTopAppBar
import li.songe.gkd.ui.destinations.AppItemPageDestination
import li.songe.gkd.util.LaunchedEffectTry
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.Singleton
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.rememberCache
import com.ramcosta.composedestinations.navigation.navigate
import li.songe.gkd.util.format

@RootNavGraph
@Destination
@Composable
fun RecordPage() {
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current

    val vm = hiltViewModel<RecordVm>()
    val triggerLogs by vm.triggerLogsFlow.collectAsState()
    val subItems by vm.subItemsFlow.collectAsState(initial = emptyList())

    val groups = remember(triggerLogs, subItems) {
        triggerLogs.map { logWrapper ->
            val sub = subItems.find { sub -> sub.id == logWrapper.subsId } ?: return@map null
            val app = sub.subscriptionRaw?.apps?.find { app -> app.id == logWrapper.appId }
            app?.groups?.find { group -> group.key == logWrapper.groupKey }
        }
    }
    val rules = remember(groups) {
        groups.mapIndexed { index, groupRaw ->
            groupRaw ?: return@mapIndexed null
            val log = triggerLogs.getOrNull(index)
            log?.run {
                if (ruleKey != null) {
                    groupRaw.rules.find { r -> r.key == ruleKey }?.let { return@mapIndexed it }
                }
                groupRaw.rules.getOrNull(ruleIndex)
            }
        }
    }
    var previewTriggerLog by remember {
        mutableStateOf<TriggerLog?>(null)
    }

    Scaffold(topBar = {
        SimpleTopAppBar(
            onClickIcon = { navController.popBackStack() },
            title = "触发记录" + if (triggerLogs.isEmpty()) "" else ("-" + triggerLogs.size.toString())
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
            itemsIndexed(triggerLogs) { index, triggerLog ->
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, Color.Black))
                    .clickable {
                        previewTriggerLog = triggerLog
                    }) {
                    Row {
                        Text(
                            text = triggerLog.id.format("yyyy-MM-dd HH:mm:ss"),
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = getAppName(triggerLog.appId) ?: triggerLog.appId ?: ""
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = triggerLog.activityId ?: "")
                    groups.getOrNull(index)?.name?.let { groupName ->
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = groupName)
                    }
                    rules.getOrNull(index)?.name?.let { ruleName ->
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = ruleName)
                    }
                    rules.getOrNull(index)?.matches?.lastOrNull()?.let { matchText ->
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = matchText)
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    })

    previewTriggerLog?.let { previewTriggerLogVal ->
        Dialog(onDismissRequest = { previewTriggerLog = null }) {
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
                        previewTriggerLog = null
                    }
                    .fillMaxWidth()
                    .padding(10.dp))
                Text(text = "删除", modifier = Modifier
                    .clickable(onClick = scope.launchAsFn {
                        previewTriggerLog = null
                        DbSet.triggerLogDb
                            .triggerLogDao()
                            .delete(previewTriggerLogVal)
                    })
                    .fillMaxWidth()
                    .padding(10.dp))
            }
        }
    }

}