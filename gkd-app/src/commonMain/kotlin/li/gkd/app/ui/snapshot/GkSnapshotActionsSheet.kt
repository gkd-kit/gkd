package li.gkd.app.ui.snapshot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import li.gkd.app.app.ActivityNames.getShowActivityId
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.Res
import li.gkd.app.resources.action_delete
import li.gkd.app.resources.action_save_to_album
import li.gkd.app.resources.action_save_to_downloads
import li.gkd.app.resources.action_share
import li.gkd.app.resources.link_copy
import li.gkd.app.resources.snapshot_generate_link
import li.gkd.app.resources.snapshot_replace_screenshot
import li.gkd.app.resources.snapshot_replace_screenshot_description
import li.gkd.app.time.format
import li.gkd.app.ui.component.GkAppNameText
import li.gkd.app.ui.component.GkFixedTimeText
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkModalBottomSheet
import li.gkd.db.Snapshot
import org.jetbrains.compose.resources.stringResource

@Composable
fun GkSnapshotActionsSheet(
    copyText: (String) -> Unit,
    imageLoader: ImageLoader,
    snapshot: Snapshot,
    appName: String,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onShare: () -> Unit,
    onSaveToDownloads: () -> Unit,
    onUpload: () -> Unit,
    onSaveToAlbum: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
) {
    fun perform(action: () -> Unit) {
        onDismissRequest()
        action()
    }
    GkModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterHorizontally)
                .widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SnapshotActionsHeader(
                imageLoader,
                snapshot,
                appName,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SnapshotActionRow(
                icon = if (snapshot.githubAssetId == null) GkIcons.Link else GkIcons.ContentCopy,
                title = if (snapshot.githubAssetId == null) {
                    stringResource(Res.string.snapshot_generate_link)
                } else {
                    stringResource(Res.string.link_copy)
                },
                trailingText = snapshot.githubAssetId?.let { "#$it" },
                onClick = {
                    val assetId = snapshot.githubAssetId
                    if (assetId == null) perform(onUpload) else copyText(AppLinks.ImportSnapshot + assetId)
                },
            )
            SnapshotActionRow(
                icon = GkIcons.Share,
                title = stringResource(Res.string.action_share),
                onClick = { perform(onShare) },
            )
            SnapshotActionRow(
                icon = GkIcons.ArrowDownward,
                title = stringResource(Res.string.action_save_to_downloads),
                onClick = { perform(onSaveToDownloads) },
            )
            SnapshotActionRow(
                icon = GkIcons.AddPhotoAlternate,
                title = stringResource(Res.string.action_save_to_album),
                onClick = { perform(onSaveToAlbum) },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SnapshotActionRow(
                icon = GkIcons.Edit,
                title = stringResource(Res.string.snapshot_replace_screenshot),
                subtitle = stringResource(Res.string.snapshot_replace_screenshot_description),
                onClick = { perform(onReplace) },
            )
            SnapshotActionRow(
                icon = GkIcons.Delete,
                title = stringResource(Res.string.action_delete),
                onClick = { perform(onDelete) },
            )
        }
    }
}

@Composable
private fun SnapshotActionsHeader(
    imageLoader: ImageLoader,
    snapshot: Snapshot,
    appName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val screenshotFile = snapshot.image()
    val screenshotModifiedAt = screenshotFile.modifiedAt
    val request = remember(context, screenshotFile, screenshotModifiedAt) {
        ImageRequest.Builder(context)
            .data(screenshotFile.path)
            .memoryCacheKey("snapshot-sheet-${snapshot.id}-$screenshotModifiedAt")
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            imageLoader = imageLoader, model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            GkAppNameText(
                appId = snapshot.appId,
                fallbackName = appName,
                style = MaterialTheme.typography.titleMedium,
            )
            getShowActivityId(snapshot.appId, snapshot.activityId)
                ?.takeIf(String::isNotBlank)?.let { activityId ->
                    Text(
                        text = activityId,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                GkFixedTimeText(
                    text = snapshot.id.format("yyyy-MM-dd HH:mm:ss"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${snapshot.screenWidth} × ${snapshot.screenHeight}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SnapshotActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    trailingText: String? = null,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .heightIn(min = if (subtitle == null) 48.dp else 56.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GkIcon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = if (trailingText == null) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
