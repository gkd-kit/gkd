package li.gkd.app.feature.log

import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.share.launchUi

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import li.gkd.app.text.UiStrings
import li.gkd.app.MainActivity
import li.gkd.app.data.fixedName
import li.gkd.app.data.isStateChanged
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.LocalDarkTheme
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.getJson5AnnotatedString
import li.gkd.app.ui.style.iconTextSize
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.ToastUtils.copyText
import li.gkd.app.util.format
import li.gkd.app.util.TimeUtils.throttle
import li.gkd.app.util.toJson5String
import li.gkd.app.util.ToastUtils.toast
import li.gkd.db.A11yEventLog
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkCopyableText
import li.gkd.app.ui.component.GkEmptyState
import li.gkd.app.ui.component.GkFixedTimeText
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.rememberListScrollState

@Serializable
data object A11yEventLogRoute : NavKey

@Composable
fun A11yEventLogPage() {
    val context = LocalActivity.current as MainActivity
    val mainVm = context.mainVm
    val vm = viewModel<A11yEventLogVm>()
    val scope = vm.scope

    var shownEventLog by remember { mutableStateOf<A11yEventLog?>(null) }
    val list = vm.pagingDataFlow.collectAsLazyPagingItems()
    val logCount = list.itemCount
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    pageScrollState.ResetOnChange(list.itemCount > 0)

    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        GkTopAppBar(
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                GkIconButton(imageVector = GkIcons.ArrowBack, onClick = {
                    mainVm.popPage()
                })
            },
            title = {
                Text(
                    text = UiStrings.event_log_title,
                    modifier = Modifier.noRippleClickable(onClick = pageScrollState::resetScroll),
                )
            },
            actions = {
                if (logCount > 0) {
                    GkIconButton(
                        imageVector = GkIcons.Delete,
                        onClick = throttle {
                            scope.launchUi {
                                if (!mainVm.dialogRequests.confirm(
                                    title = UiStrings.action_delete_logs,
                                    text = UiStrings.event_log_delete_all_confirmation,
                                    error = true,
                                )) return@launchUi
                                vm.deleteAll()
                                toast(UiStrings.delete_success)
                            }
                        }
                    )
                }
            }
        )
    }) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                count = list.itemCount,
                key = list.itemKey { it.id }
            ) { i ->
                val eventLog = list[i]
                if (eventLog != null) {
                    EventLogCard(
                        eventLog = eventLog,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clickable(onClick = { shownEventLog = eventLog }),
                    )
                }
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                if (logCount == 0 && list.loadState.refresh !is LoadState.Loading) {
                    GkEmptyState(text = UiStrings.data_empty)
                } else {
                    GkPageBottomSpace()
                }
            }
        }
    }

    shownEventLog?.let { eventLog ->
        val onDismissRequest = { shownEventLog = null }
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
        GkAlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = UiStrings.event_details) },
            text = {
                val textModifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.extraSmall,
                    )
                    .padding(horizontal = 4.dp)
                Column {
                    Text(text = UiStrings.event_type_prefix + if (eventLog.isStateChanged) UiStrings.event_window_state_changed else UiStrings.event_window_content_changed)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = UiStrings.app_id)
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
                    Text(text = UiStrings.event_data)
                    GkCopyableText(
                        text = eventText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
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
                        Text(text = UiStrings.event_selector)
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
                    Text(text = UiStrings.action_close)
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
                GkFixedTimeText(
                    text = eventLog.ctime.format("HH:mm:ss SSS"),
                )
                Spacer(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .size(height = 8.dp, width = 1.dp)
                )
                GkAppNameText(
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
            val desc = eventLog.desc
            if (desc != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    GkIcon(
                        imageVector = GkIcons.Title,
                        modifier = Modifier.iconTextSize(
                            square = false
                        ),
                    )
                    Text(
                        text = desc,
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
                    GkIcon(
                        imageVector = GkIcons.TextFields,
                        modifier = Modifier.iconTextSize(
                            square = false
                        ),
                    )
                    // If an ancestor container has height(IntrinsicSize.Min) set, it will cause FlowRow to not wrap automatically
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
    GkIcon(
        imageVector = GkIcons.ContentCopy,
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick)
            .iconTextSize(),
    )
}
