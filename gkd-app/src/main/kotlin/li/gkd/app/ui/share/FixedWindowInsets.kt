package li.gkd.app.ui.share

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density

// Fix the issue where val obj = TopAppBarDefaults.windowInsets returns inconsistent values at different times
class FixedWindowInsets(
    val insets: WindowInsets
) : WindowInsets by insets {
    var top: Int? = null
    override fun getTop(density: Density) = top ?: insets.getTop(density).also { top = it }

    var bottom: Int? = null
    override fun getBottom(density: Density) =
        bottom ?: insets.getBottom(density).also { bottom = it }
}
