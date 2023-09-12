package li.songe.gkd.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import com.ramcosta.composedestinations.navigation.navigate
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.SimpleTopAppBar
import li.songe.gkd.ui.component.SubsAppCard
import li.songe.gkd.ui.destinations.AppItemPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.ProfileTransitions
import li.songe.gkd.util.SafeR
import li.songe.gkd.util.appInfoCacheFlow
import li.songe.gkd.util.formatTimeAgo
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.subsIdToRawFlow

@RootNavGraph
@Destination(style = ProfileTransitions::class)
@Composable
fun SubsPage(
    subsItemId: Long,
) {
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val context = LocalContext.current

    val vm = hiltViewModel<SubsVm>()
    val subsItem by vm.subsItemFlow.collectAsState()
    val subsIdToRaw by subsIdToRawFlow.collectAsState()
    val appAndConfigs by vm.appAndConfigsFlow.collectAsState()
    val appInfoCache by appInfoCacheFlow.collectAsState()

    val subsRaw = subsIdToRaw[subsItem?.id]

    var showDetailDlg by remember {
        mutableStateOf(false)
    }

    Scaffold(
        topBar = {
            SimpleTopAppBar(onClickIcon = { navController.popBackStack() },
                title = subsRaw?.name ?: "",
                actions = {
                    IconButton(onClick = {
                        if (subsRaw != null) {
                            showDetailDlg = true
                        }
                    }) {
                        Icon(
                            painter = painterResource(SafeR.ic_info),
                            contentDescription = "info",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                })
        },
    ) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.padding(padding)
        ) {
            itemsIndexed(appAndConfigs, { i, a -> i.toString() + a.t0.id }) { _, a ->
                val (appRaw, subsConfig, enableSize) = a
                SubsAppCard(appRaw = appRaw,
                    appInfo = appInfoCache[appRaw.id],
                    subsConfig = subsConfig,
                    enableSize=enableSize,
                    onClick = {
                        navController.navigate(AppItemPageDestination(subsItemId, appRaw.id))
                    },
                    onValueChange = scope.launchAsFn { enable ->
                        val newItem = subsConfig?.copy(
                            enable = enable
                        ) ?: SubsConfig(
                            enable = enable,
                            type = SubsConfig.AppType,
                            subsItemId = subsItemId,
                            appId = appRaw.id,
                        )
                        DbSet.subsConfigDao.insert(newItem)
                    })
            }
            item {
                if (appAndConfigs.isEmpty()) {
                    Spacer(modifier = Modifier.height(40.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = "此订阅文件暂无规则")
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(20.dp))
            }

        }
    }

    if (showDetailDlg && subsRaw != null && subsItem != null) {
        AlertDialog(onDismissRequest = { showDetailDlg = false }, title = {
            Text(text = "订阅详情")
        }, text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier
            ) {
                Text(text = "名称: " + subsRaw.name)
                Text(text = "版本: " + subsRaw.version)
                if (subsRaw.author != null) {
                    Text(text = "作者: " + subsRaw.author)
                }
                val apps = subsRaw.apps
                val groupsSize = apps.sumOf { it.groups.size }
                if (groupsSize > 0) {
                    Text(text = "规则: ${apps.size}应用/${groupsSize}规则组")
                }
                Text(text = "更新: " + formatTimeAgo(subsItem!!.mtime))
            }
        }, confirmButton = {
            if (subsRaw.supportUri != null) {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW, Uri.parse(subsRaw.supportUri)
                        )
                    )
                }) {
                    Text(text = "问题反馈")
                }
            }
        })
    }
}