package li.songe.gkd.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.res.ResourcesCompat
import com.google.accompanist.placeholder.PlaceholderHighlight
import com.google.accompanist.placeholder.material.fade
import com.google.accompanist.placeholder.material.placeholder
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import li.songe.gkd.R
import li.songe.gkd.data.SubscriptionRaw
import li.songe.gkd.db.table.SubsConfig
import li.songe.gkd.db.table.SubsItem
import li.songe.gkd.db.util.Operator.eq
import li.songe.gkd.db.util.RoomX
import li.songe.gkd.ui.component.StatusBar
import li.songe.gkd.ui.component.SubsAppCard
import li.songe.gkd.ui.component.SubsAppCardData
import li.songe.gkd.util.Ext.getApplicationInfoExt
import li.songe.gkd.util.Status
import li.songe.gkd.util.ThrottleState
import li.songe.router.LocalRoute
import li.songe.router.LocalRouter
import li.songe.router.Page
import java.io.File


val SubsPage = Page {
    val router = LocalRouter.current
    val subsItem = LocalRoute.current.data as SubsItem

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var sub: SubscriptionRaw? by remember { mutableStateOf(null) }
    var subStatus: Status<SubscriptionRaw> by remember { mutableStateOf(Status.Progress()) }
    var subsAppCardDataList: List<SubsAppCardData>? by remember { mutableStateOf(null) }
    val placeholderList: List<SubsAppCardData> = remember {
        mutableListOf<SubsAppCardData>().apply {
            repeat(5) {
                add(
                    SubsAppCardData(
                        appName = "" + it,
                        icon = ResourcesCompat.getDrawable(
                            context.resources,
                            R.drawable.ic_app_2,
                            context.theme
                        )!!,
                        subsConfig = SubsConfig(
                            subsItemId = it.toLong(),
                            appId = "" + it
                        )
                    )
                )
            }
        }
    }
    var subsAppCardDataListStatus: Status<List<SubsAppCardData>> by remember {
        mutableStateOf(Status.Progress())
    }

    val changeItemThrottle = ThrottleState.use(scope)

    LaunchedEffect(Unit) {
        val st = System.currentTimeMillis()
        val file = File(subsItem.filePath)
        if (!(file.exists() && file.isFile)) {
            subStatus = Status.Error("在本地存储没有找到订阅文件")
            return@LaunchedEffect
        }
        val rawText = try {
            withContext(IO) { file.readText() }
        } catch (e: Exception) {
            subStatus = Status.Error("读取文件失败:$e")
            return@LaunchedEffect
        }
        val newSub = try {
            SubscriptionRaw.parse5(rawText)
        } catch (e: Exception) {
            subStatus = Status.Error("序列化失败:$e")
            return@LaunchedEffect
        }
        subStatus = Status.Success(newSub)

        val mutableSet =
            RoomX.select { (SubsConfig::type eq SubsConfig.AppType) and (SubsConfig::subsItemId eq subsItem.id) }
                .toMutableSet()

        val packageManager = context.packageManager
        val defaultIcon = ResourcesCompat.getDrawable(
            context.resources,
            R.drawable.ic_app_2,
            context.theme
        )!!
        val defaultName = "-"
        val newSubsAppCardDataList = (subStatus as Status.Success).value.apps.map { appRaw ->
            mutableSet.firstOrNull { v ->
                v.appId == appRaw.id
            }.apply {
                if (this != null) {
                    mutableSet.remove(this)
                }
            } ?: SubsConfig(
                subsItemId = subsItem.id,
                appId = appRaw.id,
                type = SubsConfig.AppType
            )
        }.map { subsConfig ->
            async(IO) {
                val info: ApplicationInfo = try {
                    packageManager.getApplicationInfoExt(
                        subsConfig.appId,
                        PackageManager.GET_META_DATA
                    )
                } catch (e: Exception) {
                    return@async SubsAppCardData(
                        defaultName,
                        defaultIcon,
                        subsConfig
                    )
                }
                return@async SubsAppCardData(
                    packageManager.getApplicationLabel(info).toString(),
                    packageManager.getApplicationIcon(info),
                    subsConfig
                )
            }
        }.awaitAll()
        subsAppCardDataListStatus = Status.Success(newSubsAppCardDataList)
        delay(400 - (System.currentTimeMillis() - st))
        sub = newSub
        subsAppCardDataList = newSubsAppCardDataList
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxSize()
    ) {
        item {
            Column {
                StatusBar()
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
        }
        subsAppCardDataList?.let { cardDataList ->
            items(cardDataList.size) { i ->
                AnimatedVisibility(visible = true) {
                    Box(modifier = Modifier
                        .wrapContentSize()
                        .clickable {
                            router.navigate(
                                AppItemPage,
                                AppItemPageParams(
                                    sub?.apps?.get(i)!!,
                                    cardDataList[i].subsConfig,
                                    cardDataList[i].appName
                                )
                            )
                        }) {
                        SubsAppCard(
                            sub = cardDataList[i],
                            onValueChange = changeItemThrottle.invoke { enable ->
                                val newItem = cardDataList[i].subsConfig.copy(
                                    enable = enable
                                )
                                if (newItem.id == 0L) {
                                    RoomX.insert(newItem)
                                } else {
                                    RoomX.update(newItem)
                                }
                                subsAppCardDataList = cardDataList.toMutableList().apply {
                                    set(i, cardDataList[i].copy(subsConfig = newItem))
                                }
                            }
                        )
                    }
                }
            }
        }
        if (subsAppCardDataList == null) {
            items(placeholderList.size) { i ->
                Box(
                    modifier = Modifier
                        .wrapContentSize()
                ) {
                    SubsAppCard(loading = true, sub = placeholderList[i])
                    Text(text = "")
                }
            }
        }

        item(true) {
            Spacer(modifier = Modifier.height(10.dp))
        }

    }


    if (subStatus !is Status.Success || subsAppCardDataListStatus !is Status.Success) {
        when (val s = subStatus) {
            is Status.Success -> {
                when (val s2 = subsAppCardDataListStatus) {
                    is Status.Error -> {
                        Dialog(onDismissRequest = { router.back() }) {
                            Text(text = s2.value.toString())
                        }
                    }

                    is Status.Progress -> {
                    }

                    else -> {}
                }
            }

            is Status.Error -> {
                Dialog(onDismissRequest = { router.back() }) {
                    Text(text = s.value.toString())
                }
            }

            is Status.Progress -> {
            }

            else -> {}
        }
    }


}