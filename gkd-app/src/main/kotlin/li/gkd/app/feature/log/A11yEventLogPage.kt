package li.gkd.app.feature.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import li.gkd.app.MainViewModel
import li.gkd.app.ui.AppConfigRoute
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.component.GkLogTimeline
import li.gkd.app.ui.component.GkLogTimeText
import li.gkd.app.ui.component.gkLogTimelineRail
import li.gkd.app.text.UiStrings
import li.gkd.app.data.fixedName
import li.gkd.app.data.isStateChanged
import li.gkd.app.ui.share.LocalDarkTheme
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.getJson5AnnotatedString
import li.gkd.app.ui.style.iconTextSize
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.ToastUtils.copyText
import li.gkd.app.util.toJson5String
import li.gkd.db.A11yEventLog
import li.gkd.app.ui.component.GkAlertDialog
import li.gkd.app.ui.component.GkCopyableText
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.rememberListScrollState

@Serializable
data object A11yEventLogRoute : NavKey

@Composable
fun A11yEventLogPage() {
    val mainVm = MainViewModel.requireCurrent()
    val vm = viewModel<A11yEventLogVm>()

    var shownEventLog by remember { mutableStateOf<A11yEventLog?>(null) }
    val list = vm.pagingDataFlow.collectAsLazyPagingItems()
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
        )
    }) { contentPadding ->
        GkLogTimeline(
            items = list,
            listState = listState,
            key = { it.id },
            appId = { it.appId },
            time = { it.ctime },
            onOpenApp = { mainVm.navigatePage(AppConfigRoute(it)) },
            modifier = Modifier.scaffoldPadding(contentPadding),
        ) { eventLog ->
            EventLogEntry(eventLog, onClick = { shownEventLog = eventLog })
        }
    }

    shownEventLog?.let { eventLog ->
        val onDismissRequest = { shownEventLog = null }
        val dark = LocalDarkTheme.current
        val eventText = remember(dark, eventLog.id) {
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
private fun EventLogEntry(eventLog: A11yEventLog, onClick: () -> Unit) {
    val color = if (eventLog.isStateChanged) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .gkLogTimelineRail(MaterialTheme.colorScheme.outlineVariant)
            .clickable(onClick = onClick)
            .padding(start = 56.dp, end = itemHorizontalPadding, top = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (eventLog.isStateChanged) UiStrings.event_window_state_changed
                    else UiStrings.event_window_content_changed,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
            GkLogTimeText(eventLog.ctime)
        }
        if (eventLog.fixedName.isNotBlank()) {
            Text(
                text = eventLog.fixedName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        eventLog.desc?.takeIf { it.isNotBlank() }?.let { desc ->
            EventLogSummary(GkIcons.Title, desc, maxLines = 1)
        }
        val summary = remember(eventLog.text) {
            eventLog.text.filter { it.isNotBlank() }.joinToString(" · ")
        }
        if (summary.isNotBlank()) {
            EventLogSummary(GkIcons.TextFields, summary, maxLines = 2)
        }
    }
}

@Composable
private fun EventLogSummary(
    icon: ImageVector,
    text: String,
    maxLines: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        GkIcon(
            imageVector = icon,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            contentDescription = null,
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
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
