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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.material.fade
import com.google.accompanist.placeholder.material.placeholder
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootNavGraph
import kotlinx.serialization.encodeToString
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.data.getAppInfo
import li.songe.gkd.db.DbSet
import li.songe.gkd.utils.Singleton
import li.songe.gkd.utils.launchAsFn

@RootNavGraph
@Destination
@Composable
fun AppItemPage(
    subsApp: SubscriptionRaw.AppRaw,
    subsConfig: SubsConfig,
) {
    val scope = rememberCoroutineScope()

    var subsConfigs: List<SubsConfig?>? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        val mutableSet = DbSet.subsConfigDao.queryGroupTypeConfig(subsConfig.subsItemId, subsApp.id)
        val list = mutableListOf<SubsConfig?>()
        subsApp.groups.forEach { group ->
            if (group.key == null) {
                list.add(null)
            } else {
                val item = mutableSet.find { s -> s.groupKey == group.key }
                    ?: SubsConfig(
                        subsItemId = subsConfig.subsItemId,
                        appId = subsConfig.appId,
                        groupKey = group.key,
                        type = SubsConfig.GroupType
                    )
                list.add(item)
            }
        }
        subsConfigs = list
    }

    var showGroupItem: SubscriptionRaw.GroupRaw? by remember { mutableStateOf(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp, 0.dp)
            ) {
                Text(
                    text = getAppInfo(subsApp.id).name ?: "-",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = subsApp.id,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        items(subsApp.groups.size) { i ->
            val group = subsApp.groups[i]
            Row(
                modifier = Modifier
                    .clickable {
                        showGroupItem = group
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
                        text = group.desc ?: "-",
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
                        checked = subsConfigs?.get(i)?.enable != false,
                        modifier = Modifier
                            .placeholder(
                                subsConfigs == null,
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
                        onCheckedChange = scope.launchAsFn { enable ->
                            val subsConfigsVal = subsConfigs ?: return@launchAsFn
                            val newItem =
                                subsConfigsVal[i]?.copy(enable = enable) ?: return@launchAsFn
                            DbSet.subsConfigDao.insert(newItem)
                            subsConfigs = subsConfigsVal.toMutableList().apply {
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


    showGroupItem?.let { showGroupItemVal ->
        Dialog(onDismissRequest = { showGroupItem = null }) {
            Text(
                text = Singleton.json.encodeToString(showGroupItemVal),
                modifier = Modifier.width(400.dp)
            )
        }
    }
}

