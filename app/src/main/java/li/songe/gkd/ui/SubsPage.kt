package li.songe.gkd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import com.ramcosta.composedestinations.navigation.navigate
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.getAppInfo
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.SimpleTopAppBar
import li.songe.gkd.ui.component.SubsAppCard
import li.songe.gkd.ui.destinations.AppItemPageDestination
import li.songe.gkd.util.LocalNavController
import li.songe.gkd.util.launchAsFn
import java.text.Collator
import java.util.Locale

@RootNavGraph
@Destination
@Composable
fun SubsPage(
    subsItemId: Long,
) {
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current

    val vm = hiltViewModel<SubsVm>()
    val subsItem by vm.subsItemFlow.collectAsState()
    val subsConfigs by vm.subsConfigsFlow.collectAsState(initial = emptyList())

    val orderedApps by remember(subsItem) {
        derivedStateOf {
            (subsItem?.subscriptionRaw?.apps ?: emptyList()).sortedWith { a, b ->
                Collator.getInstance(Locale.CHINESE)
                    .compare(getAppInfo(a.id).realName, getAppInfo(b.id).realName)
            }
        }
    }
//    val openAppPage = scope.useTask().launchAsFn<SubsAppCardData> {
//        navController.navigate(AppItemPageDestination(it.subsConfig.subsItemId, it.appRaw.id))
//    }
    Scaffold(
        topBar = {
            SimpleTopAppBar(
                onClickIcon = { navController.popBackStack() }, title = subsItem?.name ?: ""
            )
//            右上角菜单显示关于 dialog 一级属性
        },
    ) { padding ->
        subsItem?.subscriptionRaw?.let {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.padding(padding)
            ) {
                items(orderedApps, { it.id }) { appRaw ->
                    val subsConfig = subsConfigs.find { s -> s.appId == appRaw.id }
                    SubsAppCard(appRaw = appRaw, subsConfig = subsConfig, onClick = {
                        navController.navigate(AppItemPageDestination(subsItemId, appRaw.id))
                    }, onValueChange = scope.launchAsFn { enable ->
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
                item(null) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}