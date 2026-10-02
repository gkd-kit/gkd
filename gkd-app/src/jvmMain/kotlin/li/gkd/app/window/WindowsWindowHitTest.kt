package li.gkd.app.window

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** All coordinates are physical client pixels, including on monitors left of the primary display. */
data class WindowsWindowHitTest(
    val title: Rect = Rect.Zero,
    val minimize: Rect = Rect.Zero,
    val maximize: Rect = Rect.Zero,
    val close: Rect = Rect.Zero,
    val settings: Rect = Rect.Zero,
) {
    fun hit(x: Float, y: Float, width: Int, height: Int, border: Int, maximized: Boolean): Int {
        if (!maximized) {
            val left = x < border
            val right = x >= width - border
            val top = y < border
            val bottom = y >= height - border
            when {
                top && left -> return WindowsConstants.HTTOPLEFT
                top && right -> return WindowsConstants.HTTOPRIGHT
                bottom && left -> return WindowsConstants.HTBOTTOMLEFT
                bottom && right -> return WindowsConstants.HTBOTTOMRIGHT
                left -> return WindowsConstants.HTLEFT
                right -> return WindowsConstants.HTRIGHT
                top -> return WindowsConstants.HTTOP
                bottom -> return WindowsConstants.HTBOTTOM
            }
        }
        val point = Offset(x, y)
        return when {
            maximize.contains(point) -> WindowsConstants.HTMAXBUTTON // HTMAXBUTTON enables Windows 11 Snap Layouts.
            minimize.contains(point) || close.contains(point) || settings.contains(point) -> WindowsConstants.HTCLIENT // Compose handles these buttons.
            title.contains(point) -> WindowsConstants.HTCAPTION // HTCAPTION: native drag, double click and system menu.
            else -> WindowsConstants.HTCLIENT
        }
    }
}
