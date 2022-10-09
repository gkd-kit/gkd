package li.songe.gkd.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.songe.gkd.data.Subscription
import li.songe.gkd.db.table.SubsConfig
import li.songe.gkd.router.Page
import li.songe.gkd.router.Router
import li.songe.gkd.ui.component.StatusBar

object GroupItemPage : Page<GroupItemPage.Params, Unit> {
    data class Params(
        val group: Subscription.Group,
        val subsConfig: SubsConfig?,
        val appName: String
    )

    override val path: String = "GroupItemPage"
    override val defaultParams: Params
        get() = error("no defaultParams")
    override val content: @Composable BoxScope.(
        params: Params,
        router: Router<Unit>
    ) -> Unit = { params, _ ->
        val (showData, setShowData) = remember { mutableStateOf<Subscription.Rule?>(null) }
        LazyColumn {
            item(true) {
                StatusBar()
                Text(
                    text = params.appName, modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp, 0.dp)
                )
                Text(
                    text = params.group.className, modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp, 0.dp)
                )
            }
            val ruleList = params.group.ruleList
            items(ruleList.size) { i ->
                Row(
                    modifier = Modifier
                        .clickable {
                            setShowData(ruleList[i])
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
                            text = ruleList[i].description ?: "-",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth(),
                        )
                        Text(
                            text = ruleList[i].selector,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth(),
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))
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

//        val s = stringResource(id = 0)
        if (showData != null) {
            AlertDialog(
                onDismissRequest = {
                    setShowData(null)
                },
                title = {
                    Text(text = showData.description ?: "-")
                },
                text = {
                    Text(
                        text = showData.selector,
                        fontSize = 16.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { setShowData(null) }) {
                        Text(
                            "确认",
                            fontWeight = FontWeight.W500,
                            style = MaterialTheme.typography.button
                        )
                    }
                })
        }
    }
}