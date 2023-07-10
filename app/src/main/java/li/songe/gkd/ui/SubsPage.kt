package li.songe.gkd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.material.fade
import com.google.accompanist.placeholder.material.placeholder
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import com.ramcosta.composedestinations.navigation.navigate
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.withContext
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.SubsItem
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.data.getAppInfo
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.SubsAppCard
import li.songe.gkd.ui.component.SubsAppCardData
import li.songe.gkd.ui.destinations.AppItemPageDestination
import li.songe.gkd.utils.LaunchedEffectTry
import li.songe.gkd.utils.LocalNavController
import li.songe.gkd.utils.launchAsFn
import li.songe.gkd.utils.rememberCache
import li.songe.gkd.utils.useTask

@RootNavGraph
@Destination
@Composable
fun SubsPage(
    subsItem: SubsItem
) {
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current

    var sub: SubscriptionRaw? by rememberCache { mutableStateOf(null) }
    var subsAppCards: List<SubsAppCardData>? by rememberCache { mutableStateOf(null) }

    LaunchedEffectTry(Unit) {
        scope.launchAsFn {  }
        val newSub = if (sub === null) {
            SubscriptionRaw.parse5(subsItem.subsFile.readText()).apply {
                withContext(IO) {
                    apps.forEach {
                        getAppInfo(it.id)
                    }
                }
            }
        } else {
            sub!!
        }
        sub = newSub
        DbSet.subsConfigDao.queryAppTypeConfig(subsItem.id).flowOn(IO).cancellable().collect {
            val mutableSet = it.toMutableSet()
            val newSubsAppCards = newSub.apps.map { appRaw ->
                mutableSet.firstOrNull { v ->
                    v.appId == appRaw.id
                }.apply {
                    mutableSet.remove(this)
                } ?: SubsConfig(
                    subsItemId = subsItem.id,
                    appId = appRaw.id,
                    type = SubsConfig.AppType
                )
            }.mapIndexed { index, subsConfig ->
                SubsAppCardData(
                    subsConfig,
                    newSub.apps[index]
                )
            }
            subsAppCards = newSubsAppCards
        }
    }

    val openAppPage = scope.useTask().launchAsFn<SubsAppCardData> {
        navController.navigate(AppItemPageDestination(it.appRaw, it.subsConfig))
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxSize()
    ) {
        item {
            val textModifier = Modifier
                .fillMaxWidth()
                .placeholder(visible = sub == null, highlight = PlaceholderHighlight.fade())
            Column(
                modifier = Modifier.padding(10.dp, 0.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "作者: " + (sub?.author ?: "未知"),
                    modifier = textModifier
                )
                Text(
                    text = "版本: ${sub?.version}",
                    modifier = textModifier
                )
                Text(
                    text = "描述: ${sub?.name}",
                    modifier = textModifier
                )
            }
        }
        subsAppCards?.let { subsAppCardsVal ->
            items(subsAppCardsVal.size) { i ->
                SubsAppCard(
                    sub = subsAppCardsVal[i],
                    onClick = {
                        openAppPage(subsAppCardsVal[i])
                    },
                    onValueChange = scope.launchAsFn { enable ->
                        val newItem = subsAppCardsVal[i].subsConfig.copy(
                            enable = enable
                        )
                        DbSet.subsConfigDao.insert(newItem)
                    }
                )
            }
        }
//        if (subsAppCards == null) {
//            items(placeholderList.size) { i ->
//                Box(
//                    modifier = Modifier
//                        .wrapContentSize()
//                ) {
//                    SubsAppCard(loading = true, sub = placeholderList[i])
//                    Text(text = "")
//                }
//            }
//        }

        item(true) {
            Spacer(modifier = Modifier.height(10.dp))
        }
    }

}