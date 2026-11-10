package li.gkd.app.ui.snapshot

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import li.gkd.app.resources.*
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkTooltipIconButtonBox
import li.gkd.app.ui.component.LocalEditorWindowInsets
import org.jetbrains.compose.resources.stringResource

@Composable
fun SnapshotInspectionTools(
    treeVisible: Boolean,
    onTreeVisibilityChange: (Boolean) -> Unit,
    onExit: () -> Unit,
) {
    var position by remember { mutableStateOf(Offset.Zero) }
    val insets = LocalEditorWindowInsets.current ?: WindowInsets.safeDrawing
    BoxWithConstraints(
        Modifier.fillMaxSize().windowInsetsPadding(insets).padding(8.dp),
        contentAlignment = AbsoluteAlignment.TopRight,
    ) {
        val buttonSize = 48.dp
        val density = LocalDensity.current
        val maxX =
            with(density) {
                (maxWidth.roundToPx() - 2 * buttonSize.roundToPx()).coerceAtLeast(0).toFloat()
            }
        val maxY = with(density) { (maxHeight - buttonSize).coerceAtLeast(0.dp).toPx() }
        Surface(
            modifier =
                Modifier.absoluteOffset {
                        IntOffset(
                            -position.x.coerceIn(0f, maxX).roundToInt(),
                            position.y.coerceIn(0f, maxY).roundToInt(),
                        )
                    }
                    .pointerInput(maxX, maxY) {
                        detectDragGestures { change, delta ->
                            change.consume()
                            position =
                                Offset(
                                    (position.x.coerceIn(0f, maxX) - delta.x).coerceIn(0f, maxX),
                                    (position.y.coerceIn(0f, maxY) + delta.y).coerceIn(0f, maxY),
                                )
                        }
                    },
            shape = CircleShape,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
        ) {
            Row {
                GkIconButton(
                    imageVector = GkIcons.Close,
                    modifier = Modifier.width(buttonSize),
                    contentDescription = stringResource(Res.string.snapshot_node_exit_inspect),
                    onClick = onExit,
                )
                val treeActionLabel =
                    stringResource(
                        if (treeVisible) Res.string.snapshot_node_hide_tree
                        else Res.string.snapshot_node_show_tree,
                    )
                GkTooltipIconButtonBox(contentDescription = treeActionLabel) {
                    IconToggleButton(
                        checked = treeVisible,
                        onCheckedChange = onTreeVisibilityChange,
                        modifier = Modifier.width(buttonSize),
                    ) {
                        GkIcon(GkIcons.NetworkNode, contentDescription = treeActionLabel)
                    }
                }
            }
        }
    }
}
