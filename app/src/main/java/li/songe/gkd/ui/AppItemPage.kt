package li.songe.gkd.ui

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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.material.fade
import com.google.accompanist.placeholder.material.placeholder
import kotlinx.coroutines.delay
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.table.SubsConfig
import li.songe.gkd.db.util.Operator.eq
import li.songe.gkd.db.util.RoomX
import li.songe.gkd.ui.component.StatusBar
import li.songe.gkd.util.ThrottleState
import li.songe.router.LocalRoute
import li.songe.router.LocalRouter
import li.songe.router.Page

data class AppItemPageParams(
    val subsApp: SubscriptionRaw.AppRaw,
    val subsConfig: SubsConfig,
    val appName: String,
)

val AppItemPage = Page {

//        https://developer.android.com/jetpack/compose/modifiers-list

    val router = LocalRouter.current

    val params = LocalRoute.current.data as AppItemPageParams

    val scope = rememberCoroutineScope()
//        val context = LocalContext.current

    var subsConfigList: List<SubsConfig?>? by remember { mutableStateOf(null) }

    val changeItemThrottle = ThrottleState.use(scope)

    LaunchedEffect(Unit) {
        delay(400)
        val config = params.subsConfig
        val mutableSet =
            RoomX.select { (SubsConfig::type eq SubsConfig.GroupType) and (SubsConfig::subsItemId eq config.subsItemId) and (SubsConfig::appId eq config.appId) }
                .toMutableSet()
        val list = mutableListOf<SubsConfig?>()
        params.subsApp.groups.forEach { group ->
            if (group.key == null) {
                list.add(null)
            } else {
                val item = mutableSet.find { s -> s.groupKey == group.key } ?: SubsConfig(
                    subsItemId = config.subsItemId,
                    appId = config.appId,
                    groupKey = group.key,
                    type = SubsConfig.GroupType
                )
                list.add(item)
            }
        }
        subsConfigList = list
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column {
                StatusBar()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp, 0.dp)
                ) {
                    Text(
                        text = params.appName,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = params.subsApp.id,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        items(params.subsApp.groups.size) { i ->
            val group = params.subsApp.groups[i]
            Row(
                modifier = Modifier
                    .clickable {
//                        router.navigate(
//                            GroupItemPage, GroupItemPage.Params(
//                                group = group,
//                                subsConfig = subsConfigList?.get(i),
//                                appName = params.appName
//                            )
//                        )
                    }
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
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                    Text(
                        text = group.activityIds?.joinToString() ?: "",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                if (group.key != null) {
                    val crPx = with(LocalDensity.current) { 4.dp.toPx() }
                    Switch(
                        checked = subsConfigList?.get(i)?.enable ?: true,
                        modifier = Modifier
                            .placeholder(
                                subsConfigList == null,
                                highlight = PlaceholderHighlight.fade(),
                                shape = GenericShape { size, _ ->
                                    val cr = CornerRadius(crPx, crPx)
                                    addRoundRect(
                                        RoundRect(
                                            left = 0f,
                                            top = size.height * .25f,
                                            right = size.width,
                                            bottom = size.height * .75f,
                                            topLeftCornerRadius = cr,
                                            topRightCornerRadius = cr,
                                            bottomLeftCornerRadius = cr,
                                            bottomRightCornerRadius = cr,
                                        )
                                    )
                                }
                            ),
//                        当 onCheckedChange 是 null 时, size 是长方形, 反之是 正方形
                        onCheckedChange = changeItemThrottle.invoke { enable ->
                            val list = subsConfigList ?: return@invoke
                            val newItem = list[i]?.copy(enable = enable) ?: return@invoke
                            if (newItem.id == 0L) {
                                RoomX.insert(newItem)
                            } else {
                                RoomX.update(newItem)
                            }
                            subsConfigList = list.toMutableList().apply {
                                set(i, newItem)
                            }
                        }
                    )
                } else {
                    Text(
                        text = "-",
                        modifier = Modifier
                            .width(48.dp)
                            .wrapContentHeight(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

