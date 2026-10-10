package li.gkd.app.ui.snapshot

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import li.gkd.app.ui.platform.UiHost
import kotlin.math.ceil

@Composable
actual fun UiHost.snapshotShareCornerPadding(): Modifier {
    val density = LocalDensity.current
    val minimumBottom = with(density) { GkSnapshotShareProgressDefaults.BottomPadding.toPx() }
    var bottomPadding by remember(this, density) { mutableFloatStateOf(minimumBottom) }
    return Modifier.onGloballyPositioned { coordinates ->
        val decor = window.decorView
        val radius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val insets = decor.rootWindowInsets
            maxOf(
                insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0,
                insets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0,
            ).toFloat()
        } else 0f
        // Measure outside our padding so the parent's existing bottom inset is stable.
        val bottom = coordinates.positionInWindow().y + coordinates.size.height
        val parentBottomInset = (decor.height - bottom).coerceAtLeast(0f)
        val totalBottom = maxOf(radius, parentBottomInset, minimumBottom)
        bottomPadding = ceil(totalBottom - parentBottomInset)
    }.padding(bottom = with(density) { bottomPadding.toDp() })
}
