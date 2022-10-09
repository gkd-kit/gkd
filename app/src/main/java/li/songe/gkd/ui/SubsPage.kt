package li.songe.gkd.ui

import android.content.pm.ApplicationInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.*
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
import li.songe.gkd.data.Subscription
import li.songe.gkd.db.table.SubsConfig
import li.songe.gkd.db.table.SubsItem
import li.songe.gkd.db.util.Operator.eq
import li.songe.gkd.db.util.RoomX
import li.songe.gkd.router.Page
import li.songe.gkd.router.Router
import li.songe.gkd.ui.component.StatusBar
import li.songe.gkd.ui.component.SubsAppCard
import li.songe.gkd.ui.component.SubsAppCardData
import li.songe.gkd.util.CoroutineUtil.throttle
import li.songe.gkd.util.Status
import java.io.File

/**
 * 订阅文件的具体内容
 */
object SubsPage : Page<SubsItem, Unit> {
    override val path = "SubsPage"
    override val defaultParams: SubsItem
        get() = error("not defaultParams")
    override val content: @Composable BoxScope.(
        params: SubsItem,
        router: Router<Unit>
    ) -> Unit = { subsItem, router ->
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        var sub: Subscription? by remember { mutableStateOf(null) }
        var subStatus: Status<Subscription> by remember { mutableStateOf(Status.Progress()) }
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
                                packageName = "" + it
                            )
                        )
                    )
                }
            }
        }
        var subsAppCardDataListStatus: Status<List<SubsAppCardData>> by remember {
            mutableStateOf(Status.Progress())
        }

        val changeItemConfig =
            scope.throttle<List<SubsAppCardData>, Int, Boolean> { list, i, enable ->
                val newItem = list[i].subsConfig.copy(
                    enable = enable
                )
                if (newItem.id == 0L) {
                    RoomX.insert(newItem)
                } else {
                    RoomX.update(newItem)
                }
                subsAppCardDataList = list.toMutableList().apply {
                    set(i, list[i].copy(subsConfig = newItem))
                }
            }


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
                Subscription.parse(rawText)
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
            val newSubsAppCardDataList = (subStatus as Status.Success).value.appList.map { app ->
                mutableSet.firstOrNull { v ->
                    v.packageName == app.packageName
                }.apply {
                    if (this != null) {
                        mutableSet.remove(this)
                    }
                } ?: SubsConfig(subsItemId = subsItem.id, packageName = app.packageName, type = SubsConfig.AppType)
            }.map { subsConfig ->
                async(IO) {
                    val info: ApplicationInfo = try {
                        packageManager.getApplicationInfo(subsConfig.packageName, 0)
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
                            text = "描述: ${sub?.description}",
                            modifier = textModifier
                        )
                    }
                }
            }
            subsAppCardDataList?.let {
                items(it.size) { i ->
                    AnimatedVisibility(visible = true) {
                        Box(modifier = Modifier
                            .wrapContentSize()
                            .clickable {
                                router.navigate(
                                    AppItemPage,
                                    AppItemPage.Params(
                                        sub?.appList?.get(i)!!,
                                        it[i].subsConfig,
                                        it[i].appName
                                    )
                                )
                            }) {
                            SubsAppCard(args = it[i]) { enable ->
                                changeItemConfig(
                                    it,
                                    i,
                                    enable
                                )
                            }
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
                        SubsAppCard(loading = true, args = placeholderList[i])
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
}