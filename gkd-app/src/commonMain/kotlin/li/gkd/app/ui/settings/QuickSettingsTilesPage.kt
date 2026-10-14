package li.gkd.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import li.gkd.app.ui.component.gkTopAppBarScroll
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import li.gkd.app.META
import li.gkd.app.resources.*
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTopAppBar
import org.jetbrains.compose.resources.stringResource

@Composable
fun QuickSettingsTilesPage() {
    val mainVm = MainViewModel.requireCurrent()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    GkScaffold(
        modifier = Modifier.gkTopAppBarScroll(scrollBehavior),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(imageVector = GkIcons.ArrowBack, onClick = mainVm.navigator::pop)
                },
                title = { Text(stringResource(Res.string.quick_settings_tiles_title)) },
            )
        },
    ) { contentPadding ->
        Column(
            Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(Res.string.quick_settings_tiles_guide),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TileDescription(
                GkIcons.TileStatus, META.appName,
                stringResource(Res.string.quick_settings_tile_status_description),
            )
            TileDescription(
                GkIcons.TileCapture, stringResource(Res.string.quick_settings_tile_capture_title),
                stringResource(Res.string.quick_settings_tile_capture_description),
                singleAction = true,
            )
            TileDescription(
                GkIcons.TileHttp, stringResource(Res.string.http_service_compact_label),
                stringResource(Res.string.quick_settings_tile_http_description),
            )
            TileDescription(
                GkIcons.TileButton, stringResource(Res.string.snapshot_button_label),
                stringResource(Res.string.quick_settings_tile_button_description),
            )
            TileDescription(
                GkIcons.FlashOn, stringResource(Res.string.rule_matching_label),
                stringResource(Res.string.quick_settings_tile_match_description),
            )
            TileDescription(
                GkIcons.TileActivity, stringResource(Res.string.activity_service_label),
                stringResource(Res.string.quick_settings_tile_activity_description),
            )
            TileDescription(
                GkIcons.TileEvent, stringResource(Res.string.event_service_label),
                stringResource(Res.string.quick_settings_tile_event_description),
            )
            GkPageBottomSpace()
        }
    }
}

@Composable
private fun TileDescription(
    icon: ImageVector,
    title: String,
    description: String,
    singleAction: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(
                        if (singleAction) Res.string.quick_settings_tile_single_action
                        else Res.string.quick_settings_tile_toggle,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
