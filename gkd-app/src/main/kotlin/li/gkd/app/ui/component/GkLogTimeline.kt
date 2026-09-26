package li.gkd.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import li.gkd.app.data.appinfo.AppInfoRepository
import li.gkd.app.text.UiStrings
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.style.TABULAR_NUMBERS_FONT_FEATURE
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.util.format

@Composable
fun <T : Any> GkLogTimeline(
    items: LazyPagingItems<T>,
    listState: LazyListState,
    key: (T) -> Any,
    appId: (T) -> String,
    time: (T) -> Long,
    onOpenApp: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    showAppHeaders: Boolean = true,
    contextChanged: ((T, T) -> Boolean)? = null,
    contextHeader: (@Composable (T, T?) -> Unit)? = null,
    content: @Composable LazyItemScope.(T) -> Unit,
) {
    LazyColumn(modifier = modifier, state = listState) {
        val itemKey = items.itemKey(key)
        val contexts = mutableMapOf<Any, GkLogAppContext>()
        var previousDate: String? = null
        var previousApp: String? = null
        var previousItem: T? = null
        var currentContext: GkLogAppContext? = null
        var currentDateKey: String? = null
        for (index in 0 until items.itemCount) {
            val preview = items.peek(index)
            if (preview != null) {
                val date = time(preview).format("yyyy-MM-dd")
                val id = appId(preview)
                val dateChanged = date != previousDate
                if (dateChanged) {
                    val dateKey = "date:${key(preview)}"
                    currentDateKey = dateKey
                    stickyHeader(key = dateKey) {
                        GkLogDateHeader(date, dateKey, listState, contexts)
                    }
                }
                val appChanged = dateChanged || id != previousApp
                if (appChanged && showAppHeaders) {
                    val appKey = "app:${key(preview)}"
                    currentContext = GkLogAppContext(requireNotNull(currentDateKey), appKey, id)
                    contexts[appKey] = currentContext
                    item(key = appKey) {
                        GkLogAppHeader(id, dateChanged, onClick = onOpenApp?.let { open -> { open(id) } })
                    }
                }
                val precedingItem = previousItem.takeUnless { appChanged }
                if (contextHeader != null &&
                    (precedingItem == null || contextChanged?.invoke(precedingItem, preview) == true)
                ) {
                    val contextKey = "context:${key(preview)}"
                    currentContext?.let { contexts[contextKey] = it }
                    item(key = contextKey) { contextHeader(preview, precedingItem) }
                }
                currentContext?.let { contexts[itemKey(index)] = it }
                previousDate = date
                previousApp = id
                previousItem = preview
            }
            item(key = itemKey(index)) {
                items[index]?.let { content(it) }
            }
        }
        item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
            if (items.itemCount == 0 && items.loadState.refresh !is LoadState.Loading) {
                GkEmptyState(text = UiStrings.data_empty)
            } else {
                GkPageBottomSpace()
            }
        }
    }
}

@Composable
fun GkLogTimeText(time: Long) {
    val formatted = time.format("HH:mm:ss.SSS")
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            append(formatted.dropLast(4))
            withStyle(SpanStyle(color = color.copy(alpha = 0.65f))) {
                append(formatted.takeLast(4))
            }
        },
        style = MaterialTheme.typography.labelMedium.copy(
            fontFeatureSettings = TABULAR_NUMBERS_FONT_FEATURE,
        ),
        color = color,
        softWrap = false,
        maxLines = 1,
    )
}

fun Modifier.gkLogTimelineRail(color: Color): Modifier = drawBehind {
    val railX = 32.dp.toPx()
    drawLine(
        color = color,
        start = Offset(railX, 0f),
        end = Offset(railX, size.height),
        strokeWidth = 1.dp.toPx(),
    )
}

private data class GkLogAppContext(
    val dateKey: String,
    val headerKey: String,
    val appId: String,
)

@Composable
private fun GkLogDateHeader(
    date: String,
    dateKey: String,
    listState: LazyListState,
    appContexts: Map<Any, GkLogAppContext>,
) {
    val appId by remember(dateKey, listState, appContexts) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val header = layout.visibleItemsInfo.find { it.key == dateKey }
            if (header == null || header.offset > layout.viewportStartOffset) {
                null
            } else {
                val headerBottom = header.offset + header.size
                val first = layout.visibleItemsInfo
                    .filter {
                        it.key != dateKey && it.offset + it.size > headerBottom &&
                            it.offset < layout.viewportEndOffset
                    }
                    .minByOrNull { it.index }
                val context = first?.let { appContexts[it.key] }
                val appHeader = layout.visibleItemsInfo.find { it.key == context?.headerKey }
                if (context?.dateKey == dateKey &&
                    (appHeader == null || appHeader.offset + appHeader.size <= headerBottom)
                ) {
                    context.appId
                } else {
                    null
                }
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 24.dp, end = itemHorizontalPadding, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GkIcon(
            imageVector = GkIcons.Schedule,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            contentDescription = null,
        )
        Text(
            text = date,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            appId?.let { id ->
                val apps by AppInfoRepository.appInfoMapFlow.collectAsStateWithLifecycle()
                Text(
                    text = apps[id]?.name ?: id,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GkLogAppHeader(
    appId: String,
    firstInDate: Boolean,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .gkLogTimelineRail(MaterialTheme.colorScheme.outlineVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(
                start = 16.dp,
                end = itemHorizontalPadding,
                top = if (firstInDate) 0.dp else 12.dp,
                bottom = 0.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            GkAppIcon(appId = appId, size = 24.dp)
        }
        GkAppNameText(
            appId = appId,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
    }
}
