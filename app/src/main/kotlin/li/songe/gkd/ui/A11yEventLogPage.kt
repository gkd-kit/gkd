package li.songe.gkd.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import li.songe.gkd.MainActivity
import li.songe.gkd.data.A11yEventLog
import li.songe.gkd.db.DbSet
import li.songe.gkd.ui.component.AppNameText
import li.songe.gkd.ui.component.EmptyText
import li.songe.gkd.ui.component.FixedTimeText
import li.songe.gkd.ui.component.LocalNumberCharWidth
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.measureNumberTextWidth
import li.songe.gkd.ui.component.useListScrollState
import li.songe.gkd.ui.component.waitResult
import li.songe.gkd.ui.share.ListPlaceholder
import li.songe.gkd.ui.share.LocalDarkTheme
import li.songe.gkd.ui.share.noRippleClickable
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.getJson5AnnotatedString
import li.songe.gkd.ui.style.iconTextSize
import li.songe.gkd.ui.style.scaffoldPadding
import li.songe.gkd.util.copyText
import li.songe.gkd.util.format
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toJson5String
import li.songe.gkd.util.toast

@Serializable
data object A11yEventLogRoute : NavKey

@Composable
fun A11yEventLogPage() {
    val context = LocalActivity.current as MainActivity
    val mainVm = context.mainVm
    val vm = viewModel<A11yEventLogVm>()

    val logCount by vm.logCountFlow.collectAsState()
    val list = vm.pagingDataFlow.collectAsLazyPagingItems()
    val (scrollBehavior, listState) = useListScrollState(vm.resetKey, list.itemCount > 0)

    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        PerfTopAppBar(
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = {
                    mainVm.popPage()
                })
            },
            title = {
                Text(
                    text = "事件日志",
                    modifier = Modifier.noRippleClickable { vm.resetKey.intValue++ },
                )
            },
            actions = {
                if (logCount > 0) {
                    PerfIconButton(
                        imageVector = PerfIcon.Delete,
                        onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            mainVm.dialogFlow.waitResult(
                                title = "删除日志",
                                text = "确定删除所有事件日志?",
                                error = true,
                            )
                            DbSet.a11yEventLogDao.deleteAll()
                            toast("删除成功")
                        })
                    )
                }
            }
        )
    }) { contentPadding ->
        CompositionLocalProvider(
            LocalNumberCharWidth provides measureNumberTextWidth(),
        ) {
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    count = list.itemCount,
                    key = list.itemKey { it.id }
                ) { i ->
                    val eventLog = list[i] ?: return@items
                    EventLogCard(
                        eventLog = eventLog,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clickable(onClick = {
                                vm.showEventLogFlow.value = eventLog
                            })
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    if (logCount == 0 && list.loadState.refresh !is LoadState.Loading) {
                        EmptyText(text = "暂无数据")
                    }
                }
            }
        }
    }

    vm.showEventLogFlow.collectAsState().value?.let { eventLog ->
        val onDismissRequest = { vm.showEventLogFlow.value = null }
        val dark = LocalDarkTheme.current
        val eventText = remember(dark) {
            getJson5AnnotatedString(
                toJson5String(
                    JsonObject(
                        mapOf(
                            "name" to JsonPrimitive(eventLog.name),
                            "desc" to JsonPrimitive(eventLog.desc),
                            "text" to JsonArray(eventLog.text.map(::JsonPrimitive)),
                        )
                    )
                ),
                dark,
            )
        }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = "事件详情") },
            text = {
                val textModifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.extraSmall,
                    )
                    .padding(horizontal = 4.dp)
                Column {
                    Text(text = "类型: " + if (eventLog.isStateChanged) "状态变化" else "内容变化")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "应用ID")
                    Row {
                        Text(
                            text = eventLog.appId,
                            modifier = textModifier
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        CopyIcon(onClick = {
                            copyText(eventLog.appId)
                        })
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "事件数据")
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = eventText,
                                modifier = textModifier.fillMaxWidth()
                            )
                        }
                        CopyIcon(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp),
                            onClick = {
                                copyText(eventText.text)
                            })
                    }
                    if (eventLog.isStateChanged) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val selectorText = remember(eventLog.id) {
                            (listOf(
                                "name" to eventLog.name,
                                "desc" to eventLog.desc,
                                "text.size" to eventLog.text.size,
                            ) + eventLog.text.mapIndexed { i, s -> "text.get($i)" to s }).joinToString(
                                ""
                            ) { (key, value) ->
                                val v =
                                    if (value is String) toJson5String(value) else value.toString()
                                "[${key}=${v}]"
                            }
                        }
                        Text(text = "特征选择器")
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = selectorText,
                                modifier = textModifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            CopyIcon(onClick = {
                                copyText(selectorText)
                            })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = "关闭")
                }
            },
        )
    }
}

@Composable
fun EventLogCard(eventLog: A11yEventLog, modifier: Modifier = Modifier) {
    var parentHeight by remember { mutableIntStateOf(0) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged {
                parentHeight = it.height
            }
    ) {
        Spacer(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.secondary)
                .width(2.dp)
                .height((parentHeight / LocalDensity.current.density).dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FixedTimeText(
                    text = eventLog.ctime.format("HH:mm:ss SSS"),
                )
                Spacer(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .size(height = 8.dp, width = 1.dp)
                )
                AppNameText(
                    appId = eventLog.appId,
                )
            }
            Text(
                text = eventLog.fixedName,
                color = if (eventLog.isStateChanged) MaterialTheme.colorScheme.primary else Color.Unspecified,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.MiddleEllipsis,
            )
            if (eventLog.desc != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    PerfIcon(
                        imageVector = PerfIcon.Title,
                        modifier = Modifier.iconTextSize(
                            square = false
                        ),
                    )
                    Text(
                        text = eventLog.desc,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.extraSmall,
                            )
                            .padding(horizontal = 2.dp),
                    )
                }
            }
            if (eventLog.text.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    PerfIcon(
                        imageVector = PerfIcon.TextFields,
                        modifier = Modifier.iconTextSize(
                            square = false
                        ),
                    )
                    // 如果祖先容器有设置了 height(IntrinsicSize.Min) 会导致 FlowRow 不会自动换行
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        eventLog.text.forEach { subText ->
                            Text(
                                text = subText,
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = MaterialTheme.shapes.extraSmall,
                                    )
                                    .padding(horizontal = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyIcon(modifier: Modifier = Modifier, onClick: () -> Unit) {
    PerfIcon(
        imageVector = PerfIcon.ContentCopy,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick)
            .iconTextSize(),
    )
}
