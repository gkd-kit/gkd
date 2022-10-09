package li.songe.gkd.ui.home.page

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.songe.gkd.R
import li.songe.gkd.db.table.SubsConfig
import li.songe.gkd.db.table.SubsItem
import li.songe.gkd.db.util.Operator.eq
import li.songe.gkd.db.util.RoomX
import li.songe.gkd.router.Router.Companion.LocalRouter
import li.songe.gkd.ui.SubsItemInsertPage
import li.songe.gkd.ui.SubsItemUpdatePage
import li.songe.gkd.ui.SubsPage
import li.songe.gkd.ui.component.SubsItemCard
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubscriptionManagePage() {
// https://medium.com/androiddevelopers/multiple-back-stacks-b714d974f134
// https://medium.com/androiddevelopers/animations-in-navigation-compose-36d48870776b
// https://tigeroakes.com/posts/react-to-compose-dictionary/#storybook--preview
// https://google.github.io/accompanist/
// https://foso.github.io/Jetpack-Compose-Playground/
// https://www.jetpackcompose.net/
// https://jetpackcompose.cn/docs/
// https://developer.android.com/jetpack/compose/performance

    val scope = rememberCoroutineScope()
    val router = LocalRouter.current

    var subItemList by remember { mutableStateOf(listOf<SubsItem>()) }
    var shareSubItem: SubsItem? by remember { mutableStateOf(null) }
    var deleteSubItem: SubsItem? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        subItemList = RoomX.select()
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxHeight()
    ) {
        item(subItemList) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp, 0.dp)
            ) {
                Text(
                    text = "共有${subItemList.size}条订阅,激活:${subItemList.count { it.enable }},禁用:${subItemList.count { !it.enable }}",
                )
                Image(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = "",
                    modifier = Modifier
                        .clickable {
                            scope.launch {
                                val newSubsItem =
                                    router.navigateForResult(SubsItemInsertPage)
                                        ?: return@launch
                                subItemList = subItemList
                                    .toMutableList()
                                    .apply { add(newSubsItem) }
                            }
                        }
                        .padding(4.dp)
                        .size(25.dp)
                )
            }
        }

        items(
            subItemList.size,
            { i -> subItemList[i].hashCode() }
        ) { i ->
            Card(
                modifier = Modifier
                    .animateItemPlacement()
                    .padding(vertical = 3.dp, horizontal = 8.dp)
                    .clickable {
                        router.navigate(SubsPage, subItemList[i])
                    },
                elevation = 0.dp,
                border = BorderStroke(1.dp, Color(0xfff6f6f6)),
                shape = RoundedCornerShape(8.dp),
            ) {
                SubsItemCard(
                    subItemList[i],
                    onShareClick = {
                        shareSubItem = subItemList[i]
                    }, onEditClick = {
                        scope.launch {
                            val newSubsItem =
                                router.navigateForResult(SubsItemUpdatePage, subItemList[i])
                                    ?: return@launch
                            subItemList = subItemList
                                .toMutableList()
                                .apply {
                                    set(i, newSubsItem)
                                }
                        }
                    },
                    onDelClick = {
                        deleteSubItem = subItemList[i]
                    }
                )
            }
        }
    }

    if (shareSubItem != null) {
        Dialog(onDismissRequest = { shareSubItem = null }) {
            Box(
                Modifier
                    .width(250.dp)
                    .background(Color.White)
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "二维码", modifier = Modifier
                        .clickable { }
                        .fillMaxWidth()
                        .padding(8.dp)
                    )
                    Text(text = "导出至剪切板", modifier = Modifier
                        .clickable { }
                        .fillMaxWidth()
                        .padding(8.dp)
                    )
                }
            }
        }
    }

    if (deleteSubItem != null) {
        AlertDialog(
            onDismissRequest = { deleteSubItem = null },
            title = { Text(text = "是否删除该项") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        if (deleteSubItem == null) return@launch
                        deleteSubItem?.let {
                            RoomX.delete(it)
                            RoomX.delete { SubsConfig::subsItemId eq it.id }
                        }
                        withContext(IO) {
                            try {
                                File(deleteSubItem!!.filePath).delete()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        subItemList = subItemList.toMutableList().also { it.remove(deleteSubItem) }
                        deleteSubItem = null
                    }
                }) {
                    Text("是")
                }
            },
            dismissButton = {
                Button(onClick = {
                    deleteSubItem = null
                }) {
                    Text("否")
                }
            })
    }
}