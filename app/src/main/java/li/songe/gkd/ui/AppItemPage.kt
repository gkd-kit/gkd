package li.songe.gkd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.*
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
import li.songe.gkd.data.Subscription
import li.songe.gkd.db.table.SubsConfig
import li.songe.gkd.db.util.Operator.eq
import li.songe.gkd.db.util.RoomX
import li.songe.gkd.router.Page
import li.songe.gkd.router.Router
import li.songe.gkd.ui.component.StatusBar
import li.songe.gkd.util.CoroutineUtil.throttle

object AppItemPage : Page<AppItemPage.Params, Unit> {
    data class Params(
        val subsApp: Subscription.App,
        val subsConfig: SubsConfig,
        val appName: String
    )

    override val path = "AppItemPage"
    override val defaultParams
        get() = error("no defaultParams")
    override val content: @Composable BoxScope.(
        params: Params,
        router: Router<Unit>
    ) -> Unit = { params, router ->

//        https://developer.android.com/jetpack/compose/modifiers-list

        val scope = rememberCoroutineScope()
//        val context = LocalContext.current

        var subsConfigList: List<SubsConfig?>? by remember { mutableStateOf(null) }
//        var loading by remember { mutableStateOf(true) }

        val changeItemConfig = scope.throttle<
                List<SubsConfig?>,
                Int,
                Boolean
                > { list, i, enable ->
            val newItem = list[i]?.copy(enable = enable) ?: return@throttle
            if (newItem.id == 0L) {
                RoomX.insert(newItem)
            } else {
                RoomX.update(newItem)
            }
            subsConfigList = list.toMutableList().apply {
                set(i, newItem)
            }
        }

        LaunchedEffect(Unit) {
            delay(400)
            val config = params.subsConfig
            val mutableSet =
                RoomX.select { (SubsConfig::type eq SubsConfig.GroupType) and (SubsConfig::subsItemId eq config.subsItemId) and (SubsConfig::packageName eq config.packageName) }
                    .toMutableSet()
            val list = mutableListOf<SubsConfig?>()
            params.subsApp.groupList.forEach { group ->
                if (group.key == null) {
                    list.add(null)
                } else {
                    val item = mutableSet.find { s -> s.groupKey == group.key } ?: SubsConfig(
                        subsItemId = config.subsItemId,
                        packageName = config.packageName,
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
                            text = params.subsApp.packageName,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            items(params.subsApp.groupList.size) { i ->
                val group = params.subsApp.groupList[i]
                Row(
                    modifier = Modifier
                        .clickable {
                            router.navigate(
                                GroupItemPage, GroupItemPage.Params(
                                    group = group,
                                    subsConfig = subsConfigList?.get(i),
                                    appName = params.appName
                                )
                            )
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
                            text = group.description ?: "-",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                        Text(
                            text = if (group.className.startsWith(params.subsApp.packageName)) {
                                group.className.substring(params.subsApp.packageName.length)
                            } else {
                                group.className
                            },
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
                            onCheckedChange = { enable ->
                                subsConfigList?.let { list -> changeItemConfig(list, i, enable) }
                            },
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
}