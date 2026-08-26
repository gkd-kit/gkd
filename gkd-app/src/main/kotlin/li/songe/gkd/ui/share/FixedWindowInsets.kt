package li.songe.gkd.ui.share

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density

// 解决 val obj = TopAppBarDefaults.windowInsets 在不同时机返回不一致的问题
class FixedWindowInsets(
    val insets: WindowInsets
) : WindowInsets by insets {
    var top: Int? = null
    override fun getTop(density: Density) = top ?: insets.getTop(density).also { top = it }

    var bottom: Int? = null
    override fun getBottom(density: Density) =
        bottom ?: insets.getBottom(density).also { bottom = it }
}
