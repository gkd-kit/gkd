package li.gkd.app.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import li.gkd.app.model.showActivityId
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_log_activity_unknown
import li.gkd.app.resources.activity_log_title
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkLogTimeText
import li.gkd.app.ui.component.GkLogTimeline
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.gkLogTimelineRail
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.db.ActivityLog
import org.jetbrains.compose.resources.stringResource


@Composable
fun ActivityLogScreen(
    list: LazyPagingItems<ActivityLog>,
    onBack: () -> Unit, onOpenApp: (String) -> Unit, onDetails: (ActivityLog) -> Unit,
    appLabel: @Composable (String) -> String,
    appIcon: @Composable (String) -> Unit,
    appName: @Composable (String, Modifier) -> Unit,
) {
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    pageScrollState.ResetOnChange(list.itemCount > 0)
    GkScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(imageVector = GkIcons.ArrowBack, onClick = onBack)
                },
                title = {
                    Text(
                        text = stringResource(Res.string.activity_log_title),
                        modifier = Modifier.noRippleClickable(onClick = pageScrollState::resetScroll),
                    )
                },
            )
        },
    ) { contentPadding ->
        GkLogTimeline(
            items = list,
            appLabel = appLabel, appIcon = appIcon, appName = appName,
            listState = pageScrollState.listState,
            key = { it.id },
            appId = { it.appId },
            time = { it.ctime },
            onOpenApp = onOpenApp,
            modifier = Modifier.scaffoldPadding(contentPadding),
        ) { log ->
            ActivityLogEntry(log) {
                onDetails(log)
            }
        }
    }
}

@Composable
private fun ActivityLogEntry(log: ActivityLog, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .gkLogTimelineRail(MaterialTheme.colorScheme.outlineVariant)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = itemHorizontalPadding, top = 6.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            GkIcon(
                imageVector = GkIcons.Layers,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null,
            )
        }
        Text(
            text = log.showActivityId ?: stringResource(Res.string.action_log_activity_unknown),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.MiddleEllipsis,
        )
        GkLogTimeText(log.ctime)
    }
}
