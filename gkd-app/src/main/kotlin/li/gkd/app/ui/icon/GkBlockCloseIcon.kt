package li.gkd.app.ui.icon

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import li.gkd.app.ui.component.GkIconButton

@Composable
fun GkBlockCloseIconButton(
    isClose: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
) {
    // One continuous progress value also allows an interrupted transition to reverse.
    val progress by animateFloatAsState(
        targetValue = if (isClose) 0f else 1f,
        animationSpec = tween(300),
        label = "BlockClose",
    )
    val icon = remember(progress) {
        val radius = 9f
        val diagonal = radius * cos(PI / 4).toFloat()
        val closeDiagonal = diagonal * 0.9f
        val slashDiagonal = closeDiagonal + (diagonal - closeDiagonal) * progress
        val remaining = (1f - progress / 0.55f).coerceIn(0f, 1f)
        ImageVector.Builder(
            name = "BlockClose",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Butt,
            ) {
                // The backslash retracts into the shared slash before the ring completes.
                if (remaining > 0f) {
                    val halfLength = closeDiagonal * remaining
                    moveTo(12f - halfLength, 12f - halfLength)
                    lineTo(12f + halfLength, 12f + halfLength)
                }
                moveTo(12f - slashDiagonal, 12f + slashDiagonal)
                lineTo(12f + slashDiagonal, 12f - slashDiagonal)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                if (progress > 0f) {
                    // Opposite roots on the shared slash each grow half of the circle.
                    for (start in listOf(-PI / 4, 3 * PI / 4)) {
                        val end = start + PI * progress
                        moveTo(
                            12f + radius * cos(start).toFloat(),
                            12f + radius * sin(start).toFloat(),
                        )
                        arcTo(
                            radius, radius, 0f, false, true,
                            12f + radius * cos(end).toFloat(),
                            12f + radius * sin(end).toFloat(),
                        )
                    }
                }
            }
        }.build()
    }
    GkIconButton(
        imageVector = icon,
        onClick = onClick,
        modifier = modifier,
        contentDescription = contentDescription,
        onClickLabel = onClickLabel,
        colors = colors,
    )
}
