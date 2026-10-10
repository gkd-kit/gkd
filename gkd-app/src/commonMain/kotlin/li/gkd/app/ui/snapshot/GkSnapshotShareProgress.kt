package li.gkd.app.ui.snapshot

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.stringResource

object GkSnapshotShareProgressDefaults {
    val HorizontalPadding = 16.dp
    val BottomPadding = 16.dp
}

@Composable
fun GkSnapshotShareProgress(
    progress: SnapshotShareProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.completed.toFloat() / progress.total,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "snapshotShareProgress",
    )
    Surface(
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = GkSnapshotShareProgressDefaults.HorizontalPadding),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.snapshot_share_preparing),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = onCancel) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
            Text(
                text = stringResource(
                    Res.string.progress_fraction,
                    progress.completed.toString(), progress.total.toString(),
                ),
                modifier = Modifier.align(Alignment.End),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
