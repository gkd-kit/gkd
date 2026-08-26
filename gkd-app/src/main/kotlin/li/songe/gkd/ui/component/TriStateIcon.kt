package li.songe.gkd.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A tri-state toggle icon drawn via Canvas.
 *
 * @param checked
 *   - `false`  → thumb on the LEFT  (toggle-off state, matches the uploaded SVG)
 *   - `null`   → thumb in the CENTER (indeterminate state)
 *   - `true`   → thumb on the RIGHT (toggle-on state)
 * @param size          Overall size of the icon.
 * @param trackColor    Color of the outer rounded-rectangle track.
 * @param thumbColor    Color of the circular thumb.
 * @param animDurationMs Duration (ms) of the thumb translation animation.
 */
@Composable
fun TriStateIcon(
    checked: Boolean?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    trackColor: Color = Color(0xFF5F6368),
    thumbColor: Color = Color(0xFF5F6368),
    animDurationMs: Int = 300,
) {
    //   false → 0f (left), null → 0.5f (center), true → 1f (right)
    val targetFraction = when (checked) {
        false -> 0f
        null -> 0.5f
        true -> 1f
    }

    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = animDurationMs),
        label = "triStateThumbFraction",
    )

    Canvas(modifier = modifier.size(size)) {
        drawTriStateToggle(
            fraction = animatedFraction,
            trackColor = trackColor,
            thumbColor = thumbColor,
        )
    }
}

// ---------------------------------------------------------------------------
// Private drawing helpers
// ---------------------------------------------------------------------------

/**
 * Replicates the Google Fonts "Toggle Off / On" geometry inside [DrawScope].
 *
 * The original SVG uses a 960×960 view-box.  All constants below are derived
 * from that coordinate space and then scaled to the canvas size.
 */
private fun DrawScope.drawTriStateToggle(
    fraction: Float,
    trackColor: Color,
    thumbColor: Color,
) {
    // --- coordinate system ---
    // SVG viewBox: 0 -960 960 960  (origin at top-left after axis flip)
    val svgSize = 960f
    val scaleX = size.width / svgSize
    val scaleY = size.height / svgSize

    // --- track ---
    // Outer path describes a rounded rect from (40,240) to (920,720) in SVG coords.
    // Width = 880, Height = 480, corner radius = 240 (half of height → pill shape).
    val trackLeft = 40f * scaleX
    val trackTop = 240f * scaleY
    val trackWidth = 880f * scaleX
    val trackHeight = 480f * scaleY
    val trackRadius = trackHeight / 2f

    drawRoundRect(
        color = trackColor,
        topLeft = Offset(trackLeft, trackTop),
        size = Size(trackWidth, trackHeight),
        cornerRadius = CornerRadius(trackRadius, trackRadius),
    )

    // --- thumb ---
    // In the SVG the thumb circle has center (280, 480) for the OFF state and
    // center (680, 480) for the ON state (radius ≈ 160 → fits inside the track).
    //
    // We animate between these two extremes (plus the midpoint for null).
    val thumbRadius = 160f * scaleX          // use scaleX; icon is square
    val thumbCentreY = 480f * scaleY

    val thumbCentreXLeft = 280f * scaleX      // false  → left
    val thumbCentreXRight = 680f * scaleX      // true   → right
    val thumbCentreX = thumbCentreXLeft + (thumbCentreXRight - thumbCentreXLeft) * fraction

    drawCircle(
        color = thumbColor,
        radius = thumbRadius,
        center = Offset(thumbCentreX, thumbCentreY),
    )
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun TriStateIconPreview() {
    // Cycle through false → null → true → false on each composition tick
    // (replace with a Button click in real usage)
    Column(
        modifier = Modifier,
    ) {
        TriStateIcon(checked = false, size = 48.dp)   // LEFT  – matches uploaded SVG
        TriStateIcon(checked = null, size = 48.dp)   // CENTRE
        TriStateIcon(checked = true, size = 48.dp)   // RIGHT
    }
}